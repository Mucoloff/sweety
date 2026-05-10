package dev.sweety.versioning.version.artifact;

import dev.sweety.data.PrettyEnum;

import java.util.Objects;

public record Artifact(String name) implements PrettyEnum {

    public static final Artifact APP = new Artifact("APP");
    public static final Artifact LAUNCHER = new Artifact("LAUNCHER");

    public Artifact {
        Objects.requireNonNull(name, "name cannot be null");
    }

    @Override
    public String toString() {
        return name;
    }
}
