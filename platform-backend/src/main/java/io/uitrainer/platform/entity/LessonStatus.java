package io.uitrainer.platform.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum LessonStatus implements EnumClass<String> {
    DRAFT("DRAFT"),
    REVIEW("REVIEW"),
    PUBLISHED("PUBLISHED"),
    ARCHIVED("ARCHIVED");

    private final String id;

    LessonStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static LessonStatus fromId(String id) {
        for (LessonStatus at : LessonStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
