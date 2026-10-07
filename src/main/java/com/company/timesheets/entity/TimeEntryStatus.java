package com.company.timesheets.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.jspecify.annotations.Nullable;

public enum TimeEntryStatus implements EnumClass<String> {

    NEW("new"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CLOSED("closed");

    private final String id;

    TimeEntryStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static TimeEntryStatus fromId(String id) {
        for (TimeEntryStatus value : TimeEntryStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
