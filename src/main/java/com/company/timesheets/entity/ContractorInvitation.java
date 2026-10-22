package com.company.timesheets.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A contractor's participation in a project, waiting for the contractor to activate their account
 * (specs/03_access_control/05-contractor-invitation.spec.adoc). On activation it becomes a ProjectParticipant
 * and is removed. Not a participant yet, so an unactivated contractor is not "in" the project.
 */
@JmixEntity
@Entity(name = "ts_ContractorInvitation")
@Table(name = "TS_CONTRACTOR_INVITATION", indexes = {
        @Index(name = "IDX_TS_CONTRACTOR_INVITATION_ON_USER_PROJECT", columnList = "USER_ID, PROJECT_ID",
                unique = true),
        @Index(name = "IDX_TS_CONTRACTOR_INVITATION_ON_PROJECT", columnList = "PROJECT_ID"),
        @Index(name = "IDX_TS_CONTRACTOR_INVITATION_ON_PROJECT_ROLE", columnList = "PROJECT_ROLE_ID"),
        @Index(name = "IDX_TS_CONTRACTOR_INVITATION_ON_INVITED_BY", columnList = "INVITED_BY_ID")
})
public class ContractorInvitation {

    @Id
    @Column(name = "ID", nullable = false)
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    // The registration cleanup removes invitations itself before the user (RegistrationService); with
    // DataManager.remove(user) alone the foreign key fails.
    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "USER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "PROJECT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Project project;

    @NotNull
    @OnDeleteInverse(DeletePolicy.DENY)
    @JoinColumn(name = "PROJECT_ROLE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ProjectRole projectRole;

    // Who invited; kept for the email and history, cleared if that user is removed.
    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "INVITED_BY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private User invitedBy;

    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public ProjectRole getProjectRole() {
        return projectRole;
    }

    public void setProjectRole(ProjectRole projectRole) {
        this.projectRole = projectRole;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(User invitedBy) {
        this.invitedBy = invitedBy;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    @InstanceName
    @DependsOnProperties({"user", "project"})
    public String getDisplayName() {
        String userName = user == null ? "" : user.getUsername();
        String projectName = project == null ? "" : project.getName();
        return userName + " → " + projectName;
    }
}
