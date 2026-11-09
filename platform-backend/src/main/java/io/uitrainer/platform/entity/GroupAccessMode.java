package io.uitrainer.platform.entity;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

public enum GroupAccessMode implements EnumClass<String> {
    ALL_AT_ONCE("ALL_AT_ONCE"),
    SEQUENTIAL("SEQUENTIAL");

    private final String id;

    GroupAccessMode(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static GroupAccessMode fromId(String id) {
        for (GroupAccessMode value : GroupAccessMode.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
