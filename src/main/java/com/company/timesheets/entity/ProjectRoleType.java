package com.company.timesheets.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.jspecify.annotations.Nullable;

public enum ProjectRoleType implements EnumClass<String> {

    MANAGER("manager"),
    APPROVER("approver"),
    MEMBER("member"),
    OBSERVER("observer");

    private final String id;

    ProjectRoleType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static ProjectRoleType fromId(String id) {
        for (ProjectRoleType value : ProjectRoleType.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
