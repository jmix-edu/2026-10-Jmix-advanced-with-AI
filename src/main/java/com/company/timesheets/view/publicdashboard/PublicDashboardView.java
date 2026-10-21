package com.company.timesheets.view.publicdashboard;

import com.company.timesheets.datatype.SpentTimeDatatype;
import com.company.timesheets.entity.DashboardValue;
import com.company.timesheets.service.DashboardSummary;
import com.company.timesheets.service.PublicDashboardService;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.LoadContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Projects at a glance, open without signing in: specs/03_access_control/02-public-dashboard.spec.adoc.
 * No layout on purpose: an anonymous visitor has no main view and no menu. Access is the anonymous role's
 * view policy, not {@code @AnonymousAllowed}, so it shows in the resource roles.
 */
@Route("public/dashboard")
@ViewController(id = "ts_PublicDashboardView")
@ViewDescriptor(path = "public-dashboard-view.xml")
public class PublicDashboardView extends StandardView {

    private static final SpentTimeDatatype SPENT_TIME_FORMAT = new SpentTimeDatatype();
    static final int MONTHS = 12;

    @ViewComponent
    private Span openProjectsValue;
    @ViewComponent
    private Span currentMonthValue;
    @ViewComponent
    private Span lastMonthValue;
    @Autowired
    private PublicDashboardService dashboardService;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        DashboardSummary summary = dashboardService.summary();
        openProjectsValue.setText(String.valueOf(summary.openProjects()));
        currentMonthValue.setText(SPENT_TIME_FORMAT.format(summary.currentMonth()));
        lastMonthValue.setText(SPENT_TIME_FORMAT.format(summary.lastMonth()));
    }

    @Install(to = "hoursByProjectDl", target = Target.DATA_LOADER)
    private List<DashboardValue> hoursByProjectDlLoadDelegate(final LoadContext<DashboardValue> loadContext) {
        return dashboardService.hoursByProject(dashboardService.currentMonth());
    }

    @Install(to = "hoursByMonthDl", target = Target.DATA_LOADER)
    private List<DashboardValue> hoursByMonthDlLoadDelegate(final LoadContext<DashboardValue> loadContext) {
        return dashboardService.hoursByMonth(MONTHS);
    }

    @Install(to = "hoursByTaskTypeDl", target = Target.DATA_LOADER)
    private List<DashboardValue> hoursByTaskTypeDlLoadDelegate(final LoadContext<DashboardValue> loadContext) {
        return dashboardService.hoursByTaskType(dashboardService.currentMonth());
    }
}
