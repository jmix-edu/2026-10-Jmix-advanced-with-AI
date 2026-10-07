package com.company.timesheets.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JmixEntity
@Entity(name = "ts_Task")
@Table(name = "TS_TASK", indexes = {
        @Index(name = "IDX_TS_TASK_ON_PROJECT", columnList = "PROJECT_ID"),
        @Index(name = "IDX_TS_TASK_ON_TASK_TYPE", columnList = "TASK_TYPE_ID")
})
public class Task {

    @Id
    @Column(name = "ID", nullable = false)
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @Column(name = "NAME", nullable = false)
    private String name;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @NotNull
    @Column(name = "STATUS", nullable = false)
    private String status = TaskStatus.ACTIVE.getId();

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "PROJECT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Project project;

    @JoinColumn(name = "TASK_TYPE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private TaskType taskType;

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status == null ? null : TaskStatus.fromId(status);
    }

    public void setStatus(final TaskStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public Project getProject() {
        return project;
    }

    public void setProject(final Project project) {
        this.project = project;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(final TaskType taskType) {
        this.taskType = taskType;
    }

    @InstanceName
    @DependsOnProperties({"name", "project"})
    public String getDisplayName() {
        return project == null ? name : "[" + project.getName() + "] " + name;
    }
}
