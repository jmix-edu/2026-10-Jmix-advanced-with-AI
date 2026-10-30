package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.datatype.SpentTimeDatatype;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import com.company.timesheets.listener.TimeEntryRuleException;
import com.company.timesheets.service.WeekTimesheet.DayCell;
import com.company.timesheets.service.WeekTimesheet.TaskRow;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Неделя текущего пользователя в виде таблицы и сохранение ячеек, которые пользователь в ней изменил.
 * <p>
 * Работает через {@link DataManager} с проверкой прав: row-level роли решают, какие задачи и списания видит пользователь,
 * а правила списаний ({@code TimeEntryRulesListener}) проверяют каждое изменение точно так же, как в форме списания. Все
 * изменения одного сохранения идут в одном {@link SaveContext}, поэтому сохраняются вместе или не сохраняются вовсе.
 * См. specs/02_ui_development/05-week-sheet.spec.adoc.
 */
@Service
public class WeekTimesheetService {

    private static final SpentTimeDatatype SPENT_TIME = new SpentTimeDatatype();

    private final DataManager dataManager;
    private final CurrentAuthentication currentAuthentication;
    private final Messages messages;

    public WeekTimesheetService(DataManager dataManager, CurrentAuthentication currentAuthentication,
                                Messages messages) {
        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
        this.messages = messages;
    }

    /**
     * Неделя, которая начинается в {@code monday}: строка для каждой задачи, на которую пользователь может списывать время
     * (активная задача открытого проекта, в котором он участвует), и для каждой задачи, по которой у пользователя уже есть
     * списания на этой неделе, даже если она уже закрыта.
     */
    public WeekTimesheet load(LocalDate monday) {
        if (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("A week starts on Monday: " + monday);
        }
        List<LocalDate> days = Stream.iterate(monday, day -> day.plusDays(1)).limit(7).toList();
        UUID userId = currentUser().getId();

        List<TimeEntry> entries = dataManager.load(TimeEntry.class)
                .query("select e from ts_TimeEntry e where e.user.id = :userId"
                        + " and e.date >= :start and e.date <= :end order by e.createdDate")
                .parameter("userId", userId)
                .parameter("start", days.get(0))
                .parameter("end", days.get(6))
                // Правилам ячеек нужны статусы задачи и проекта.
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("task", task -> task.addFetchPlan(FetchPlan.BASE)
                                .add("project", FetchPlan.BASE)))
                .list();

        List<Task> openTasks = dataManager.load(Task.class)
                .query("select t from ts_Task t"
                        + " where t.status = @enum(com.company.timesheets.entity.TaskStatus.ACTIVE)"
                        + " and t.project.status = @enum(com.company.timesheets.entity.ProjectStatus.OPEN)"
                        + " and exists (select pp from ts_ProjectParticipant pp"
                        + "   where pp.project = t.project and pp.user.id = :userId)")
                .parameter("userId", userId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("project", FetchPlan.BASE))
                .list();

        // Задачи по id: сначала открытые, потом те, что известны только из списаний этой недели.
        Map<UUID, Task> tasks = new LinkedHashMap<>();
        openTasks.forEach(task -> tasks.put(task.getId(), task));
        entries.forEach(entry -> tasks.putIfAbsent(entry.getTask().getId(), entry.getTask()));

