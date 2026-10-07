package com.company.timesheets.listener;

import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectStatus;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.TimeEntryStatus;
import io.jmix.core.SaveContext;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.event.EntityChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Closes the approved time entries of a project in the same transaction in which the project is closed.
 */
@Component
public class ProjectClosingListener {

    private final UnconstrainedDataManager dataManager;

    public ProjectClosingListener(UnconstrainedDataManager dataManager) {
        this.dataManager = dataManager;
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

        List<TimeEntry> approved = dataManager.load(TimeEntry.class)
                .query("select e from ts_TimeEntry e where e.task.project = :project and e.status = :approved")
                .parameter("project", project)
                .parameter("approved", TimeEntryStatus.APPROVED)
                .list();
        if (approved.isEmpty()) {
            return;
        }
        SaveContext saveContext = new SaveContext();
        for (TimeEntry entry : approved) {
            entry.setStatus(TimeEntryStatus.CLOSED);
            saveContext.saving(entry);
        }
        dataManager.save(saveContext);
    }
}
