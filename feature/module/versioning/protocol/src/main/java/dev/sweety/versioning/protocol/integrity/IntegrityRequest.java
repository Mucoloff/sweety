package dev.sweety.versioning.protocol.integrity;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.PacketTransaction;

import java.util.Objects;

/**
 * Client → server: "what is the SHA-256 of the bytes you have served for this download token so far?".
 * Sent over the authenticated Netty channel while the HTTP download streams, for progressive
 * integrity verification.
 */
public class IntegrityRequest extends PacketTransaction.Transaction {

    private String token;

    public IntegrityRequest() {}

    public IntegrityRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeString(token == null ? "" : token);
    }

    @Override
    public void read(BufferReader buffer) {
        this.token = buffer.readString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntegrityRequest that)) return false;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token);
    }
}
