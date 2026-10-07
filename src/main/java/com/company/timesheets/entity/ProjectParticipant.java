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
@Entity(name = "ts_ProjectParticipant")
@Table(name = "TS_PROJECT_PARTICIPANT", indexes = {
        @Index(name = "IDX_TS_PROJECT_PARTICIPANT_ON_USER", columnList = "USER_ID"),
        @Index(name = "IDX_TS_PROJECT_PARTICIPANT_ON_PROJECT", columnList = "PROJECT_ID"),
        @Index(name = "IDX_TS_PROJECT_PARTICIPANT_ON_PROJECT_ROLE", columnList = "PROJECT_ROLE_ID")
})
public class ProjectParticipant {

    @Id
    @Column(name = "ID", nullable = false)
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @JoinColumn(name = "USER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "PROJECT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Project project;

    @NotNull
    @JoinColumn(name = "PROJECT_ROLE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ProjectRole projectRole;

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

    public User getUser() {
        return user;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(final Project project) {
        this.project = project;
    }

    public ProjectRole getProjectRole() {
        return projectRole;
    }

    public void setProjectRole(final ProjectRole projectRole) {
        this.projectRole = projectRole;
    }

    @InstanceName
    @DependsOnProperties({"user"})
    public String getDisplayName() {
        return user == null ? null : user.getDisplayName();
    }
}
