package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.entity.ProjectStatus;
import com.company.timesheets.entity.ProjectSummary;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.company.timesheets.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.entity.KeyValueEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reports from specs/01_data_manipulation/06-hours-reports.spec.adoc. Assertions look only at the projects
 * created by the test, so data left by others does not matter.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class HoursReportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2002, 3, 1);
    private static final LocalDate TO = LocalDate.of(2002, 3, 31);

    @Autowired
    private HoursReportService hoursReportService;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private DataSource dataSource;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> clientIds = new ArrayList<>();
    private final List<UUID> roleIds = new ArrayList<>();

    // --- hours by project and user ---

    @Test
    void countsOnlyApprovedAndClosedTime() {
        Project project = project();
        Task task = task(project);
        User user = user();
        approve(entry(task, user, FROM, 60));
        close(approve(entry(task, user, FROM, 30)));
        entry(task, user, FROM, 600);
        reject(entry(task, user, FROM.plusDays(1), 120));

        List<KeyValueEntity> rows = rowsOf(project);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().<SpentTime>getValue("totalTime")).isEqualTo(new SpentTime(90));
        assertThat(rows.getFirst().<Long>getValue("entryCount")).isEqualTo(2L);
    }

    @Test
    void periodIncludesBothBoundsOnly() {
        Project project = project();
        Task task = task(project);
        User user = user();
        approve(entry(task, user, FROM.minusDays(1), 1));
        approve(entry(task, user, FROM, 10));
        approve(entry(task, user, TO, 20));
        approve(entry(task, user, TO.plusDays(1), 2));

        assertThat(rowsOf(project).getFirst().<SpentTime>getValue("totalTime")).isEqualTo(new SpentTime(30));
    }

    @Test
    void groupsByProjectAndUser() {
        Project first = project();
        Project second = project();
        User alice = user();
        User bob = user();
        approve(entry(task(first), alice, FROM, 60));
        approve(entry(task(first), alice, FROM.plusDays(1), 30));
        approve(entry(task(first), bob, FROM, 45));
        approve(entry(task(second), alice, FROM, 15));

        List<KeyValueEntity> firstRows = rowsOf(first);
        assertThat(firstRows).hasSize(2);
        assertThat(rowFor(firstRows, alice).<SpentTime>getValue("totalTime")).isEqualTo(new SpentTime(90));
        assertThat(rowFor(firstRows, alice).<Long>getValue("entryCount")).isEqualTo(2L);
        assertThat(rowFor(firstRows, bob).<SpentTime>getValue("totalTime")).isEqualTo(new SpentTime(45));

        List<KeyValueEntity> secondRows = rowsOf(second);
        assertThat(secondRows).hasSize(1);
        assertThat(rowFor(secondRows, alice).<SpentTime>getValue("totalTime")).isEqualTo(new SpentTime(15));
    }

    @Test
    void archivedEntryIsNotCounted() {
        Project project = project();
        Task task = task(project);
        User user = user();
        approve(entry(task, user, FROM, 60));
        TimeEntry archived = approve(entry(task, user, FROM, 30));
        dataManager.remove(archived);

        assertThat(rowsOf(project).getFirst().<SpentTime>getValue("totalTime")).isEqualTo(new SpentTime(60));
    }

    @Test
    void rejectsInvertedPeriod() {
        assertThatThrownBy(() -> hoursReportService.loadHoursByProjectAndUser(TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- project summaries ---

    @Test
    void summaryHasTotalAndCurrentMonthTime() {
        Project project = project();
        Task task = task(project);
        User user = user();
        LocalDate today = LocalDate.now();
        approve(entry(task, user, today, 60));
        approve(entry(task, user, today.withDayOfMonth(1).minusDays(1), 30));
        entry(task, user, today, 600);

        ProjectSummary summary = summaryOf(project);

        assertThat(summary.getTotalTime()).isEqualTo(new SpentTime(90));
        assertThat(summary.getCurrentMonthTime()).isEqualTo(new SpentTime(60));
        assertThat(summary.getClient().getId()).isEqualTo(project.getClient().getId());
        assertThat(summary.getStatus()).isEqualTo(ProjectStatus.OPEN);
    }

    @Test
    void summaryCountsDistinctParticipants() {
        Project project = project();
        User alice = user();
        participant(project, alice, role(ProjectRoleType.MANAGER));
        participant(project, alice, role(ProjectRoleType.APPROVER));
        participant(project, user(), role(ProjectRoleType.MEMBER));

        assertThat(summaryOf(project).getParticipantCount()).isEqualTo(2);
    }

    @Test
    void projectWithoutDataHasZeros() {
        Project project = project();

        ProjectSummary summary = summaryOf(project);

        assertThat(summary.getTotalTime()).isEqualTo(new SpentTime(0));
        assertThat(summary.getCurrentMonthTime()).isEqualTo(new SpentTime(0));
        assertThat(summary.getParticipantCount()).isZero();
    }

    @Test
    void closedProjectIsInSummaries() {
        Project project = project();
        project.setStatus(ProjectStatus.CLOSED);
        Project closed = dataManager.save(project);

        assertThat(summaryOf(closed).getStatus()).isEqualTo(ProjectStatus.CLOSED);
    }

    // --- helpers ---

    private List<KeyValueEntity> rowsOf(Project project) {
        return hoursReportService.loadHoursByProjectAndUser(FROM, TO).stream()
                .filter(r -> r.<Project>getValue("project").getId().equals(project.getId()))
                .toList();
    }

    private static KeyValueEntity rowFor(List<KeyValueEntity> rows, User user) {
        return rows.stream()
                .filter(r -> r.<User>getValue("user").getId().equals(user.getId()))
                .findFirst().orElseThrow();
    }

    private ProjectSummary summaryOf(Project project) {
        return hoursReportService.loadProjectSummaries().stream()
                .filter(s -> s.getId().equals(project.getId()))
                .findFirst().orElseThrow();
    }

    private User user() {
        User user = dataManager.create(User.class);
        user.setUsername("report-test-" + UUID.randomUUID());
        User saved = dataManager.save(user);
        userIds.add(saved.getId());
        return saved;
    }

    private Project project() {
        Client client = dataManager.create(Client.class);
        client.setName("Client " + UUID.randomUUID());
        client.setContactInformation(dataManager.create(ContactInformation.class));
        Client savedClient = dataManager.save(client);
        clientIds.add(savedClient.getId());

        Project project = dataManager.create(Project.class);
        project.setName("Project " + UUID.randomUUID());
        project.setClient(savedClient);
        return dataManager.save(project);
    }

    private Task task(Project project) {
        Task task = dataManager.create(Task.class);
        task.setName("Task " + UUID.randomUUID());
        task.setProject(project);
        return dataManager.save(task);
    }

    private ProjectRole role(ProjectRoleType type) {
        ProjectRole role = dataManager.create(ProjectRole.class);
        role.setName("Role " + UUID.randomUUID());
        role.setType(type);
        ProjectRole saved = dataManager.save(role);
        roleIds.add(saved.getId());
        return saved;
    }

    private void participant(Project project, User user, ProjectRole role) {
        ProjectParticipant participant = dataManager.create(ProjectParticipant.class);
        participant.setProject(project);
        participant.setUser(user);
        participant.setProjectRole(role);
        dataManager.saveWithoutReload(participant);
    }

    private TimeEntry entry(Task task, User user, LocalDate date, long minutes) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(date);
        entry.setSpentTime(new SpentTime(minutes));
        return dataManager.save(entry);
    }

    private TimeEntry approve(TimeEntry entry) {
        entry.setStatus(TimeEntryStatus.APPROVED);
        return dataManager.save(entry);
    }

    private void close(TimeEntry entry) {
        // Approved time is closed only into a billing record; the record itself is not needed here.
        entry.setStatus(TimeEntryStatus.CLOSED);
        entry.setBillingRecordId(UUID.randomUUID());
        dataManager.saveWithoutReload(entry);
    }

    private void reject(TimeEntry entry) {
        entry.setStatus(TimeEntryStatus.REJECTED);
        entry.setRejectionReason("Wrong task");
        dataManager.saveWithoutReload(entry);
    }

    @AfterEach
    void tearDown() {
        // Plain SQL: soft delete would leave rows in the file-based test database,
        // and the time entry rules forbid removing closed entries.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (UUID clientId : clientIds) {
            String projectIds = "select ID from TS_PROJECT where CLIENT_ID = ?";
            String taskIds = "select ID from TS_TASK where PROJECT_ID in (" + projectIds + ")";
            jdbc.update("delete from TS_TIME_ENTRY where TASK_ID in (" + taskIds + ")", clientId);
            jdbc.update("delete from TS_PROJECT_PARTICIPANT where PROJECT_ID in (" + projectIds + ")", clientId);
            jdbc.update("delete from TS_TASK where PROJECT_ID in (" + projectIds + ")", clientId);
            jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
            jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        }
        for (UUID roleId : roleIds) {
            jdbc.update("delete from TS_PROJECT_ROLE where ID = ?", roleId);
        }
        for (UUID userId : userIds) {
            jdbc.update("delete from TS_USER where ID = ?", userId);
        }
        clientIds.clear();
        roleIds.clear();
        userIds.clear();
    }
}
