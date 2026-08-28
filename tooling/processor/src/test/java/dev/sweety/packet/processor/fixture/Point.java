package dev.sweety.packet.processor.fixture;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.data.buffer.io.AbstractDecoder;
import dev.sweety.data.buffer.io.AbstractEncoder;

import java.util.Objects;

public final class Point implements AbstractEncoder, AbstractDecoder {

    private int x;
    private int y;

    public Point() {}

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }
    public int y() { return y; }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeVarInt(x);
        buffer.writeVarInt(y);
    }

    @Override
    public void read(BufferReader buffer) {
        this.x = buffer.readVarInt();
        this.y = buffer.readVarInt();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point p)) return false;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
