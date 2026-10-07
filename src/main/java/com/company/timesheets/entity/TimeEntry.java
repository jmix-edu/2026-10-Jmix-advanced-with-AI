package com.company.timesheets.entity;

import com.company.timesheets.datatype.SpentTime;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@JmixEntity
@Entity(name = "ts_TimeEntry")
@Table(name = "TS_TIME_ENTRY", indexes = {
        @Index(name = "IDX_TS_TIME_ENTRY_ON_TASK", columnList = "TASK_ID"),
        @Index(name = "IDX_TS_TIME_ENTRY_ON_USER", columnList = "USER_ID")
})
public class TimeEntry {

    @Id
    @Column(name = "ID", nullable = false)
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @Column(name = "DATE_", nullable = false)
    private LocalDate date;

    @NotNull
    @Column(name = "SPENT_TIME", nullable = false)
    private SpentTime spentTime;

    @NotNull
    @Column(name = "STATUS", nullable = false)
    private String status = TimeEntryStatus.NEW.getId();

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Lob
    @Column(name = "REJECTION_REASON")
    private String rejectionReason;

    @NotNull
    @JoinColumn(name = "TASK_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Task task;

    @NotNull
    @JoinColumn(name = "USER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @PostConstruct
    public void postConstruct() {
        date = LocalDate.now();
    }

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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(final LocalDate date) {
        this.date = date;
    }

    public SpentTime getSpentTime() {
        return spentTime;
    }

    public void setSpentTime(final SpentTime spentTime) {
        this.spentTime = spentTime;
    }

    public TimeEntryStatus getStatus() {
        return status == null ? null : TimeEntryStatus.fromId(status);
    }

    public void setStatus(final TimeEntryStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(final String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(final Task task) {
        this.task = task;
    }

    public User getUser() {
        return user;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    @InstanceName
    @DependsOnProperties({"date", "task"})
    public String getDisplayName(DatatypeFormatter datatypeFormatter) {
        String datePart = date == null ? null : datatypeFormatter.formatLocalDate(date);
        String taskPart = task == null ? null : task.getDisplayName();
        if (datePart == null) {
            return taskPart;
        }
        return taskPart == null ? datePart : datePart + " " + taskPart;
    }
}
