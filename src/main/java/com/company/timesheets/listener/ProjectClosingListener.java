package com.company.timesheets.listener;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectStatus;
import com.company.timesheets.entity.TimeEntryStatus;
import com.company.timesheets.service.MonthClosingService;
import io.jmix.core.Messages;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.pessimisticlock.LockManager;
import io.jmix.pessimisticlock.entity.LockInfo;
import io.jmix.pessimisticlock.entity.LockNotSupported;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A project cannot be closed while it has approved time entries: they must be billed by closing their months
 * first, otherwise that time would never reach billing. Nor can it be closed while one of its months is being
 * closed: that closing has not committed yet, so its entries still look approved.
 */
@Component
public class ProjectClosingListener {

    private final UnconstrainedDataManager dataManager;
    private final Messages messages;
    private final LockManager lockManager;

    public ProjectClosingListener(UnconstrainedDataManager dataManager, Messages messages, LockManager lockManager) {
        this.dataManager = dataManager;
        this.messages = messages;
        this.lockManager = lockManager;
    }

    @EventListener
    public void onProjectChanged(EntityChangedEvent<Project> event) {
        if (event.getType() != EntityChangedEvent.Type.UPDATED || !event.getChanges().isChanged("status")) {
            return;
        }
        Project project = dataManager.load(event.getEntityId()).optional().orElse(null);
        if (project == null || project.getStatus() != ProjectStatus.CLOSED) {
            return;
        }

        LockInfo closing = lockManager.getLockInfo(MonthClosingService.LOCK_NAME, project.getId().toString());
        if (closing != null && !(closing instanceof LockNotSupported)) {
            throw new TimeEntryRuleException(messages.formatMessage(getClass(), "project.monthClosingInProgress",
                    closing.getUsername()));
        }

        long approved = dataManager.loadValue(
                        "select count(e) from ts_TimeEntry e where e.task.project = :project and e.status = :approved",
                        Long.class)
                .parameter("project", project)
                .parameter("approved", TimeEntryStatus.APPROVED)
                .one();
        if (approved > 0) {
            throw new TimeEntryRuleException(messages.getMessage(getClass(), "project.approvedEntriesNotBilled"));
        }
    }
}
