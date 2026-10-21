package com.company.timesheets.security;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import com.company.timesheets.service.MonthClosingService;
import com.company.timesheets.service.ProjectAccessSupport;
import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.accesscontext.EntityAttributeContext;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Timesheet roles from the user's side, through the ordinary DataManager: specs/03_access_control/01-roles.spec.adoc.
 */
@SpringBootTest
@ActiveProfiles("test")
class RolesTest {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private AccessManager accessManager;
    @Autowired
    private Metadata metadata;
    @Autowired
    private ProjectAccessSupport projectAccessSupport;
    @Autowired
    private MonthClosingService monthClosingService;

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
    void employeeSeesOnlyOwnProjectsTasksAndEntries() {
        systemAuthenticator.runWithUser(fixture.emp, () -> {
            assertThat(visible(Project.class)).contains(fixture.projectA.getId())
                    .doesNotContain(fixture.projectB.getId());
            assertThat(visible(Task.class)).contains(fixture.taskA.getId()).doesNotContain(fixture.taskB.getId());
            assertThat(visible(TimeEntry.class)).contains(fixture.empEntryA.getId())
                    .doesNotContain(fixture.pmEntryA.getId(), fixture.pmEntryB.getId(), fixture.otherEntryB.getId());
        });
    }

    @Test
    void managerSeesEntriesOfManagedProjectsAndOnlyOwnElsewhere() {
        systemAuthenticator.runWithUser(fixture.pm, () ->
                assertThat(visible(TimeEntry.class))
                        .contains(fixture.empEntryA.getId(), fixture.pmEntryA.getId(), fixture.pmEntryB.getId())
                        .doesNotContain(fixture.otherEntryB.getId()));
    }

    @Test
    void approverSeesProjectEntriesButAManagerByParticipationWithEmployeeRoleDoesNot() {
        systemAuthenticator.runWithUser(fixture.apr, () ->
                assertThat(visible(TimeEntry.class)).contains(fixture.empEntryA.getId(), fixture.pmEntryA.getId()));
        systemAuthenticator.runWithUser(fixture.mgrEmp, () ->
                assertThat(visible(TimeEntry.class)).doesNotContain(fixture.empEntryA.getId()));
    }

    @Test
    void managerChangesTasksOnlyInManagedProjects() {
        systemAuthenticator.runWithUser(fixture.pm, () -> {
            Task inA = dataManager.load(Task.class).id(fixture.taskA.getId()).one();
            inA.setDescription("Changed by the manager");
            dataManager.save(inA);

            Task inB = dataManager.load(Task.class).id(fixture.taskB.getId()).one();
            inB.setDescription("Not allowed");
            Throwable thrown = catchThrowable(() -> dataManager.save(inB));
            assertThat(thrown).as("saving a task of a project the user does not manage").isNotNull();
            assertThat(causes(thrown)).anyMatch(AccessDeniedException.class::isInstance);
        });
    }

    @Test
    void managerCreatesTasksAndParticipantsOnlyInManagedProjects() {
        systemAuthenticator.runWithUser(fixture.pm, () -> {
            dataManager.save(newTask(fixture.projectA));
            assertThat(causes(catchThrowable(() -> dataManager.save(newTask(fixture.projectB)))))
                    .anyMatch(AccessDeniedException.class::isInstance);

            dataManager.save(newParticipant(fixture.projectA));
            assertThat(causes(catchThrowable(() -> dataManager.save(newParticipant(fixture.projectB)))))
                    .anyMatch(AccessDeniedException.class::isInstance);
        });
    }

    @Test
    void employeeCannotChangeTasks() {
        systemAuthenticator.runWithUser(fixture.emp, () -> {
            Task inA = dataManager.load(Task.class).id(fixture.taskA.getId()).one();
            inA.setDescription("Not allowed");
            assertThat(causes(catchThrowable(() -> dataManager.save(inA))))
                    .anyMatch(AccessDeniedException.class::isInstance);
        });
    }

    @Test
    void contractorDoesNotSeeProjectDescriptionAndClient() {
        systemAuthenticator.runWithUser(fixture.con, () -> {
            assertThat(canView("description")).isFalse();
            assertThat(canView("client")).isFalse();
            assertThat(canView("name")).isTrue();
        });
        systemAuthenticator.runWithUser(fixture.emp, () -> {
            assertThat(canView("description")).isTrue();
            assertThat(canView("client")).isTrue();
        });
    }

    @Test
    void onlyAManagerOrApproverOfTheProjectApprovesAndNotTheirOwnEntry() {
        systemAuthenticator.runWithUser(fixture.pm, () -> {
            assertThat(projectAccessSupport.canApprove(reload(fixture.empEntryA))).isTrue();
            assertThat(projectAccessSupport.canApprove(reload(fixture.pmEntryA))).isFalse();
        });
        systemAuthenticator.runWithUser(fixture.apr, () ->
                assertThat(projectAccessSupport.canApprove(reload(fixture.empEntryA))).isTrue());
        systemAuthenticator.runWithUser(fixture.emp, () ->
                assertThat(projectAccessSupport.canApprove(reload(fixture.empEntryA))).isFalse());
        systemAuthenticator.runWithUser("admin", () ->
                assertThat(projectAccessSupport.canApprove(reload(fixture.empEntryA))).isTrue());
    }

    @Test
    void monthClosingNeedsThePolicy() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        systemAuthenticator.runWithUser(fixture.pm, () -> {
            assertThat(projectAccessSupport.isPermitted(TimesheetsPolicies.MONTH_CLOSING)).isFalse();
            assertThatThrownBy(() -> monthClosingService.closeMonth(fixture.projectA.getId(), lastMonth))
                    .isInstanceOf(AccessDeniedException.class);
        });
        systemAuthenticator.runWithUser(fixture.closer, () -> {
            assertThat(projectAccessSupport.isPermitted(TimesheetsPolicies.MONTH_CLOSING)).isTrue();
            // Nothing approved last month: the closing runs and bills nothing.
            assertThat(monthClosingService.closeMonth(fixture.projectA.getId(), lastMonth)).isEmpty();
        });
    }

    private Task newTask(Project project) {
        Task task = dataManager.create(Task.class);
        task.setName("New task " + UUID.randomUUID());
        task.setProject(project);
        return task;
    }

    private ProjectParticipant newParticipant(Project project) {
        ProjectParticipant participant = dataManager.create(ProjectParticipant.class);
        participant.setProject(project);
        participant.setUser(dataManager.load(User.class).query("select u from ts_User u where u.username = :username")
                .parameter("username", fixture.other).one());
        participant.setProjectRole(systemAuthenticator.withSystem(() -> dataManager.load(ProjectRole.class)
                .query("select r from ts_ProjectRole r where r.name like :prefix")
                .parameter("prefix", fixture.other.substring(0, fixture.other.length() - "other".length()) + "%")
                .maxResults(1).one()));
        return participant;
    }

    private <E> List<UUID> visible(Class<E> entityClass) {
        return fixture.ids(dataManager.load(entityClass).all().list());
    }

    private TimeEntry reload(TimeEntry entry) {
        return systemAuthenticator.withSystem(() -> dataManager.load(TimeEntry.class).id(entry.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("user").add("task", task -> task.add("project")))
                .one());
    }

    private boolean canView(String attribute) {
        EntityAttributeContext context = new EntityAttributeContext(metadata.getClass(Project.class), attribute);
        accessManager.applyRegisteredConstraints(context);
        return context.canView();
    }

    private static List<Throwable> causes(Throwable thrown) {
        List<Throwable> causes = new java.util.ArrayList<>();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
    }
}
