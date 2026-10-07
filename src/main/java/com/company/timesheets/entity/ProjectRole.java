package com.company.timesheets.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JmixEntity
@Entity(name = "ts_ProjectRole")
@Table(name = "TS_PROJECT_ROLE")
public class ProjectRole {

    @Id
    @Column(name = "ID", nullable = false)
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @InstanceName
    @NotNull
    @Column(name = "NAME", nullable = false)
    private String name;

    @NotNull
    @Column(name = "TYPE", nullable = false)
    private String type;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

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

    public ProjectRoleType getType() {
        return type == null ? null : ProjectRoleType.fromId(type);
    }

    public void setType(final ProjectRoleType type) {
        this.type = type == null ? null : type.getId();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }
}
