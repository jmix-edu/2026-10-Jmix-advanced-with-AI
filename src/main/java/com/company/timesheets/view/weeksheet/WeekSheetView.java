package com.company.timesheets.view.weeksheet;

import com.company.timesheets.component.WeekSheet;
import com.company.timesheets.component.WeekSheetModel;
import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.datatype.SpentTimeDatatype;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.service.WeekTimesheet;
import com.company.timesheets.service.WeekTimesheet.DayCell;
import com.company.timesheets.service.WeekTimesheetService;
import com.company.timesheets.service.WeekTimesheetService.CellEdit;
import com.company.timesheets.service.WeekTimesheetService.SaveResult;
import com.company.timesheets.view.main.MainView;
import com.company.timesheets.view.myweek.WeekPeriod;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeLeaveEvent.ContinueNavigationAction;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.core.TimeSource;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.util.UnknownOperationResult;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.NavigateCloseAction;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.flowui.view.ViewValidation;
import io.jmix.flowui.view.navigation.RouteSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Неделя текущего пользователя в виде электронной таблицы: строка на задачу, колонка на день, часы в ячейках.
 * <p>
 * Экран связывает три части: {@link WeekTimesheetService} (загружает неделю и сохраняет правки по правилам списаний),
 * {@link WeekSheet} (наш компонент вокруг JS-библиотеки RevoGrid) и неделю в URL, как в «My week». Экран превращает
 * {@link WeekTimesheet} сервиса в {@link WeekSheetModel} компонента: подписи, тексты и подсказки собираются здесь,
 * поэтому компонент не зависит от сущностей и сообщений.
 * См. specs/02_ui_development/05-week-sheet.spec.adoc и docs/revogrid-integration.adoc.
 */
@Route(value = "week-sheet", layout = MainView.class)
@ViewController(id = "TimeEntries.weekSheet")
@ViewDescriptor(path = "week-sheet-view.xml")
public class WeekSheetView extends StandardView {

    static final String WEEK_PARAMETER = "week";
    private static final DateTimeFormatter CAPTION_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DAY_DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final SpentTimeDatatype SPENT_TIME_FORMAT = new SpentTimeDatatype();

    @ViewComponent
    private WeekSheet weekSheet;
    @ViewComponent
    private Span weekCaption;
    @ViewComponent
    private JmixButton saveButton;
    @ViewComponent
    private JmixButton discardButton;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private WeekTimesheetService weekTimesheetService;
    @Autowired
    private TimeSource timeSource;
    @Autowired
    private RouteSupport routeSupport;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private Notifications notifications;
    @Autowired
    private ViewValidation viewValidation;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private Messages messages;

    private WeekPeriod week;

    @Subscribe
    public void onInit(final InitEvent event) {
        // Save и Discard следуют за несохранёнными изменениями: компонент шлёт событие после каждой пачки из браузера.
        weekSheet.addChangeListener(change -> {
            saveButton.setEnabled(change.hasChanges());
            discardButton.setEnabled(change.hasChanges());
        });
    }

    /**
     * Неделя берётся из URL, как в «My week»: ссылка открывает эту неделю, неверное значение заменяется
     * текущей неделей.
     */
    @Subscribe
    public void onQueryParametersChange(final QueryParametersChangeEvent event) {
        String requested = event.getQueryParameters().getSingleParameter(WEEK_PARAMETER).orElse(null);
        Optional<WeekPeriod> parsed = WeekPeriod.parse(requested);
        WeekPeriod shown = parsed.orElseGet(() -> WeekPeriod.of(today()));
        if (requested != null && !shown.start().toString().equals(requested)) {
            // Пока экран не подключён, getUI() ещё пуст, поэтому берём текущий UI.
            routeSupport.setQueryParameter(UI.getCurrent(), WEEK_PARAMETER, shown.start().toString());
        }
        week = shown;
        weekCaption.setText(messageBundle.formatMessage("weekCaption",
                CAPTION_DATE.format(week.start()), CAPTION_DATE.format(week.end())));
        reload();
    }

    @Subscribe("previousWeekButton")
    public void onPreviousWeekButtonClick(final ClickEvent<JmixButton> event) {
        whenNoUnsavedChanges(() -> navigateTo(week.previous()));
    }

    @Subscribe("nextWeekButton")
    public void onNextWeekButtonClick(final ClickEvent<JmixButton> event) {
        whenNoUnsavedChanges(() -> navigateTo(week.next()));
    }

    /**
     * Отправляет изменённые ячейки в сервис. При успехе таблица показывает неделю в сохранённом виде; иначе тексты
     * пользователя остаются в таблице: ошибочные ячейки отмечены, нарушение правила показано сообщением.
     */
    @Subscribe("saveButton")
    public void onSaveButtonClick(final ClickEvent<JmixButton> event) {
        List<CellEdit> edits = weekSheet.getChanges().stream()
                .map(change -> new CellEdit(UUID.fromString(change.rowKey()), LocalDate.parse(change.dayKey()),
                        change.text()))
                .toList();
        SaveResult result = weekTimesheetService.apply(week.start(), edits);
        if (result.saved()) {
            reload();
            notifications.create(messageBundle.formatMessage("saved", result.changed()))
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } else if (!result.problems().isEmpty()) {
            weekSheet.showErrors(result.problems().stream()
                    .map(p -> new WeekSheet.CellError(p.taskId().toString(), p.day().toString(), p.message()))
                    .toList());
            notifications.create(messageBundle.getMessage("notSaved.cells"))
                    .withType(Notifications.Type.WARNING)
                    .show();
        } else {
            notifications.create(messageBundle.formatMessage("notSaved.rule", result.ruleViolation()))
                    .withType(Notifications.Type.WARNING)
                    .show();
        }
    }

