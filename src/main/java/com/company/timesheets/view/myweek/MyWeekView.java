package com.company.timesheets.view.myweek;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.datatype.SpentTimeDatatype;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import com.company.timesheets.view.main.MainView;
import com.company.timesheets.view.timeentry.TimeEntryDetailView;
import com.company.timesheets.view.timeentry.TimeEntryStatusBadge;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import io.jmix.core.TimeSource;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import io.jmix.flowui.view.navigation.RouteSupport;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Map;
import java.util.Optional;

/**
 * Списания текущего пользователя за одну неделю, с понедельника по воскресенье, с итогами по дням и за неделю.
 * Неделя хранится в URL-параметре {@code week}, см. specs/02_ui_development/01-my-week.spec.adoc.
 */
@Route(value = "my-week", layout = MainView.class)
@ViewController(id = "TimeEntries.myWeek")
@ViewDescriptor(path = "my-week-view.xml")
public class MyWeekView extends StandardView {

    static final String WEEK_PARAMETER = "week";
    private static final DateTimeFormatter CAPTION_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TOTAL_DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final SpentTimeDatatype SPENT_TIME_FORMAT = new SpentTimeDatatype();

    @ViewComponent
    private CollectionLoader<TimeEntry> timeEntriesDl;
    @ViewComponent
    private CollectionContainer<TimeEntry> timeEntriesDc;
    @ViewComponent
    private Span weekCaption;
    @ViewComponent
    private HorizontalLayout totalsPanel;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private TimeSource timeSource;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private RouteSupport routeSupport;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private UiComponents uiComponents;

    private WeekPeriod week;

    /**
     * Срабатывает перед показом экрана и снова, когда экран открывают с другими параметрами,
     * поэтому ссылка на неделю всегда открывает эту неделю.
     */
    @Subscribe
    public void onQueryParametersChange(final QueryParametersChangeEvent event) {
        String requested = event.getQueryParameters().getSingleParameter(WEEK_PARAMETER).orElse(null);
        Optional<WeekPeriod> parsed = WeekPeriod.parse(requested);
        WeekPeriod shown = parsed.orElseGet(this::currentWeek);
        // Записываем в URL канонический понедельник или убираем значение, которое не является датой.
        boolean urlNeedsFix = requested != null && !shown.start().toString().equals(requested);
        showWeek(shown, urlNeedsFix);
    }

    @Subscribe("previousWeekButton")
    public void onPreviousWeekButtonClick(final ClickEvent<JmixButton> event) {
        navigateTo(week.previous());
    }

    @Subscribe("nextWeekButton")
    public void onNextWeekButtonClick(final ClickEvent<JmixButton> event) {
        navigateTo(week.next());
    }

    @Subscribe(id = "timeEntriesDc", target = Target.DATA_CONTAINER)
    public void onTimeEntriesDcCollectionChange(final CollectionContainer.CollectionChangeEvent<TimeEntry> event) {
        updateTotals();
    }

    @Install(to = "timeEntriesDataGrid.create", subject = "initializer")
    private void timeEntriesDataGridCreateInitializer(final TimeEntry entry) {
        entry.setUser((User) currentAuthentication.getUser());
        LocalDate today = today();
        entry.setDate(week.contains(today) ? today : week.start());
    }

    @Install(to = "timeEntriesDataGrid.create", subject = "viewConfigurer")
    private void timeEntriesDataGridCreateViewConfigurer(final TimeEntryDetailView view) {
        view.setOwnTimeEntry(true);
    }

    @Install(to = "timeEntriesDataGrid.edit", subject = "viewConfigurer")
    private void timeEntriesDataGridEditViewConfigurer(final TimeEntryDetailView view) {
        view.setOwnTimeEntry(true);
    }

    @Install(to = "timeEntriesDataGrid.create", subject = "afterSaveHandler")
    private void timeEntriesDataGridCreateAfterSaveHandler(final TimeEntry entry) {
        // Дата может оказаться вне недели: перезагружаем, а не оставляем сохранённый экземпляр.
        timeEntriesDl.load();
    }

    @Install(to = "timeEntriesDataGrid.edit", subject = "afterSaveHandler")
    private void timeEntriesDataGridEditAfterSaveHandler(final TimeEntry entry) {
        timeEntriesDl.load();
    }

    @Install(to = "timeEntriesDataGrid", subject = "partNameGenerator")
    private String timeEntriesDataGridPartNameGenerator(final TimeEntry timeEntry) {
        return TimeEntryStatusBadge.rowPartName(timeEntry);
    }

    @Supply(to = "timeEntriesDataGrid.project", subject = "renderer")
    private Renderer<TimeEntry> timeEntriesDataGridProjectRenderer() {
        return new ComponentRenderer<>(entry -> {
            Project project = entry.getTask() == null ? null : entry.getTask().getProject();
            if (project == null) {
                return new Span();
            }
            JmixButton link = uiComponents.create(JmixButton.class);
            link.setText(project.getName());
            link.addClickListener(click -> openProject(project));
            return link;
        });
    }

    private void openProject(Project project) {
        viewNavigators.detailView(this, Project.class)
                .editEntity(project)
                .withBackwardNavigation(true)
                .navigate();
    }

    /**
     * Новая запись истории на каждую неделю, чтобы «Назад» в браузере возвращал прошлую неделю.
     * Экран читает неделю в {@link #onQueryParametersChange}, как и при переходе по ссылке.
     */
    private void navigateTo(WeekPeriod target) {
        viewNavigators.view(this, MyWeekView.class)
                .withQueryParameters(QueryParameters.of(WEEK_PARAMETER, target.start().toString()))
                .navigate();
    }

    private void showWeek(WeekPeriod shown, boolean updateUrl) {
        week = shown;
        if (updateUrl) {
            // Пока экран не присоединён, getUI() ещё пуст, поэтому берём текущий UI.
            routeSupport.setQueryParameter(UI.getCurrent(), WEEK_PARAMETER, week.start().toString());
        }
        weekCaption.setText(messageBundle.formatMessage("weekCaption",
                CAPTION_DATE.format(week.start()), CAPTION_DATE.format(week.end())));
        timeEntriesDl.setParameter("weekStart", week.start());
        timeEntriesDl.setParameter("weekEnd", week.end());
        timeEntriesDl.load();
    }

    private void updateTotals() {
        totalsPanel.removeAll();
        if (week == null) {
            return;
        }
        Map<LocalDate, Long> minutesByDay = week.minutesByDay(timeEntriesDc.getItems());
        minutesByDay.forEach((day, minutes) -> totalsPanel.add(totalSpan("dayTotal_" + day.getDayOfWeek(),
                messageBundle.formatMessage("dayTotal",
                        day.getDayOfWeek().getDisplayName(TextStyle.SHORT, currentAuthentication.getLocale()),
                        TOTAL_DATE.format(day), format(minutes)))));
        long weekMinutes = minutesByDay.values().stream().mapToLong(Long::longValue).sum();
        totalsPanel.add(totalSpan("weekTotal", messageBundle.formatMessage("weekTotal", format(weekMinutes))));
    }

    private Span totalSpan(String id, String text) {
        Span span = uiComponents.create(Span.class);
        span.setId(id);
        span.setText(text);
        return span;
    }

    private String format(long minutes) {
        return SPENT_TIME_FORMAT.format(new SpentTime(minutes));
    }

    private WeekPeriod currentWeek() {
        return WeekPeriod.of(today());
    }

    private LocalDate today() {
        return timeSource.now().toLocalDate();
    }

    WeekPeriod getWeek() {
        return week;
    }
}
