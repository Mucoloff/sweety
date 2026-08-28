package dev.sweety.netty.messaging.listener.decoder;

import dev.sweety.data.buffer.BufferPool;
import dev.sweety.data.compress.CompressUtils;
import dev.sweety.data.compress.CompressionLimitException;
import dev.sweety.exception.PacketDecodeException;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.CRC32C;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class PacketDecoder {

    private static final int MAX_PAYLOAD_SIZE = 1 << 20; // 1 MB — max compressed payload accepted off the wire
    private static final int MAX_UNCOMPRESSED_SIZE = 16 << 20; // 16 MB — max decompressed output allowed (zipbomb size cap)
    private static final int MAX_COMPRESSION_RATIO = 100; // decompressed/compressed sanity limit (zipbomb ratio cap)
    private static final long MAX_DECOMPRESS_NANOS = 250_000_000L; // 250ms — CPU-bomb wall-clock guard on inflate loop

    private static final ByteBuffer SEED_BUFFER = ByteBuffer.wrap(new byte[]{
            (byte) (Messenger.SEED >>> 24), (byte) (Messenger.SEED >>> 16),
            (byte) (Messenger.SEED >>> 8), (byte) Messenger.SEED
    }).asReadOnlyBuffer();
    private final PacketRegistry packetRegistry;
    /**
     * Per-connection timestamp memory: each PacketDecoder is created fresh per channel (see Messenger), so this never leaks across connections.
     */
    private final TimestampState timestampState = new TimestampState();

    public PacketDecoder(final PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    private static boolean cantRead(final PacketBuffer in, int len) {
        if (in.readableBytes() >= len) return false;
        in.resetReaderIndex();
        return true;
    }

    public void decode(final PacketBuffer in, final List<Packet> out) throws PacketDecodeException {
        decode(in, out, this.packetRegistry, this.timestampState);
    }

    /**
     * Stateless one-shot decode: always expects an absolute timestamp, mirroring {@link dev.sweety.netty.messaging.listener.encoder.PacketEncoder#encode(Packet, PacketBuffer, PacketRegistry)}.
     */
    public static void decode(final PacketBuffer in, final List<Packet> out, final PacketRegistry packetRegistry) throws PacketDecodeException {
        decode(in, out, packetRegistry, null);
    }

    /**
     * Mutable per-connection timestamp memory mirroring {@code PacketEncoder.TimestampState}: only
     * committed once a packet is fully decoded and delivered, so an aborted partial-frame decode
     * attempt (incomplete data, retried once more bytes arrive) never double-advances it.
     */
    public static final class TimestampState {
        private long last;
        private boolean has;
    }

    public static void decode(final PacketBuffer in, final List<Packet> out, final PacketRegistry packetRegistry, final TimestampState timestampState) throws PacketDecodeException {
        if (in.readableBytes() - Integer.BYTES < 2) return; // minimal header
        in.markReaderIndex();

        try {
            // Check if we can read at least the flags
            final int id = in.readVarInt();
            if (cantRead(in, 1)) return;

            final boolean hasTimestamp = in.readBoolean();
            final boolean hasPayload = in.readBoolean();
            final long timestamp;

            if (hasTimestamp) {
                // Check if we can read the varlong
                if (cantRead(in, 1)) return;
                timestamp = (timestampState != null && timestampState.has)
                        ? timestampState.last + in.readVarLongZigZag()
                        : in.readVarLong();
            } else timestamp = Messenger.timeMode.now();

            // Validate checksum
            final CRC32C crc32 = BufferPool.DEFAULT.acquireCrc32c();
            crc32.update(SEED_BUFFER.duplicate());

            final ByteBuf payloadBuf;
            if (!hasPayload) {
                payloadBuf = Unpooled.EMPTY_BUFFER;
            } else {
                final boolean compressed = in.readBoolean();

                if (cantRead(in, 1)) return;

                if (compressed) {
                    final int uncompressedLen = in.readVarInt();
                    if (cantRead(in, 1)) return;
                    final int compressedLen = in.readVarInt();

                    // Anti-zipbomb: reject oversized compressed frame before touching the buffer.
                    if (compressedLen < 0 || compressedLen > MAX_PAYLOAD_SIZE)
                        throw PacketDecodeException.of("Compressed payload too large (" + compressedLen + " bytes) for packetId " + id);
                    // Anti-zipbomb: reject declared uncompressed size before allocating for it.
                    if (uncompressedLen < 0 || uncompressedLen > MAX_UNCOMPRESSED_SIZE)
                        throw PacketDecodeException.of("Declared uncompressed size too large (" + uncompressedLen + " bytes) for packetId " + id);
                    // Anti-zipbomb: reject anomalous expansion ratio.
                    if (compressedLen == 0 ? uncompressedLen > 0 : uncompressedLen > (long) compressedLen * MAX_COMPRESSION_RATIO)
                        throw PacketDecodeException.of("Compression ratio too high (" + uncompressedLen + "/" + compressedLen + ") for packetId " + id);

                    if (cantRead(in, compressedLen)) return;

                    final PacketBuffer slice = in.readRetainedSlice(compressedLen);
                    final ByteBuf nioView = slice.nettyBuffer();

                    final ByteBuffer nio = nioView.nioBuffer(0, compressedLen);
                    crc32.update(nio);

                    // srcView is a zero-copy NIO view — no byte[] borrow for input
                    final ByteBuffer srcView = nioView.nioBuffer(0, compressedLen);
                    final Inflater inflater = BufferPool.DEFAULT.acquireInflater();
                    try {
                        final byte[] decompressed = new byte[uncompressedLen];
                        try {
                            CompressUtils.inflateBounded(srcView, decompressed, uncompressedLen, inflater,
                                    MAX_DECOMPRESS_NANOS, MAX_UNCOMPRESSED_SIZE);
                        } catch (DataFormatException e) {
                            throw PacketDecodeException.of("Failed to inflate payload", e);
                        } catch (CompressionLimitException e) {
                            throw PacketDecodeException.of(e.getMessage() + " for packetId " + id);
                        }
                        payloadBuf = Unpooled.wrappedBuffer(decompressed); // zero-copy wrap
                    } finally {
                        BufferPool.DEFAULT.releaseInflater(inflater);
                        slice.release();
                    }
                } else {
                    final int payloadLength = in.readVarInt();
                    if (cantRead(in, payloadLength)) return;

                    final PacketBuffer slice = in.readRetainedSlice(payloadLength);
                    final ByteBuf nioView = slice.nettyBuffer();

                    final ByteBuffer nio = nioView.nioBuffer(0, payloadLength);
                    crc32.update(nio);

                    payloadBuf = nioView; // pass through retained slice
                }
            }

            // Check for checksum VarInt
            if (in.readableBytes() < 1) {
                if (payloadBuf != Unpooled.EMPTY_BUFFER)
                    payloadBuf.release();
                in.resetReaderIndex();
                return;
            }

            final int checksum = in.readVarInt();
            final int check = (int) crc32.getValue();
            if (check != checksum) {
                if (payloadBuf != Unpooled.EMPTY_BUFFER) payloadBuf.release();
                throw PacketDecodeException.of("Invalid checksum for packetId " + id);
            }

            final Packet packet;
            try {
                byte[] bytes;
                if (!payloadBuf.isReadable()) {
                    bytes = new byte[0];
                } else {
                    bytes = new byte[payloadBuf.readableBytes()];
                    payloadBuf.getBytes(payloadBuf.readerIndex(), bytes);
                }
                packet = packetRegistry.constructPacket(id, timestamp, bytes);
            } catch (Exception e) {
                throw PacketDecodeException.of("Failed to decode packet (" + id + ")", e);
            } finally {
                if (payloadBuf != Unpooled.EMPTY_BUFFER) payloadBuf.release();
            }

            // Commit timestamp state only now that the packet is fully decoded and about to be
            // delivered — never on an aborted partial-frame attempt (see TimestampState javadoc).
            if (hasTimestamp && timestampState != null) {
                timestampState.last = timestamp;
                timestampState.has = true;
            }
            out.add(packet);
        } catch (IndexOutOfBoundsException ignored) {
            // Incomplete frame: wait for the next network chunk instead of closing channel.
            in.resetReaderIndex();
        }
    }

}
