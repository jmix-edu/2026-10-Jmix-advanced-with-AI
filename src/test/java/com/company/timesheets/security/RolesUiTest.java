package com.company.timesheets.security;

import com.company.timesheets.TimesheetsApplication;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.view.project.ProjectDetailView;
import com.company.timesheets.view.timeentry.TimeEntryDetailView;
import com.vaadin.flow.component.HasValue;
import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.accesscontext.UiShowViewContext;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the timesheet roles leave open in the forms: specs/03_access_control/01-roles.spec.adoc.
 */
@UiTest
@SpringBootTest(classes = {TimesheetsApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class RolesUiTest {

    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private AccessManager accessManager;

    private RolesFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new RolesFixture(dataManager, systemAuthenticator, dataSource);
        fixture.create();
    }

    @AfterEach
    void tearDown() {
        fixture.remove();
    }

    @Test
    void employeeCannotChangeTheStatusOfTheirOwnEntry() {
        systemAuthenticator.runWithUser(fixture.emp, () ->
                assertThat(statusFieldOf(fixture.empEntryA).isReadOnly()).isTrue());
    }

    @Test
    void managerChangesTheStatusOfOthersEntriesButNotTheirOwn() {
        systemAuthenticator.runWithUser(fixture.pm, () -> {
            assertThat(statusFieldOf(fixture.empEntryA).isReadOnly()).isFalse();
            assertThat(statusFieldOf(fixture.pmEntryA).isReadOnly()).isTrue();
        });
    }

    @Test
    void contractorSeesTheProjectWithoutDescriptionAndClient() {
        systemAuthenticator.runWithUser(fixture.con, () -> {
            ProjectDetailView view = openProject(fixture.projectA);
            assertThat(UiTestUtils.<JmixTextArea>getComponent(view, "descriptionField").isVisible()).isFalse();
            assertThat(UiTestUtils.<EntityComboBox<?>>getComponent(view, "clientsComboBox").isVisible()).isFalse();
        });
        systemAuthenticator.runWithUser(fixture.emp, () -> {
            ProjectDetailView view = openProject(fixture.projectA);
            assertThat(UiTestUtils.<JmixTextArea>getComponent(view, "descriptionField").isVisible()).isTrue();
            assertThat(view.isReadOnly()).as("an employee only reads the project").isTrue();
        });
    }

    @Test
    void managerEditsTheProjectFormOnlyOfAManagedProject() {
        systemAuthenticator.runWithUser(fixture.pm, () -> {
            assertThat(openProject(fixture.projectA).isReadOnly()).isFalse();
            assertThat(openProject(fixture.projectB).isReadOnly()).isTrue();
        });
    }

    @Test
    void lazyTabsOfAProjectTheManagerDoesNotManageOfferNoCreateOrRemove() {
        systemAuthenticator.runWithUser(fixture.pm, () -> {
            for (String tab : new String[]{"tasksTab", "participantsTab"}) {
                String grid = tab.equals("tasksTab") ? "tasksDataGrid" : "participantsDataGrid";

                ProjectDetailView foreign = openProject(fixture.projectB);
                selectTab(foreign, tab);
                DataGrid<?> foreignGrid = UiTestUtils.getComponent(foreign, grid);
                assertThat(foreignGrid.getAction("createAction").isEnabled()).as(grid + " create in B").isFalse();
                assertThat(foreignGrid.getAction("removeAction").isEnabled()).as(grid + " remove in B").isFalse();

                ProjectDetailView managed = openProject(fixture.projectA);
                selectTab(managed, tab);
                DataGrid<?> managedGrid = UiTestUtils.getComponent(managed, grid);
                assertThat(managedGrid.getAction("createAction").isEnabled()).as(grid + " create in A").isTrue();
            }
        });
    }

    @Test
    void screensOutsideTheRolesAreClosed() {
        systemAuthenticator.runWithUser(fixture.emp, () -> {
            assertThat(viewPermitted("ts_Client.list")).isFalse();
            assertThat(viewPermitted("Projects.cards")).isFalse();
            assertThat(viewPermitted("ts_TimeEntry.list")).isFalse();
            assertThat(viewPermitted("TimeEntries.myWeek")).isTrue();
            assertThat(viewPermitted("ts_Task.list-simple")).isTrue();
        });
        systemAuthenticator.runWithUser(fixture.pm, () -> {
            assertThat(viewPermitted("ts_TimeEntry.list")).isTrue();
            assertThat(viewPermitted("ts_HoursReportView")).isFalse();
            assertThat(viewPermitted("pslock_LockInfo.list")).isFalse();
        });
    }

    private HasValue<?, ?> statusFieldOf(TimeEntry entry) {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), TimeEntry.class).editEntity(entry).navigate();
        TimeEntryDetailView view = UiTestUtils.getCurrentView();
        return UiTestUtils.getComponent(view, "statusField");
    }

    private ProjectDetailView openProject(Project project) {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Project.class).editEntity(project).navigate();
        return UiTestUtils.getCurrentView();
    }

    private void selectTab(ProjectDetailView view, String tabId) {
        JmixTabSheet tabSheet = UiTestUtils.getComponent(view, "tabSheet");
        for (int i = 0; i < tabSheet.getTabCount(); i++) {
            if (tabSheet.getTabAt(i).getId().filter(tabId::equals).isPresent()) {
                tabSheet.setSelectedIndex(i);
                return;
            }
        }
        throw new IllegalArgumentException("No tab " + tabId);
    }

    private boolean viewPermitted(String viewId) {
        UiShowViewContext context = new UiShowViewContext(viewId);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }
}
