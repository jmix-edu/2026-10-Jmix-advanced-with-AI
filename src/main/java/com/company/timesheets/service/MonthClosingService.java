package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.BillingRecord;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import io.jmix.core.DataManager;
import io.jmix.core.EntitySet;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.pessimisticlock.LockManager;
import io.jmix.pessimisticlock.entity.LockInfo;
import io.jmix.pessimisticlock.entity.LockNotSupported;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Closes a project's month: approved time goes into {@link BillingRecord}s in the {@code reports} store and the
 * entries become {@code CLOSED}.
 * <p>
 * The two stores have separate transactions. Entries are changed first inside the main transaction (rules and
 * optimistic locks fire on save), billing records are committed next, and the main transaction commits last.
 * If that last commit fails, the billing record is left ahead of the entries; running the operation again fixes
 * it, because a record's time is always recalculated from the entries linked to it, never accumulated.
 * <p>
 * Closings of one project run one at a time: a pessimistic lock on the project is held from before the main
 * transaction starts until after it commits, so the next closing sees the entries already closed.
 */
@Service
public class MonthClosingService {

    /**
     * Pessimistic lock name; the lock id is the project id.
     */
    public static final String LOCK_NAME = "ts_MonthClosing";

    private final DataManager dataManager;
    private final LockManager lockManager;
    private final TransactionTemplate transaction;

    public MonthClosingService(DataManager dataManager, LockManager lockManager,
                               PlatformTransactionManager transactionManager) {
        this.dataManager = dataManager;
        this.lockManager = lockManager;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Bills the project's approved entries dated within the month and returns the month's records of the project,
     * one per user, ordered by username. Only past months can be closed.
     *
     * @throws MonthClosingLockedException if a closing of the same project is already running
     */
    public List<BillingRecord> closeMonth(UUID projectId, YearMonth month) {
        if (projectId == null || month == null || !month.isBefore(YearMonth.now())) {
            throw new IllegalArgumentException("Only a past month can be closed: " + month);
        }
        String lockId = projectId.toString();
        LockInfo holder = lockManager.lock(LOCK_NAME, lockId);
        if (holder instanceof LockNotSupported) {
            throw new IllegalStateException("No lock descriptor for " + LOCK_NAME);
        }
        if (holder != null) {
            throw new MonthClosingLockedException(holder.getUsername(), holder.getSince());
        }
        try {
            return transaction.execute(status -> closeLocked(projectId, month));
        } finally {
            lockManager.unlock(LOCK_NAME, lockId);
        }
    }

    private List<BillingRecord> closeLocked(UUID projectId, YearMonth month) {
        LocalDate firstDay = month.atDay(1);
        Project project = dataManager.load(Project.class).id(projectId).one();

        Map<UUID, BillingRecord> records = dataManager.load(BillingRecord.class)
                .query("select r from ts_BillingRecord r where r.projectId = :projectId and r.month = :month")
                .parameter("projectId", projectId)
                .parameter("month", firstDay)
                .list().stream()
                .collect(Collectors.toMap(BillingRecord::getUserId, Function.identity()));

        List<TimeEntry> approved = dataManager.load(TimeEntry.class)
                .query("""
                        select e from ts_TimeEntry e
                        where e.task.project.id = :projectId and e.status = :approved
                          and e.date between :firstDay and :lastDay""")
                .parameter("projectId", projectId)
                .parameter("approved", TimeEntryStatus.APPROVED)
                .parameter("firstDay", firstDay)
                .parameter("lastDay", month.atEndOfMonth())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("user", FetchPlan.BASE))
                .list();
        if (approved.isEmpty()) {
            return sorted(records.values());
        }

        // 1. Close and link the entries. Not committed yet, but flushed: the rules have already run.
        Map<UUID, BillingRecord> touched = new HashMap<>();
        SaveContext entries = new SaveContext();
        for (TimeEntry entry : approved) {
            User user = entry.getUser();
            BillingRecord record = touched.computeIfAbsent(user.getId(), userId -> {
                BillingRecord existing = records.get(userId);
                return existing != null ? existing : newRecord(projectId, userId, firstDay);
            });
            record.setProjectName(project.getName());
            record.setUsername(user.getUsername());
            entry.setStatus(TimeEntryStatus.CLOSED);
            entry.setBillingRecordId(record.getId());
            entries.saving(entry);
        }
        dataManager.save(entries.setDiscardSaved(true));

        // 2. Recalculate from every entry linked to the records, including the ones just linked.
        Map<UUID, Long> minutes = dataManager.loadValues("""
                        select e.billingRecordId, sum(e.spentTime) from ts_TimeEntry e
                        where e.billingRecordId in :ids
                        group by e.billingRecordId""")
                .properties("recordId", "minutes")
                .parameter("ids", touched.values().stream().map(BillingRecord::getId).toList())
                .list().stream()
                .collect(Collectors.toMap(r -> r.getValue("recordId"), r -> toLong(r.getValue("minutes"))));
        LocalDate today = LocalDate.now();
        SaveContext billing = new SaveContext();
        for (BillingRecord record : touched.values()) {
            record.setSpentTime(new SpentTime(minutes.getOrDefault(record.getId(), 0L)));
            record.setClosedDate(today);
            billing.saving(record);
        }

        // 3. The reports store commits here, in its own transaction; the main one commits when this method returns.
        EntitySet saved = dataManager.save(billing);
        for (BillingRecord record : saved.getAll(BillingRecord.class)) {
            records.put(record.getUserId(), record);
        }
        return sorted(records.values());
    }

    private BillingRecord newRecord(UUID projectId, UUID userId, LocalDate month) {
        BillingRecord record = dataManager.create(BillingRecord.class);
        record.setProjectId(projectId);
        record.setUserId(userId);
        record.setMonth(month);
        return record;
    }

    private static List<BillingRecord> sorted(Collection<BillingRecord> records) {
        return records.stream().sorted(Comparator.comparing(BillingRecord::getUsername)).toList();
    }

    /**
     * SUM over the SpentTime column skips the converter and returns a plain number.
     */
    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
