package com.company.timesheets.entity;

import com.company.timesheets.datatype.SpentTime;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.Store;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Closed time of one user on one project for one month. Lives in the {@code reports} store, so the project
 * and the user are kept as ids and name snapshots, not as references.
 */
@Store(name = "reports")
@JmixEntity
@Entity(name = "ts_BillingRecord")
@Table(name = "TS_BILLING_RECORD", indexes = {
        @Index(name = "IDX_TS_BILLING_RECORD_UNQ", columnList = "PROJECT_ID, USER_ID, MONTH_", unique = true)
})
public class BillingRecord {

    @Id
    @Column(name = "ID", nullable = false)
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @Column(name = "PROJECT_ID", nullable = false)
    private UUID projectId;

    @NotNull
    @Column(name = "PROJECT_NAME", nullable = false)
    private String projectName;

    @NotNull
    @Column(name = "USER_ID", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "USERNAME", nullable = false)
    private String username;

    /**
     * First day of the month.
     */
    @NotNull
    @Column(name = "MONTH_", nullable = false)
    private LocalDate month;

    @NotNull
    @Column(name = "SPENT_TIME", nullable = false)
    private SpentTime spentTime;

    @NotNull
    @Column(name = "CLOSED_DATE", nullable = false)
    private LocalDate closedDate;

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

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(final UUID projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(final String projectName) {
        this.projectName = projectName;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(final UUID userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public LocalDate getMonth() {
        return month;
    }

    public void setMonth(final LocalDate month) {
        this.month = month;
    }

    public SpentTime getSpentTime() {
        return spentTime;
    }

    public void setSpentTime(final SpentTime spentTime) {
        this.spentTime = spentTime;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(final LocalDate closedDate) {
        this.closedDate = closedDate;
    }

    @InstanceName
    @DependsOnProperties({"projectName", "username", "month"})
    public String getDisplayName() {
        return projectName + " / " + username + " / " + month;
    }
}
