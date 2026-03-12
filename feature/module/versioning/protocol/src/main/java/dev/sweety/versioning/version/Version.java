package dev.sweety.versioning.version;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Version(int major, int minor, int patch) implements Encoder {

    public static final Version ZERO = new Version(0, 0, 0);

    public static final CallableDecoder<Version> DECODER =
            buffer -> new Version(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeVarInt(this.major).writeVarInt(this.minor).writeVarInt(this.patch);
    }

    public boolean newerThan(final Version that) {
        if (this.major() != that.major())
            return this.major() > that.major();
        if (this.minor() != that.minor())
            return this.minor() > that.minor();
        return this.patch() > that.patch();
    }

    public Path resolve(Path parent) {
        return parent.resolve(major+"").resolve(major+"").resolve(patch+"");
    }

    @Override
    public @NotNull String toString() {
        return "%d.%d.%d".formatted(this.major, this.minor, this.patch);
    }

    public static final Pattern REGEX =
            Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    public static Version parse(String version) {
        final Matcher m = REGEX.matcher(version);
        if (!m.matches()) return ZERO;

        return new Version(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3))
        );
    }
}
