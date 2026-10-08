package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.BillingRecord;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.company.timesheets.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.event.EntitySavingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Month closing from specs/01_data_manipulation/07-billing-store.spec.adoc: approved time goes into the reports
 * store, entries become CLOSED, a repeat converges instead of duplicating.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
@Import(MonthClosingServiceTest.FailingReportsStore.class)
class MonthClosingServiceTest {

    private static final YearMonth MONTH = YearMonth.of(2003, 4);
    private static final LocalDate DAY = MONTH.atDay(10);

    @Autowired
    private MonthClosingService monthClosingService;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    @Qualifier("reportsDataSource")
    private DataSource reportsDataSource;

    @Autowired
    private FailingReportsStore failingReportsStore;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> clientIds = new ArrayList<>();
    private final List<UUID> projectIds = new ArrayList<>();

    @Test
    void createsRecordPerUserFromApprovedTimeOfTheMonth() {
        Project project = project();
        Task task = task(project);
        User alice = user();
        User bob = user();
        TimeEntry alice1 = approved(task, alice, DAY, 60);
        TimeEntry alice2 = approved(task, alice, MONTH.atEndOfMonth(), 30);
        TimeEntry bob1 = approved(task, bob, MONTH.atDay(1), 45);
        TimeEntry fresh = entry(task, alice, DAY, 600);
        TimeEntry rejected = reject(entry(task, bob, DAY, 120));
        TimeEntry nextMonth = approved(task, alice, MONTH.plusMonths(1).atDay(1), 15);
        TimeEntry otherProject = approved(task(project()), alice, DAY, 50);

        List<BillingRecord> records = monthClosingService.closeMonth(project.getId(), MONTH);

        assertThat(records).hasSize(2);
        List<BillingRecord> stored = storedRecords(project);
        assertThat(stored).hasSize(2);
        BillingRecord aliceRecord = recordOf(stored, alice);
        assertThat(aliceRecord.getSpentTime()).isEqualTo(new SpentTime(90));
        assertThat(aliceRecord.getMonth()).isEqualTo(MONTH.atDay(1));
        assertThat(aliceRecord.getProjectName()).isEqualTo(project.getName());
        assertThat(aliceRecord.getUsername()).isEqualTo(alice.getUsername());
        assertThat(aliceRecord.getClosedDate()).isEqualTo(LocalDate.now());
        BillingRecord bobRecord = recordOf(stored, bob);
        assertThat(bobRecord.getSpentTime()).isEqualTo(new SpentTime(45));

        assertClosedInto(alice1, aliceRecord);
        assertClosedInto(alice2, aliceRecord);
        assertClosedInto(bob1, bobRecord);
        assertUntouched(fresh, TimeEntryStatus.NEW);
        assertUntouched(rejected, TimeEntryStatus.REJECTED);
        assertUntouched(nextMonth, TimeEntryStatus.APPROVED);
        assertUntouched(otherProject, TimeEntryStatus.APPROVED);
    }

