package dev.sweety.versioning.protocol.integrity;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.PacketTransaction;

import java.util.Arrays;

/**
 * Server → client: rolling SHA-256 of the bytes served so far for a download token, plus the final
 * HMAC-SHA256 / Ed25519 tags once the transfer is complete. {@code known=false} means the token has
 * no live session (unknown, expired, or finished and reaped).
 *
 * <p>{@code hmacHex}/{@code ed25519Base64} are empty until {@code complete} and stay empty if the
 * server has that signing scheme disabled.
 */
public class IntegrityResponse extends PacketTransaction.Transaction {

    private boolean known;
    private long bytesHashed;
    private long totalBytes;
    private byte[] rollingSha256;
    private boolean complete;
    private String hmacHex;
    private String ed25519Base64;

    public IntegrityResponse() {}

    public IntegrityResponse(boolean known, long bytesHashed, long totalBytes, byte[] rollingSha256,
                             boolean complete, String hmacHex, String ed25519Base64) {
        this.known = known;
        this.bytesHashed = bytesHashed;
        this.totalBytes = totalBytes;
        this.rollingSha256 = rollingSha256 == null ? new byte[0] : rollingSha256;
        this.complete = complete;
        this.hmacHex = hmacHex == null ? "" : hmacHex;
        this.ed25519Base64 = ed25519Base64 == null ? "" : ed25519Base64;
    }

    public static IntegrityResponse unknown() {
        return new IntegrityResponse(false, 0L, -1L, new byte[0], false, "", "");
    }

    public boolean isKnown() { return known; }
    public long getBytesHashed() { return bytesHashed; }
    public long getTotalBytes() { return totalBytes; }
    public byte[] getRollingSha256() { return rollingSha256; }
    public boolean isComplete() { return complete; }
    public String getHmacHex() { return hmacHex; }
    public String getEd25519Base64() { return ed25519Base64; }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeBoolean(known);
        buffer.writeVarLong(bytesHashed);
        buffer.writeVarLong(totalBytes);
        // rollingSha256 is only meaningful when known=true (unknown session -> always empty), so gate on
        // the flag already on the wire instead of paying a length prefix for the empty case too.
        if (known) buffer.writeByteArray(rollingSha256);
        buffer.writeBoolean(complete);
        buffer.writeString(hmacHex);
        buffer.writeString(ed25519Base64);
    }

    @Override
    public void read(BufferReader buffer) {
        this.known = buffer.readBoolean();
        this.bytesHashed = buffer.readVarLong();
        this.totalBytes = buffer.readVarLong();
        this.rollingSha256 = this.known ? buffer.readByteArray() : new byte[0];
        this.complete = buffer.readBoolean();
        this.hmacHex = buffer.readString();
        this.ed25519Base64 = buffer.readString();
    }

    @Override
    public String toString() {
        return "IntegrityResponse{known=" + known + ", bytesHashed=" + bytesHashed
                + ", totalBytes=" + totalBytes + ", complete=" + complete
                + ", rollingSha256=" + Arrays.toString(rollingSha256) + '}';
    }
}
