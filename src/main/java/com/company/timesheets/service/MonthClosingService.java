package com.company.timesheets.service;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.entity.BillingRecord;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.entity.User;
import com.company.timesheets.security.TimesheetsPolicies;
import io.jmix.core.EntitySet;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.AccessDeniedException;
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
 * Закрывает месяц проекта: утверждённое время попадает в {@link BillingRecord} в хранилище {@code reports},
 * а списания получают статус {@code CLOSED}.
 * <p>
 * У двух хранилищ раздельные транзакции. Сначала в основной транзакции меняются списания (при сохранении
 * срабатывают правила и оптимистичные блокировки), затем коммитятся записи биллинга, последней — основная транзакция.
 * Если последний коммит упадёт, запись биллинга обгонит списания; повторный запуск это исправит, потому что время
 * записи всегда пересчитывается из привязанных к ней списаний, а не накапливается.
 * <p>
 * Закрытия одного проекта идут по одному: пессимистичная блокировка проекта держится с момента до начала
 * основной транзакции и до её коммита, поэтому следующее закрытие видит списания уже закрытыми.
 */
@Service
public class MonthClosingService {

    /**
     * Имя пессимистичной блокировки; id блокировки — id проекта.
     */
    public static final String LOCK_NAME = "ts_MonthClosing";

    // Без ограничений: закрытие должно выставить в биллинг все утверждённые списания месяца, а не видимые вызывающему.
    // Кто может закрывать, решает политика закрытия месяца, её проверяет closeMonth.
    private final UnconstrainedDataManager dataManager;
    private final LockManager lockManager;
    private final TransactionTemplate transaction;
    private final ProjectAccessSupport projectAccessSupport;

    public MonthClosingService(UnconstrainedDataManager dataManager, LockManager lockManager,
                               PlatformTransactionManager transactionManager,
                               ProjectAccessSupport projectAccessSupport) {
        this.dataManager = dataManager;
        this.lockManager = lockManager;
        this.transaction = new TransactionTemplate(transactionManager);
        this.projectAccessSupport = projectAccessSupport;
    }

    /**
     * Выставляет в биллинг утверждённые списания проекта за месяц и возвращает записи проекта за месяц,
     * по одной на пользователя, отсортированные по username. Закрыть можно только прошедшие месяцы.
     *
     * @throws MonthClosingLockedException если закрытие того же проекта уже идёт
     * @throws AccessDeniedException если у пользователя нет политики {@link TimesheetsPolicies#MONTH_CLOSING}
     */
    public List<BillingRecord> closeMonth(UUID projectId, YearMonth month) {
        if (!projectAccessSupport.isPermitted(TimesheetsPolicies.MONTH_CLOSING)) {
            throw new AccessDeniedException("specific", TimesheetsPolicies.MONTH_CLOSING);
        }
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

        // 1. Закрываем и привязываем списания. Ещё не закоммичено, но сброшено в БД: правила уже отработали.
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

        // 2. Пересчитываем по всем списаниям, привязанным к записям, включая только что привязанные.
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

        // 3. Хранилище reports коммитится здесь, в своей транзакции; основная коммитится при выходе из метода.
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
     * SUM по колонке SpentTime обходит конвертер и возвращает просто число.
     */
    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
