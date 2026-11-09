package io.uitrainer.platform.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum GroupStatus implements EnumClass<String> {
    CREATED("CREATED"),
    ACTIVE("ACTIVE"),
    CLOSED("CLOSED");

    private final String id;

    GroupStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static GroupStatus fromId(String id) {
        for (GroupStatus value : GroupStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