    @Subscribe("discardButton")
    public void onDiscardButtonClick(final ClickEvent<JmixButton> event) {
        discardChanges();
    }

    /**
     * Уход с экрана (меню, ссылка, навигация браузера) с несохранёнными изменениями сначала спрашивает подтверждение, как
     * в экранах редактирования. В StandardView такой проверки нет, поэтому она сделана здесь так же, как в
     * StandardDetailView: отложить навигацию, показать диалог несохранённых изменений Jmix, затем продолжить или отменить.
     */
    @Subscribe
    public void onBeforeClose(final BeforeCloseEvent event) {
        if (!weekSheet.hasChanges() || !(event.getCloseAction() instanceof NavigateCloseAction navigate)) {
            return;
        }
        ContinueNavigationAction navigation = navigate.getBeforeLeaveEvent().postpone();
        UnknownOperationResult result = new UnknownOperationResult();
        viewValidation.showUnsavedChangesDialog(this)
                .onDiscard(() -> {
                    navigation.proceed();
                    result.success();
                })
                .onCancel(() -> {
                    // С React-роутером незавершённая навигация зависает, если её явно не отменить.
                    navigation.cancel();
                    result.fail();
                });
        event.preventClose(result);
    }

    /**
     * Выполняет действие сразу или после того, как пользователь согласился отбросить несохранённые изменения. Нужен для
     * кнопок недели: переход на другую неделю остаётся на этом экране и перезагружает его.
     */
    private void whenNoUnsavedChanges(Runnable action) {
        if (!weekSheet.hasChanges()) {
            action.run();
            return;
        }
        viewValidation.showUnsavedChangesDialog(this)
                .onDiscard(() -> {
                    discardChanges();
                    action.run();
                });
    }

    /**
     * Модель, которую получила таблица, — всё ещё сохранённое состояние: если показать её снова, введённое пропадёт.
     */
    private void discardChanges() {
        WeekSheetModel model = weekSheet.getModel();
        if (model != null) {
            weekSheet.setModel(model);
        }
    }

    private void navigateTo(WeekPeriod target) {
        viewNavigators.view(this, WeekSheetView.class)
                .withQueryParameters(QueryParameters.of(WEEK_PARAMETER, target.start().toString()))
                .navigate();
    }

    /**
     * Загружает неделю из базы и показывает её; несохранённые изменения и отметки ячеек сбрасываются.
     */
    private void reload() {
        weekSheet.setModel(toModel(weekTimesheetService.load(week.start())));
    }

    // --- WeekTimesheet (сервис, сущности) -> WeekSheetModel (компонент, простые тексты) ---

    WeekSheetModel toModel(WeekTimesheet sheet) {
        List<WeekSheetModel.Day> days = sheet.days().stream()
                .map(day -> new WeekSheetModel.Day(day.toString(), dayCaption(day)))
                .toList();
        List<WeekSheetModel.Row> rows = sheet.rows().stream()
                .map(row -> new WeekSheetModel.Row(
                        row.task().getId().toString(),
                        row.task().getDisplayName(),
                        row.cells().stream().map(this::toCell).toList()))
                .toList();
        return new WeekSheetModel(messageBundle.getMessage("rowHeader"), messageBundle.getMessage("totalHeader"),
                messageBundle.getMessage("invalidText"), days, rows);
    }

    private WeekSheetModel.Cell toCell(DayCell cell) {
        long minutes = cell.minutes();
        String text = minutes == 0 ? "" : SPENT_TIME_FORMAT.format(new SpentTime(minutes));
        return new WeekSheetModel.Cell(text, cell.editable(), hint(cell));
    }

    /**
     * Почему ячейка только для чтения, показывается в её подсказке; для редактируемых ячеек — ничего.
     */
    @Nullable
    private String hint(DayCell cell) {
        if (cell.editable()) {
            return null;
        }
        if (!cell.taskOpen() && cell.entries().isEmpty()) {
            return messageBundle.getMessage("hint.taskClosed");
        }
        if (cell.entries().size() > 1) {
            return messageBundle.formatMessage("hint.several", cell.entries().size());
        }
        TimeEntry entry = cell.entries().get(0);
        TimeEntryStatus status = entry.getStatus();
        if (status == TimeEntryStatus.NEW) {
            // Списание NEW заблокировано только потому, что его задача больше не принимает время.
            return messageBundle.getMessage("hint.taskClosed");
        }
        String statusCaption = messages.getMessage(status);
        if (status == TimeEntryStatus.REJECTED) {
            return messageBundle.formatMessage("hint.rejected",
                    SPENT_TIME_FORMAT.format(entry.getSpentTime()),
                    entry.getRejectionReason() == null ? "" : entry.getRejectionReason());
        }
        return messageBundle.formatMessage("hint.status", statusCaption);
    }

    private String dayCaption(LocalDate day) {
        return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, currentAuthentication.getLocale())
                + " " + DAY_DATE.format(day);
    }

    private LocalDate today() {
        return timeSource.now().toLocalDate();
    }

    WeekPeriod getWeek() {
        return week;
    }
}