        List<TaskRow> rows = tasks.values().stream()
                .sorted(Comparator.comparing((Task task) -> task.getProject().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Task::getName, String.CASE_INSENSITIVE_ORDER))
                .map(task -> new TaskRow(task, days.stream()
                        .map(day -> new DayCell(task, day, entries.stream()
                                .filter(e -> e.getTask().getId().equals(task.getId()) && day.equals(e.getDate()))
                                .toList()))
                        .toList()))
                .toList();
        return new WeekTimesheet(days, rows);
    }

    /**
     * Применяет изменения пользователя к неделе, которая начинается в {@code monday}. Неделя загружается заново, поэтому
     * решения принимаются по данным в их текущем виде, а не в том, в каком они были при открытии таблицы.
     * <p>
     * Для каждой изменённой ячейки: пустой текст или {@code 0} — нет времени (списание NEW этой ячейки удаляется); время —
     * его получает списание NEW этой ячейки, или создаётся новое списание NEW. Ячейка, которая сейчас не редактируется, или
     * текст, который не время, — проблема этой ячейки: тогда не сохраняется вообще ничего. Нарушение правила при сохранении
     * (например, больше 24 часов в день) тоже откатывает всё и возвращается сообщением.
     */
    public SaveResult apply(LocalDate monday, List<CellEdit> edits) {
        WeekTimesheet week = load(monday);
        User user = currentUser();
        SaveContext saveContext = new SaveContext();
        List<CellProblem> problems = new ArrayList<>();
        int changed = 0;

        for (CellEdit edit : edits) {
            DayCell cell = week.cell(edit.taskId(), edit.day());
            if (cell == null || !cell.editable()) {
                // Заблокирована за это время (утверждена, в «My week» добавили второе списание) или не редактировалась
                // никогда: браузеру не доверяем, здесь выполняется та же проверка.
                problems.add(new CellProblem(edit.taskId(), edit.day(), message("cellLocked")));
                continue;
            }
            SpentTime time;
            try {
                time = parse(edit.text());
            } catch (ParseException e) {
                problems.add(new CellProblem(edit.taskId(), edit.day(),
                        messages.formatMessage(getClass(), "invalidTime", edit.text())));
                continue;
            }
            TimeEntry entry = cell.editableEntry();
            if (time == null) {
                if (entry != null) {
                    // Мягкое удаление, как и везде для списаний.
                    saveContext.removing(entry);
                    changed++;
                }
            } else if (entry == null) {
                TimeEntry created = dataManager.create(TimeEntry.class);
                created.setUser(user);
                created.setTask(cell.task());
                created.setDate(cell.day());
                created.setSpentTime(time);
                saveContext.saving(created);
                changed++;
            } else if (!Objects.equals(entry.getSpentTime(), time)) {
                entry.setSpentTime(time);
                saveContext.saving(entry);
                changed++;
            }
        }

        if (!problems.isEmpty()) {
            return new SaveResult(0, problems, null);
        }
        if (changed == 0) {
            return new SaveResult(0, List.of(), null);
        }
        try {
            dataManager.save(saveContext);
        } catch (RuntimeException e) {
            // Слушатель правил бросает исключение внутри сохранения; слой данных может его обернуть.
            TimeEntryRuleException rule = findRuleViolation(e);
            if (rule == null) {
                throw e;
            }
            return new SaveResult(0, List.of(), rule.getMessage());
        }
        return new SaveResult(changed, List.of(), null);
    }

    /**
     * Текст ячейки как время: пусто — времени нет, {@code 0} или {@code 0:00} — тоже нет: в электронной таблице ноль
     * означает «ничего», а правила не принимают списание на 0:00.
     */
    @Nullable
    private static SpentTime parse(String text) throws ParseException {
        SpentTime time = SPENT_TIME.parse(text);
        return time == null || time.minutes() == 0 ? null : time;
    }

    @Nullable
    private static TimeEntryRuleException findRuleViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeEntryRuleException rule) {
                return rule;
            }
        }
        return null;
    }

    private User currentUser() {
        return (User) currentAuthentication.getUser();
    }

    private String message(String key) {
        return messages.getMessage(getClass(), key);
    }

    /**
     * Изменённая ячейка в том виде, в каком её оставил пользователь: задача, день и текст; пусто, если ячейку очистили.
     */
    public record CellEdit(UUID taskId, LocalDate day, String text) {
    }

    /**
     * Ячейка, которую не удалось сохранить, и причина.
     */
    public record CellProblem(UUID taskId, LocalDate day, String message) {
    }

    /**
     * Результат {@link #apply}: либо сохранено (с числом изменённых списаний, возможно 0), либо не сохранено вовсе из-за
     * проблем в ячейках или нарушения правила.
     *
     * @param changed       созданные, изменённые и удалённые списания
     * @param problems      ячейки, которые остановили сохранение
     * @param ruleViolation сообщение правила списаний, которое остановило сохранение
     */
    public record SaveResult(int changed, List<CellProblem> problems, @Nullable String ruleViolation) {

        public boolean saved() {
            return problems.isEmpty() && ruleViolation == null;
        }
    }
}
