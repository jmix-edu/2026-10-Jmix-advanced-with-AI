package com.company.timesheets.service;

import com.company.timesheets.entity.ProjectStatus;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TaskStatus;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Неделя текущего пользователя в виде таблицы: строка на задачу, ячейка на задачу и день со списаниями этого дня.
 * Строится в {@link WeekTimesheetService#load}; экран недельной таблицы её показывает, сервис применяет к ней правки.
 * Чистые данные с правилами ячейки: в какую ячейку можно вводить и что она показывает
 * (specs/02_ui_development/05-week-sheet.spec.adoc, "Модель недели и правила ячеек").
 *
 * @param days с понедельника по воскресенье
 * @param rows задачи, упорядоченные по проекту и названию задачи
 */
public record WeekTimesheet(List<LocalDate> days, List<TaskRow> rows) {

    /**
     * Ячейка задачи и дня или {@code null}, если в неделе нет такой строки или такого дня.
     */
    @Nullable
    public DayCell cell(UUID taskId, LocalDate day) {
        for (TaskRow row : rows) {
            if (row.task().getId().equals(taskId)) {
                int index = days.indexOf(day);
                return index < 0 ? null : row.cells().get(index);
            }
        }
        return null;
    }

    /**
     * @param task  задача с загруженным проектом
     * @param cells по одной на день, в порядке {@link #days()}
     */
    public record TaskRow(Task task, List<DayCell> cells) {
    }

    /**
     * @param task    задача строки
     * @param day     дата
     * @param entries списания пользователя по этой задаче в этот день, в любом статусе
     */
    public record DayCell(Task task, LocalDate day, List<TimeEntry> entries) {

        /**
         * Время, которое показывает ячейка: все списания, кроме отклонённых, — они нигде не учитываются
         * (правило П2 из specs/01_data_manipulation/05-time-entry-rules.spec.adoc).
         */
        public long minutes() {
            return entries.stream()
                    .filter(e -> e.getStatus() != TimeEntryStatus.REJECTED)
                    .mapToLong(e -> e.getSpentTime() == null ? 0 : e.getSpentTime().minutes())
                    .sum();
        }

        /**
         * В ячейку можно ввести значение, когда она соответствует ровно одному списанию, которое можно менять:
         * списания ещё нет (создаётся новое) или есть одно в статусе NEW (меняется его время, а если ячейку очистили,
         * оно удаляется). И только по задаче, которая принимает время: активной, в открытом проекте (правило П1).
         */
        public boolean editable() {
            if (!taskOpen()) {
                return false;
            }
            return entries.isEmpty() || (entries.size() == 1 && entries.get(0).getStatus() == TimeEntryStatus.NEW);
        }

        /**
         * Единственное списание, которое правит ячейка, или {@code null}, если его нет (или ячейка не редактируется).
         */
        @Nullable
        public TimeEntry editableEntry() {
            return editable() && !entries.isEmpty() ? entries.get(0) : null;
        }

        public boolean taskOpen() {
            return task.getStatus() == TaskStatus.ACTIVE
                    && task.getProject() != null
                    && task.getProject().getStatus() == ProjectStatus.OPEN;
        }
    }
}
