package com.company.timesheets.view.hoursreport;

import com.company.timesheets.entity.ProjectSummary;
import com.company.timesheets.service.HoursReportService;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.LoadContext;
import io.jmix.core.ValueLoadContext;
import io.jmix.core.entity.KeyValueEntity;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.KeyValueCollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only hours report. All calculations live in {@link HoursReportService}.
 */
@Route(value = "hours-report", layout = MainView.class)
@ViewController(id = "ts_HoursReportView")
@ViewDescriptor(path = "hours-report-view.xml")
public class HoursReportView extends StandardView {

    @ViewComponent
    private TypedDatePicker<LocalDate> fromField;
    @ViewComponent
    private TypedDatePicker<LocalDate> toField;
    @ViewComponent
    private KeyValueCollectionLoader hoursDl;
    @ViewComponent
    private CollectionLoader<ProjectSummary> projectSummariesDl;
    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private HoursReportService hoursReportService;
    @Autowired
    private Notifications notifications;

    @Subscribe
    public void onInit(final InitEvent event) {
        LocalDate today = LocalDate.now();
        fromField.setTypedValue(today.withDayOfMonth(1));
        toField.setTypedValue(today.withDayOfMonth(today.lengthOfMonth()));
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        hoursDl.load();
        projectSummariesDl.load();
    }

    @Subscribe("showButton")
    public void onShowButtonClick(final ClickEvent<JmixButton> event) {
        if (!isPeriodValid()) {
            notifications.create(messageBundle.getMessage("invalidPeriod"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        hoursDl.load();
    }

    @Install(to = "hoursDl", target = Target.DATA_LOADER)
    private List<KeyValueEntity> hoursDlLoadDelegate(final ValueLoadContext loadContext) {
        if (!isPeriodValid()) {
            return List.of();
        }
        return hoursReportService.loadHoursByProjectAndUser(fromField.getTypedValue(), toField.getTypedValue());
    }

    @Install(to = "projectSummariesDl", target = Target.DATA_LOADER)
    private List<ProjectSummary> projectSummariesDlLoadDelegate(final LoadContext<ProjectSummary> loadContext) {
        return hoursReportService.loadProjectSummaries();
    }

    private boolean isPeriodValid() {
        LocalDate from = fromField.getTypedValue();
        LocalDate to = toField.getTypedValue();
        return from != null && to != null && !from.isAfter(to);
    }
}
