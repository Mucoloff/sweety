package dev.sweety.versioning.version;

public interface PrettyEnum {

    String name();

    default String prettyName() {
        return this.name().toLowerCase();
    }

}
