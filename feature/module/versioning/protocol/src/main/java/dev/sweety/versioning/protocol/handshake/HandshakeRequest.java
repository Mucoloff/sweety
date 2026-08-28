package dev.sweety.versioning.protocol.handshake;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.versioning.version.LauncherInfo;

import java.util.Objects;

public class HandshakeRequest extends PacketTransaction.Transaction {

    private LauncherInfo info;

    public HandshakeRequest() {
    }

    public HandshakeRequest(final LauncherInfo info) {
        this.info = info;
    }

    public LauncherInfo getInfo() {
        return info;
    }

    public void setInfo(final LauncherInfo info) {
        this.info = info;
    }

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writeObject(info);
    }

    @Override
    public void read(final BufferReader buffer) {
        this.info = buffer.readObject(LauncherInfo.DECODER);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof HandshakeRequest that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(info, that.info);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), info);
    }

    @Override
    public String toString() {
        return "HandshakeRequest{info=%s}".formatted(info);
    }
}
