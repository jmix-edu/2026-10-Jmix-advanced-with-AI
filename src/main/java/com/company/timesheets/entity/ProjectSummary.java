package com.company.timesheets.entity;

import com.company.timesheets.datatype.SpentTime;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;

import java.util.UUID;

/**
 * Per-project totals for a manager. Calculated by {@code HoursReportService}, never stored.
 */
@JmixEntity(name = "ts_ProjectSummary", annotatedPropertiesOnly = true)
public class ProjectSummary {

    /**
     * Same as the project id.
     */
    @JmixId
    @JmixProperty(mandatory = true)
    private UUID id;

    @JmixProperty
    private Project project;

    @JmixProperty
    private Client client;

    @JmixProperty
    private ProjectStatus status;

    @JmixProperty
    private SpentTime totalTime;

    @JmixProperty
    private SpentTime currentMonthTime;

    @JmixProperty
    private Integer participantCount;

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(final Project project) {
        this.project = project;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(final Client client) {
        this.client = client;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(final ProjectStatus status) {
        this.status = status;
    }

    public SpentTime getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(final SpentTime totalTime) {
        this.totalTime = totalTime;
    }

    public SpentTime getCurrentMonthTime() {
        return currentMonthTime;
    }

    public void setCurrentMonthTime(final SpentTime currentMonthTime) {
        this.currentMonthTime = currentMonthTime;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(final Integer participantCount) {
        this.participantCount = participantCount;
    }

    @InstanceName
    @DependsOnProperties({"project"})
    public String getDisplayName() {
        return project == null ? null : project.getName();
    }
}
