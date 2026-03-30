package dev.sweety.data;

public interface PrettyEnum {

    String name();

    default String prettyName() {
        return this.name().toLowerCase();
    }

}
