package com.company.timesheets.view.project;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.datatype.SpentTimeDatatype;
import com.company.timesheets.entity.BillingRecord;
import com.company.timesheets.entity.Project;
import com.company.timesheets.listener.TimeEntryRuleException;
import com.company.timesheets.service.MonthClosingService;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;


@Route(value = "projects", layout = MainView.class)
@ViewController(id = "ts_Project.list")
@ViewDescriptor(path = "project-list-view.xml")
@LookupComponent("projectsDataGrid")
@DialogMode(width = "64em")
public class ProjectListView extends StandardListView<Project> {

    private static final SpentTimeDatatype SPENT_TIME_FORMAT = new SpentTimeDatatype();

    @ViewComponent
    private DataGrid<Project> projectsDataGrid;
    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private Dialogs dialogs;
    @Autowired
    private Notifications notifications;
    @Autowired
    private MonthClosingService monthClosingService;

    @Subscribe("projectsDataGrid.closeMonthAction")
    public void onProjectsDataGridCloseMonthAction(final ActionPerformedEvent event) {
        Project project = projectsDataGrid.getSingleSelectedItem();
        if (project == null) {
            return;
        }
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.formatMessage("closeMonthDialog.header", project.getName()))
                .withParameter(localDateParameter("month")
                        .withLabel(messageBundle.getMessage("closeMonthDialog.month"))
                        .withRequired(true)
                        .withDefaultValue(LocalDate.now().minusMonths(1)))
                .withActions(DialogActions.OK_CANCEL, result -> {
                    LocalDate day = result.getValue("month");
                    if (result.closedWith(DialogOutcome.OK) && day != null) {
                        closeMonth(project, YearMonth.from(day));
                    }
                })
                .open();
    }

    private void closeMonth(Project project, YearMonth month) {
        if (!month.isBefore(YearMonth.now())) {
            showWarning(messageBundle.getMessage("closeMonth.notPastMonth"));
            return;
        }
        List<BillingRecord> records;
        try {
            records = monthClosingService.closeMonth(project.getId(), month);
        } catch (RuntimeException e) {
            // A time entry rule arrives wrapped by the data layer; anything else goes to the standard handler.
            TimeEntryRuleException rule = findRuleViolation(e);
            if (rule == null) {
                throw e;
            }
            showWarning(rule.getMessage());
            return;
        }
        long minutes = records.stream().mapToLong(r -> r.getSpentTime().minutes()).sum();
        notifications.create(messageBundle.formatMessage("closeMonth.done",
                        month, records.size(), SPENT_TIME_FORMAT.format(new SpentTime(minutes))))
                .withType(Notifications.Type.SUCCESS)
                .show();
    }

    private static TimeEntryRuleException findRuleViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeEntryRuleException rule) {
                return rule;
            }
        }
        return null;
    }

    private void showWarning(String message) {
        notifications.create(message)
                .withType(Notifications.Type.WARNING)
                .show();
    }
}
