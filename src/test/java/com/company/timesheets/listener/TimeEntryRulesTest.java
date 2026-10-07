package com.company.timesheets.listener;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectStatus;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TaskStatus;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.company.timesheets.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.data.PersistenceHints;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Time tracking rules from specs/01_data_manipulation/05-time-entry-rules.spec.adoc,
 * exercised through DataManager without any UI.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class TimeEntryRulesTest {

    private static final LocalDate DAY = LocalDate.of(2001, 1, 1);

    @Autowired
    private DataManager dataManager;

    @Autowired
    private DataSource dataSource;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> clientIds = new ArrayList<>();

    // --- П1: closed project, inactive task ---

    @Test
    void cannotCreateEntryForInactiveTask() {
        Task task = task(project(), TaskStatus.INACTIVE);

        assertRuleViolation(() -> newEntry(task, user(), DAY, 60));
    }

    @Test
    void cannotCreateEntryForTaskOfClosedProject() {
        Project project = project();
        Task task = task(project, TaskStatus.ACTIVE);
        close(project);

        assertRuleViolation(() -> newEntry(task, user(), DAY, 60));
    }

    @Test
    void cannotMoveEntryToInactiveTask() {
        Project project = project();
        TimeEntry entry = newEntry(task(project, TaskStatus.ACTIVE), user(), DAY, 60);
        Task inactive = task(project, TaskStatus.INACTIVE);

        entry.setTask(inactive);
        assertRuleViolation(() -> dataManager.save(entry));
    }

    @Test
    void statusCanChangeButFieldsCannotOnClosedProject() {
        Project project = project();
        TimeEntry entry = newEntry(task(project, TaskStatus.ACTIVE), user(), DAY, 60);
        close(project);

        TimeEntry reloaded = reload(entry);
        reloaded.setDescription("changed");
        assertRuleViolation(() -> dataManager.save(reloaded));

        TimeEntry approved = setStatus(reload(entry), TimeEntryStatus.APPROVED);
        assertThat(approved.getStatus()).isEqualTo(TimeEntryStatus.APPROVED);
    }

    // --- П2: spent time ---

    @Test
    void zeroSpentTimeIsRejected() {
        Task task = task(project(), TaskStatus.ACTIVE);

        assertRuleViolation(() -> newEntry(task, user(), DAY, 0));
    }

    @Test
    void dailyTotalIsLimitedTo24Hours() {
        Task task = task(project(), TaskStatus.ACTIVE);
        User user = user();
        newEntry(task, user, DAY, 20 * 60);
        newEntry(task, user, DAY, 4 * 60);

        assertRuleViolation(() -> newEntry(task, user, DAY, 1));
    }

    @Test
    void dailyLimitIsPerUserAndDate() {
        Task task = task(project(), TaskStatus.ACTIVE);
        User user = user();
        newEntry(task, user, DAY, 24 * 60);

        newEntry(task, user, DAY.plusDays(1), 60);
        newEntry(task, user(), DAY, 60);
    }

    @Test
    void editedEntryIsCountedOnce() {
        TimeEntry entry = newEntry(task(project(), TaskStatus.ACTIVE), user(), DAY, 23 * 60);

        entry.setSpentTime(new SpentTime(24 * 60));
        TimeEntry saved = dataManager.save(entry);
        assertThat(saved.getSpentTime()).isEqualTo(new SpentTime(24 * 60));
    }

    @Test
    void rejectedTimeIsNotCounted() {
        Task task = task(project(), TaskStatus.ACTIVE);
        User user = user();
        reject(newEntry(task, user, DAY, 24 * 60));

        newEntry(task, user, DAY, 60);
    }

    // --- П3: status lifecycle ---

    @Test
    void newEntryMustStartAsNew() {
        Task task = task(project(), TaskStatus.ACTIVE);
        TimeEntry entry = entry(task, user(), DAY, 60);
        entry.setStatus(TimeEntryStatus.APPROVED);

        assertRuleViolation(() -> dataManager.save(entry));
    }

    @Test
    void allowedTransitionsPass() {
        Task task = task(project(), TaskStatus.ACTIVE);
        User user = user();

        TimeEntry approved = setStatus(newEntry(task, user, DAY, 60), TimeEntryStatus.APPROVED);
        assertThat(setStatus(approved, TimeEntryStatus.CLOSED).getStatus()).isEqualTo(TimeEntryStatus.CLOSED);

        TimeEntry rejected = reject(newEntry(task, user, DAY, 60));
        assertThat(setStatus(rejected, TimeEntryStatus.CLOSED).getStatus()).isEqualTo(TimeEntryStatus.CLOSED);
    }

    @Test
    void forbiddenTransitionsAreRejected() {
        Task task = task(project(), TaskStatus.ACTIVE);
        User user = user();

        assertTransitionRejected(newEntry(task, user, DAY, 60), TimeEntryStatus.CLOSED);

        TimeEntry approved = setStatus(newEntry(task, user, DAY, 60), TimeEntryStatus.APPROVED);
        assertTransitionRejected(approved, TimeEntryStatus.NEW);
        assertTransitionRejected(approved, TimeEntryStatus.REJECTED);

        TimeEntry rejected = reject(newEntry(task, user, DAY, 60));
        assertTransitionRejected(rejected, TimeEntryStatus.NEW);
        assertTransitionRejected(rejected, TimeEntryStatus.APPROVED);

        TimeEntry closed = setStatus(approved, TimeEntryStatus.CLOSED);
        assertTransitionRejected(closed, TimeEntryStatus.APPROVED);
    }

    @Test
    void rejectionNeedsReason() {
        TimeEntry entry = newEntry(task(project(), TaskStatus.ACTIVE), user(), DAY, 60);

        entry.setStatus(TimeEntryStatus.REJECTED);
        entry.setRejectionReason("  ");
        assertRuleViolation(() -> dataManager.save(entry));
    }

    @Test
    void fieldsAreEditableOnlyInNew() {
        Task task = task(project(), TaskStatus.ACTIVE);
        User user = user();
        TimeEntry approved = setStatus(newEntry(task, user, DAY, 60), TimeEntryStatus.APPROVED);
        TimeEntry rejected = reject(newEntry(task, user, DAY, 60));

        approved.setSpentTime(new SpentTime(90));
        assertRuleViolation(() -> dataManager.save(approved));

        rejected.setDescription("changed");
        assertRuleViolation(() -> dataManager.save(rejected));
    }

    @Test
    void closedEntryCannotBeChangedOrDeleted() {
        TimeEntry entry = newEntry(task(project(), TaskStatus.ACTIVE), user(), DAY, 60);
        TimeEntry closed = setStatus(setStatus(entry, TimeEntryStatus.APPROVED), TimeEntryStatus.CLOSED);

        closed.setDescription("changed");
        assertRuleViolation(() -> dataManager.save(closed));

        assertRuleViolation(() -> dataManager.remove(reload(entry)));
        assertThat(reload(entry).getDeletedDate()).isNull();
    }

    @Test
    void newEntryCanBeDeleted() {
        TimeEntry entry = newEntry(task(project(), TaskStatus.ACTIVE), user(), DAY, 60);

        dataManager.remove(entry);
        assertThat(loadIncludingDeleted(entry).getDeletedDate()).isNotNull();
    }

    // --- П4: closing a project ---

    @Test
    void closingProjectClosesApprovedEntriesOnly() {
        Project project = project();
        Task task = task(project, TaskStatus.ACTIVE);
        User user = user();
        TimeEntry approved = setStatus(newEntry(task, user, DAY, 60), TimeEntryStatus.APPROVED);
        TimeEntry fresh = newEntry(task, user, DAY, 60);
        TimeEntry rejected = reject(newEntry(task, user, DAY, 60));
        TimeEntry otherProject = setStatus(newEntry(task(project(), TaskStatus.ACTIVE), user, DAY, 60),
                TimeEntryStatus.APPROVED);

        close(project);

        assertThat(reload(approved).getStatus()).isEqualTo(TimeEntryStatus.CLOSED);
        assertThat(reload(fresh).getStatus()).isEqualTo(TimeEntryStatus.NEW);
        assertThat(reload(rejected).getStatus()).isEqualTo(TimeEntryStatus.REJECTED);
        assertThat(reload(otherProject).getStatus()).isEqualTo(TimeEntryStatus.APPROVED);
    }

    // --- П5: deletion ---

    @Test
    void projectWithClosedEntryCannotBeDeleted() {
        Project project = project();
        Task task = task(project, TaskStatus.ACTIVE);
        setStatus(setStatus(newEntry(task, user(), DAY, 60), TimeEntryStatus.APPROVED), TimeEntryStatus.CLOSED);

        assertRuleViolation(() -> dataManager.remove(dataManager.load(Project.class).id(project.getId()).one()));

        assertThat(loadIncludingDeleted(project).getDeletedDate()).isNull();
        assertThat(loadIncludingDeleted(task).getDeletedDate()).isNull();
    }

    @Test
    void taskWithClosedEntryCannotBeDeleted() {
        Task task = task(project(), TaskStatus.ACTIVE);
        setStatus(setStatus(newEntry(task, user(), DAY, 60), TimeEntryStatus.APPROVED), TimeEntryStatus.CLOSED);

        assertRuleViolation(() -> dataManager.remove(dataManager.load(Task.class).id(task.getId()).one()));
        assertThat(loadIncludingDeleted(task).getDeletedDate()).isNull();
    }

    @Test
    void projectWithoutClosedEntriesIsArchivedWithEverything() {
        Project project = project();
        Task task = task(project, TaskStatus.ACTIVE);
        TimeEntry entry = setStatus(newEntry(task, user(), DAY, 60), TimeEntryStatus.APPROVED);

        dataManager.remove(dataManager.load(Project.class).id(project.getId()).one());

        assertThat(loadIncludingDeleted(project).getDeletedDate()).isNotNull();
        assertThat(loadIncludingDeleted(task).getDeletedDate()).isNotNull();
        assertThat(loadIncludingDeleted(entry).getDeletedDate()).isNotNull();
    }

    // --- fixtures ---

    private User user() {
        User user = dataManager.create(User.class);
        user.setUsername("rules-test-" + UUID.randomUUID());
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

    private Task task(Project project, TaskStatus status) {
        Task task = dataManager.create(Task.class);
        task.setName("Task " + UUID.randomUUID());
        task.setProject(project);
        task.setStatus(status);
        return dataManager.save(task);
    }

    private TimeEntry entry(Task task, User user, LocalDate date, long minutes) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(date);
        entry.setSpentTime(new SpentTime(minutes));
        return entry;
    }

    private TimeEntry newEntry(Task task, User user, LocalDate date, long minutes) {
        return dataManager.save(entry(task, user, date, minutes));
    }

    private void close(Project project) {
        Project loaded = dataManager.load(Project.class).id(project.getId()).one();
        loaded.setStatus(ProjectStatus.CLOSED);
        dataManager.save(loaded);
    }

    private TimeEntry setStatus(TimeEntry entry, TimeEntryStatus status) {
        TimeEntry loaded = reload(entry);
        loaded.setStatus(status);
        return dataManager.save(loaded);
    }

    private TimeEntry reject(TimeEntry entry) {
        TimeEntry loaded = reload(entry);
        loaded.setStatus(TimeEntryStatus.REJECTED);
        loaded.setRejectionReason("Wrong task");
        return dataManager.save(loaded);
    }

    private void assertTransitionRejected(TimeEntry entry, TimeEntryStatus status) {
        TimeEntry loaded = reload(entry);
        loaded.setStatus(status);
        assertRuleViolation(() -> dataManager.save(loaded));
    }

    private TimeEntry reload(TimeEntry entry) {
        return dataManager.load(TimeEntry.class).id(entry.getId()).one();
    }

    private <T> T loadIncludingDeleted(T entity) {
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        return dataManager.load(entityClass)
                .id(io.jmix.core.entity.EntityValues.getId(entity))
                .hint(PersistenceHints.SOFT_DELETION, false)
                .one();
    }

    private static void assertRuleViolation(ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown).as("expected a time entry rule violation").isNotNull();
        Throwable cause = thrown;
        while (cause != null && !(cause instanceof TimeEntryRuleException)) {
            cause = cause.getCause();
        }
        assertThat(cause).as("cause chain of %s", thrown).isInstanceOf(TimeEntryRuleException.class);
        // Messages returns the bare key when it is missing from the bundle.
        assertThat(cause.getMessage()).doesNotStartWith("timeEntry.");
    }

    @AfterEach
    void tearDown() {
        // Plain SQL: soft delete would leave rows in the file-based test database,
        // and the rules forbid removing closed entries.
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
        for (UUID userId : userIds) {
            jdbc.update("delete from TS_USER where ID = ?", userId);
        }
        clientIds.clear();
        userIds.clear();
    }
}
