package io.uitrainer.platform.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum CourseProgressStatus implements EnumClass<String> {
    NOT_STARTED("NOT_STARTED"),
    STARTED("STARTED"),
    COMPLETED("COMPLETED");

    private final String id;

    CourseProgressStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static CourseProgressStatus fromId(String id) {
        for (CourseProgressStatus value : CourseProgressStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
