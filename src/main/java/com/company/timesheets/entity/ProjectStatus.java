package com.company.timesheets.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.jspecify.annotations.Nullable;

public enum ProjectStatus implements EnumClass<String> {

    OPEN("open"),
    CLOSED("closed");

    private final String id;

    ProjectStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static ProjectStatus fromId(String id) {
        for (ProjectStatus value : ProjectStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
