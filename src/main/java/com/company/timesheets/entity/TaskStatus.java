package com.company.timesheets.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.jspecify.annotations.Nullable;

public enum TaskStatus implements EnumClass<String> {

    ACTIVE("active"),
    INACTIVE("inactive");

    private final String id;

    TaskStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static TaskStatus fromId(String id) {
        for (TaskStatus value : TaskStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
