package dev.sweety.versioning.version.artifact;

import dev.sweety.data.PrettyEnum;
import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.versioning.version.Version;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record Artifact(String name) implements PrettyEnum, Encoder {

    private Artifact(BufferReader buf) {
        this(buf.readString());
    }

    public static final Artifact APP = new Artifact("APP");
    public static final Artifact LAUNCHER = new Artifact("LAUNCHER");

    public Artifact {
        Objects.requireNonNull(name, "name cannot be null");
    }

    @Override
    public @NotNull String prettyName() {
        return name().toLowerCase();
    }

    @Override
    public @NotNull String toString() {
        return name;
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeString(name);
    }

    public static final CallableDecoder<Artifact> DECODER = Artifact::new;
}
