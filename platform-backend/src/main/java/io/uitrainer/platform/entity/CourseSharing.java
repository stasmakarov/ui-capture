package io.uitrainer.platform.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum CourseSharing implements EnumClass<String> {
    NONE("NONE"),
    ALL_TEACHERS("ALL_TEACHERS"),
    SELECTED("SELECTED");

    private final String id;

    CourseSharing(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static CourseSharing fromId(String id) {
        for (CourseSharing value : values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