    @Test
    void repeatWithoutNewEntriesChangesNothing() {
        Project project = project();
        Task task = task(project);
        User alice = user();
        approved(task, alice, DAY, 60);
        monthClosingService.closeMonth(project.getId(), MONTH);
        BillingRecord first = recordOf(storedRecords(project), alice);

        List<BillingRecord> repeated = monthClosingService.closeMonth(project.getId(), MONTH);

        assertThat(repeated).extracting(BillingRecord::getId).containsExactly(first.getId());
        List<BillingRecord> stored = storedRecords(project);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getSpentTime()).isEqualTo(new SpentTime(60));
        assertThat(stored.getFirst().getVersion()).isEqualTo(first.getVersion());
    }

    @Test
    void repeatAfterNewApprovalAddsToTheSameRecord() {
        Project project = project();
        Task task = task(project);
        User alice = user();
        approved(task, alice, DAY, 60);
        monthClosingService.closeMonth(project.getId(), MONTH);
        TimeEntry late = approved(task, alice, DAY.plusDays(1), 40);

        monthClosingService.closeMonth(project.getId(), MONTH);

        List<BillingRecord> stored = storedRecords(project);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getSpentTime()).isEqualTo(new SpentTime(100));
        assertClosedInto(late, stored.getFirst());
    }

    @Test
    void failureInReportsStoreRollsBackTheEntries() {
        Project project = project();
        Task task = task(project);
        TimeEntry entry = approved(task, user(), DAY, 60);

        failingReportsStore.failing = true;
        try {
            Throwable thrown = catchThrowable(() -> monthClosingService.closeMonth(project.getId(), MONTH));
            assertThat(thrown).isNotNull();
            List<String> messages = new ArrayList<>();
            for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
                messages.add(cause.getMessage());
            }
            assertThat(messages).contains(FailingReportsStore.MESSAGE);
        } finally {
            failingReportsStore.failing = false;
        }

        assertUntouched(entry, TimeEntryStatus.APPROVED);
        assertThat(storedRecords(project)).isEmpty();
    }

    @Test
    void recalculatesRecordLeftByFailedMainCommit() {
        Project project = project();
        Task task = task(project);
        User alice = user();
        TimeEntry entry = approved(task, alice, DAY, 60);
        // The reports store committed, the main one did not: the record exists, the entry is still APPROVED.
        BillingRecord orphan = dataManager.create(BillingRecord.class);
        orphan.setProjectId(project.getId());
        orphan.setProjectName(project.getName());
        orphan.setUserId(alice.getId());
        orphan.setUsername(alice.getUsername());
        orphan.setMonth(MONTH.atDay(1));
        orphan.setSpentTime(new SpentTime(999));
        orphan.setClosedDate(LocalDate.now());
        orphan = dataManager.save(orphan);

        monthClosingService.closeMonth(project.getId(), MONTH);

        List<BillingRecord> stored = storedRecords(project);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getId()).isEqualTo(orphan.getId());
        assertThat(stored.getFirst().getSpentTime()).isEqualTo(new SpentTime(60));
        assertClosedInto(entry, stored.getFirst());
    }

    @Test
    void currentMonthCannotBeClosed() {
        Project project = project();
        TimeEntry entry = approved(task(project), user(), LocalDate.now(), 60);

        assertThatThrownBy(() -> monthClosingService.closeMonth(project.getId(), YearMonth.now()))
                .isInstanceOf(IllegalArgumentException.class);

        assertUntouched(entry, TimeEntryStatus.APPROVED);
        assertThat(storedRecords(project)).isEmpty();
    }

    // --- helpers ---

    private void assertClosedInto(TimeEntry entry, BillingRecord record) {
        TimeEntry loaded = reload(entry);
        assertThat(loaded.getStatus()).isEqualTo(TimeEntryStatus.CLOSED);
        assertThat(loaded.getBillingRecordId()).isEqualTo(record.getId());
    }

    private void assertUntouched(TimeEntry entry, TimeEntryStatus status) {
        TimeEntry loaded = reload(entry);
        assertThat(loaded.getStatus()).isEqualTo(status);
        assertThat(loaded.getBillingRecordId()).isNull();
    }

    private List<BillingRecord> storedRecords(Project project) {
        return dataManager.load(BillingRecord.class)
                .query("select r from ts_BillingRecord r where r.projectId = :projectId")
                .parameter("projectId", project.getId())
                .list();
    }

    private static BillingRecord recordOf(List<BillingRecord> records, User user) {
        return records.stream().filter(r -> r.getUserId().equals(user.getId())).findFirst().orElseThrow();
    }

    private TimeEntry reload(TimeEntry entry) {
        return dataManager.load(TimeEntry.class).id(entry.getId()).one();
    }

    private User user() {
        User user = dataManager.create(User.class);
        user.setUsername("billing-test-" + UUID.randomUUID());
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
        Project saved = dataManager.save(project);
        projectIds.add(saved.getId());
        return saved;
    }

    private Task task(Project project) {
        Task task = dataManager.create(Task.class);
        task.setName("Task " + UUID.randomUUID());
        task.setProject(project);
        return dataManager.save(task);
    }

    private TimeEntry entry(Task task, User user, LocalDate date, long minutes) {
        TimeEntry entry = dataManager.create(TimeEntry.class);
        entry.setTask(task);
        entry.setUser(user);
        entry.setDate(date);
        entry.setSpentTime(new SpentTime(minutes));
        return dataManager.save(entry);
    }

    private TimeEntry approved(Task task, User user, LocalDate date, long minutes) {
        TimeEntry entry = entry(task, user, date, minutes);
        entry.setStatus(TimeEntryStatus.APPROVED);
        return dataManager.save(entry);
    }

    private TimeEntry reject(TimeEntry entry) {
        entry.setStatus(TimeEntryStatus.REJECTED);
        entry.setRejectionReason("Wrong task");
        return dataManager.save(entry);
    }

    @AfterEach
    void tearDown() {
        JdbcTemplate reports = new JdbcTemplate(reportsDataSource);
        for (UUID projectId : projectIds) {
            reports.update("delete from TS_BILLING_RECORD where PROJECT_ID = ?", projectId);
        }
        // Plain SQL: soft delete would leave rows in the file-based test database,
        // and the time entry rules forbid removing closed entries.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (UUID clientId : clientIds) {
            String projectsOfClient = "select ID from TS_PROJECT where CLIENT_ID = ?";
            String taskIds = "select ID from TS_TASK where PROJECT_ID in (" + projectsOfClient + ")";
            jdbc.update("delete from TS_TIME_ENTRY where TASK_ID in (" + taskIds + ")", clientId);
            jdbc.update("delete from TS_TASK where PROJECT_ID in (" + projectsOfClient + ")", clientId);
            jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
            jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        }
        for (UUID userId : userIds) {
            jdbc.update("delete from TS_USER where ID = ?", userId);
        }
        projectIds.clear();
        clientIds.clear();
        userIds.clear();
    }

    /**
     * Makes saving a billing record fail on demand, standing in for an unavailable reports database.
     */
    @TestConfiguration
    static class FailingReportsStore {

        static final String MESSAGE = "reports store is down (test)";

        volatile boolean failing;

        @EventListener
        public void onBillingRecordSaving(EntitySavingEvent<BillingRecord> event) {
            if (failing) {
                throw new IllegalStateException(MESSAGE);
            }
        }
    }
}
