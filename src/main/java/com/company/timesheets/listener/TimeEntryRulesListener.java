package com.company.timesheets.listener;

import com.company.timesheets.datatype.SpentTime;
import com.company.timesheets.datatype.SpentTimeDatatype;
import com.company.timesheets.entity.ProjectStatus;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TaskStatus;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.event.AttributeChanges;
import io.jmix.core.event.EntityChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Правила учёта времени для {@link TimeEntry}. Срабатывает перед коммитом при каждом сохранении через {@code DataManager},
 * поэтому правила действуют одинаково для форм, сервисов и импорта. Нарушение откатывает всё сохранение.
 */
@Component
public class TimeEntryRulesListener {

    static final long MAX_MINUTES_PER_DAY = 24 * 60;

    private static final Set<String> DATA_FIELDS = Set.of("date", "spentTime", "task", "user", "description");

    private static final Map<TimeEntryStatus, Set<TimeEntryStatus>> TRANSITIONS = Map.of(
            TimeEntryStatus.NEW, EnumSet.of(TimeEntryStatus.APPROVED, TimeEntryStatus.REJECTED),
            TimeEntryStatus.APPROVED, EnumSet.of(TimeEntryStatus.CLOSED),
            TimeEntryStatus.REJECTED, EnumSet.of(TimeEntryStatus.CLOSED),
            TimeEntryStatus.CLOSED, EnumSet.noneOf(TimeEntryStatus.class));

    private static final SpentTimeDatatype SPENT_TIME_FORMAT = new SpentTimeDatatype();

    private final UnconstrainedDataManager dataManager;
    private final Messages messages;

    public TimeEntryRulesListener(UnconstrainedDataManager dataManager, Messages messages) {
        this.dataManager = dataManager;
        this.messages = messages;
    }

    @EventListener
    public void onTimeEntryChanged(EntityChangedEvent<TimeEntry> event) {
        AttributeChanges changes = event.getChanges();
        // Мягкое удаление тоже приходит как DELETED, а в изменениях лежат значения всех атрибутов до удаления.
        if (event.getType() == EntityChangedEvent.Type.DELETED) {
            checkDeletion(toStatus(changes.getOldValue("status")));
            return;
        }

        TimeEntry entry = dataManager.load(event.getEntityId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("task", task -> task.addFetchPlan(FetchPlan.BASE)
                                .add("project", FetchPlan.BASE)))
                .one();

        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            checkCreated(entry);
            return;
        }

        TimeEntryStatus oldStatus = changes.isChanged("status")
                ? toStatus(changes.getOldValue("status"))
                : entry.getStatus();
        checkUpdated(entry, oldStatus, changes);
    }

    private void checkCreated(TimeEntry entry) {
        if (entry.getStatus() != TimeEntryStatus.NEW) {
            throw violation("timeEntry.createdNotNew");
        }
        if (entry.getBillingRecordId() != null) {
            throw violation("timeEntry.billingRecordOnlyOnClosing");
        }
        checkTaskOpen(entry.getTask());
        checkSpentTime(entry);
    }

    private void checkUpdated(TimeEntry entry, TimeEntryStatus oldStatus, AttributeChanges changes) {
        if (oldStatus == TimeEntryStatus.CLOSED) {
            throw violation("timeEntry.closedImmutable");
        }

        TimeEntryStatus newStatus = entry.getStatus();
        boolean statusChanged = oldStatus != newStatus;
        if (statusChanged && !TRANSITIONS.get(oldStatus).contains(newStatus)) {
            throw new TimeEntryRuleException(messages.formatMessage(getClass(), "timeEntry.transitionNotAllowed",
                    messages.getMessage(oldStatus), messages.getMessage(newStatus)));
        }
        if (statusChanged && newStatus == TimeEntryStatus.REJECTED
                && (entry.getRejectionReason() == null || entry.getRejectionReason().isBlank())) {
            throw violation("timeEntry.rejectionReasonRequired");
        }
        // Одобренное время закрывается только в запись биллинга (MonthClosingService), поэтому ничего не теряется.
        boolean closingApproved = statusChanged && oldStatus == TimeEntryStatus.APPROVED;
        if (closingApproved && entry.getBillingRecordId() == null) {
            throw violation("timeEntry.closeApprovedThroughBilling");
        }
        if (changes.isChanged("billingRecordId") && !closingApproved) {
            throw violation("timeEntry.billingRecordOnlyOnClosing");
        }

        boolean rejectionReasonEdited = changes.isChanged("rejectionReason")
                && !(statusChanged && newStatus == TimeEntryStatus.REJECTED);
        boolean dataChanged = rejectionReasonEdited || DATA_FIELDS.stream().anyMatch(changes::isChanged);
        if (!dataChanged) {
            return;
        }
        if (oldStatus != TimeEntryStatus.NEW) {
            throw violation("timeEntry.fieldsEditableOnlyInNew");
        }
        checkTaskOpen(entry.getTask());
        if (changes.isChanged("spentTime") || changes.isChanged("date") || changes.isChanged("user")) {
            checkSpentTime(entry);
        }
    }

    private void checkDeletion(TimeEntryStatus status) {
        if (status == TimeEntryStatus.CLOSED) {
            throw violation("timeEntry.closedNotDeletable");
        }
    }

    private void checkTaskOpen(Task task) {
        if (task.getStatus() == TaskStatus.INACTIVE) {
            throw violation("timeEntry.taskInactive");
        }
        if (task.getProject().getStatus() == ProjectStatus.CLOSED) {
            throw violation("timeEntry.projectClosed");
        }
    }

    private void checkSpentTime(TimeEntry entry) {
        if (entry.getSpentTime() == null || entry.getSpentTime().minutes() == 0) {
            throw violation("timeEntry.spentTimeNotPositive");
        }
        // Сохранённая строка уже сброшена в базу (flush), поэтому запрос учитывает и её.
        List<TimeEntry> sameDay = dataManager.load(TimeEntry.class)
                .query("select e from ts_TimeEntry e where e.user = :user and e.date = :date and e.status <> :rejected")
                .parameter("user", entry.getUser())
                .parameter("date", entry.getDate())
                .parameter("rejected", TimeEntryStatus.REJECTED)
                .list();
        long total = sameDay.stream().mapToLong(e -> e.getSpentTime().minutes()).sum();
        if (total > MAX_MINUTES_PER_DAY) {
            throw new TimeEntryRuleException(messages.formatMessage(getClass(), "timeEntry.dailyLimitExceeded",
                    SPENT_TIME_FORMAT.format(new SpentTime(total))));
        }
    }

    private TimeEntryRuleException violation(String key) {
        return new TimeEntryRuleException(messages.getMessage(getClass(), key));
    }

    private static TimeEntryStatus toStatus(Object value) {
        if (value instanceof TimeEntryStatus status) {
            return status;
        }
        return value == null ? null : TimeEntryStatus.fromId(value.toString());
    }
}
