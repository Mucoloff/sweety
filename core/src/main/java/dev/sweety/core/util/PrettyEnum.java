package dev.sweety.core.util;

public interface PrettyEnum {

    String name();

    default String prettyName() {
        return this.name().toLowerCase();
    }

}
