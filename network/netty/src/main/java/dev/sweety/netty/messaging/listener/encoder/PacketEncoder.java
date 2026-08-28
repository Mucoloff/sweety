package dev.sweety.netty.messaging.listener.encoder;

import dev.sweety.data.buffer.BufferPool;
import dev.sweety.data.compress.CompressUtils;
import dev.sweety.data.compress.CompressionLimitException;
import dev.sweety.file.ResourceUtils;
import dev.sweety.netty.messaging.exception.PacketEncodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;
import java.util.zip.Deflater;

public class PacketEncoder {
    static final int ZIP_THRESHOLD = 256;
    private static final int MAX_PAYLOAD_SIZE = 16 << 20; // 16 MB — reject outgoing packets this large (mirrors decoder's MAX_UNCOMPRESSED_SIZE)
    private static final long MAX_COMPRESS_NANOS = 250_000_000L; // 250ms — deflate wall-clock guard

    private static final ByteBuffer SEED_BUFFER = ByteBuffer.wrap(new byte[]{
            (byte) (Messenger.SEED >>> 24), (byte) (Messenger.SEED >>> 16),
            (byte) (Messenger.SEED >>> 8),  (byte)  Messenger.SEED
    }).asReadOnlyBuffer();
    final PacketRegistry packetRegistry;
    /** Per-connection timestamp memory: each PacketEncoder is created fresh per channel (see Messenger), so this never leaks across connections. */
    private final TimestampState timestampState = new TimestampState();

    public PacketEncoder(final PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    public void encode(final Packet packet, final PacketBuffer out) throws PacketEncodeException {
        encode(packet, out, this.packetRegistry, this.timestampState);
    }

    /** Stateless one-shot encode: always writes an absolute timestamp, no delta. */
    public static void encode(final Packet packet, final PacketBuffer out, final PacketRegistry packetRegistry) throws PacketEncodeException {
        encode(packet, out, packetRegistry, null);
    }

    /**
     * Mutable per-connection timestamp memory used to delta-encode consecutive packet timestamps
     * on the same channel. Safe across a TCP stream: ordering is guaranteed, and a fresh connection
     * gets a fresh (zeroed) state, so there's no cross-connection or reconnect desync risk.
     */
    static final class TimestampState {
        private long last;
        private boolean has;
    }

    public static void encode(final Packet packet, final PacketBuffer out, final PacketRegistry packetRegistry, final TimestampState timestampState) throws PacketEncodeException {
        int packetId = packetRegistry.getPacketId(packet.getClass());
        if (packetId == -1)
            throw new PacketEncodeException("Returned PacketId by registry is invalid (-1)");

        final boolean hasTimestamp = packet.hasTimestamp();
        final PacketBuffer payloadBuf = PacketBufferAllocator.DEFAULT.buffer();
        packet.write(payloadBuf);
        final ByteBuf payloadNetty = payloadBuf.nettyBuffer();
        final int readable = payloadNetty.readableBytes();
        final boolean hasPayload = readable > 0;

        // Hardening: refuse to build an oversized frame rather than let it blow past sane wire limits.
        if (readable > MAX_PAYLOAD_SIZE) {
            payloadBuf.release();
            throw new PacketEncodeException("Packet payload too large (" + readable + " bytes) for packetId " + packetId);
        }

        out.writeVarInt(packetId).writeBoolean(hasTimestamp).writeBoolean(hasPayload);
        if (hasTimestamp) {
            final long timestamp = packet.timestamp();
            if (timestampState != null && timestampState.has) {
                out.writeVarLongZigZag(timestamp - timestampState.last);
            } else {
                out.writeVarLong(timestamp);
            }
            // Commit deferred to the very end of the method (see there) — a later exception
            // (oversized/compression-timeout) discards this frame before it reaches the wire, and
            // committing here regardless would desync the decoder's mirrored state permanently.
        }

        // Compute checksum directly on ByteBuf
        CRC32C crc32 = BufferPool.DEFAULT.acquireCrc32c();
        crc32.update(SEED_BUFFER.duplicate());

        if (hasPayload) {
            final boolean compressed;
            ByteBuf toWrite = payloadNetty.slice(payloadNetty.readerIndex(), readable);
            toWrite.retain();

            if (readable < ZIP_THRESHOLD) {
                compressed = false;
            } else {
                // srcView is a zero-copy NIO view — no byte[] borrow for input
                ByteBuffer srcView = toWrite.nioBuffer(0, readable);
                byte[] dst = BufferPool.DEFAULT.borrowBytes(readable);
                ByteBuffer dstView = ByteBuffer.wrap(dst, 0, readable);
                Deflater deflater = BufferPool.DEFAULT.acquireDeflater();
                try {
                    int compressedLen;
                    try {
                        compressedLen = CompressUtils.deflateBounded(srcView, dstView, deflater, MAX_COMPRESS_NANOS);
                    } catch (CompressionLimitException e) {
                        throw new PacketEncodeException(e.getMessage() + " for packetId " + packetId);
                    }
                    if (compressedLen < 0 || compressedLen >= readable) {
                        compressed = false;
                    } else {
                        // new byte[] required for Unpooled.wrappedBuffer zero-copy ownership transfer
                        byte[] exact = new byte[compressedLen];
                        System.arraycopy(dst, 0, exact, 0, compressedLen);
                        toWrite.release();
                        toWrite = Unpooled.wrappedBuffer(exact);
                        compressed = true;
                    }
                } finally {
                    BufferPool.DEFAULT.returnBytes(dst);
                    BufferPool.DEFAULT.releaseDeflater(deflater);
                }
            }

            ByteBuffer nio = toWrite.nioBuffer(0, toWrite.readableBytes());
            crc32.update(nio);

            if (compressed) {
                out.writeBoolean(true).writeVarInt(readable).writeVarInt(toWrite.readableBytes());
            } else {
                out.writeBoolean(false).writeVarInt(toWrite.readableBytes());
            }
            out.nettyBuffer().writeBytes(toWrite, toWrite.readableBytes());
            toWrite.release();
        }

        int check = (int) crc32.getValue();
        out.writeVarInt(check);
        payloadBuf.release();

        // Frame fully built without throwing — now safe to commit the timestamp baseline.
        if (hasTimestamp && timestampState != null) {
            timestampState.last = packet.timestamp();
            timestampState.has = true;
        }
    }
}
