package dev.sweety.netty.messaging.compress;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Adaptive zero-allocation compression codec.
 * Packets under the threshold bypass compression to avoid negative compression overhead.
 */
public final class PacketCompressionCodec extends ByteToMessageCodec<ByteBuf> {

    public static final int DEFAULT_THRESHOLD = 128; // bytes

    private static final byte FLAG_UNCOMPRESSED = 0x00;
    private static final byte FLAG_COMPRESSED = 0x01;

    private final int threshold;
    private final ThreadLocal<Deflater> deflaters;
    private final ThreadLocal<Inflater> inflaters;

    public PacketCompressionCodec(int threshold) {
        this.threshold = threshold;
        this.deflaters = ThreadLocal.withInitial(() -> new Deflater(Deflater.BEST_SPEED));
        this.inflaters = ThreadLocal.withInitial(Inflater::new);
    }

    public static PacketCompressionCodec withThreshold(int threshold) {
        return new PacketCompressionCodec(threshold);
    }

    public static PacketCompressionCodec defaultThreshold() {
        return new PacketCompressionCodec(DEFAULT_THRESHOLD);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int readable = msg.readableBytes();
        if (readable == 0) return;

        if (readable < threshold) {
            out.writeByte(FLAG_UNCOMPRESSED);
            out.writeInt(readable);
            out.writeBytes(msg);
            return;
        }

        // Compress
        out.writeByte(FLAG_COMPRESSED);
        out.writeInt(readable); // Uncompressed original size

        int compressedLenPlaceholder = out.writerIndex();
        out.writeInt(0); // Placeholder for compressed length

        Deflater deflater = deflaters.get();
        deflater.reset();

        ByteBuffer inNio = msg.nioBuffer();
        deflater.setInput(inNio);
        deflater.finish();

        byte[] temp = new byte[Math.min(readable, 4096)];
        int totalCompressed = 0;
        while (!deflater.finished()) {
            int count = deflater.deflate(temp);
            out.writeBytes(temp, 0, count);
            totalCompressed += count;
        }
        // Patch compressed length
        out.setInt(compressedLenPlaceholder, totalCompressed);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) return;

        in.markReaderIndex();
        byte flag = in.readByte();

        if (flag == FLAG_UNCOMPRESSED) {
            if (in.readableBytes() < 4) {
                in.resetReaderIndex();
                return;
            }
            int len = in.readInt();
            if (in.readableBytes() < len) {
                in.resetReaderIndex();
                return;
            }
            out.add(in.readRetainedSlice(len));
            return;
        }

        if (flag == FLAG_COMPRESSED) {
            if (in.readableBytes() < 8) {
                in.resetReaderIndex();
                return;
            }

            int uncompressedSize = in.readInt();
            int compressedLen = in.readInt();

            if (in.readableBytes() < compressedLen) {
                in.resetReaderIndex();
                return;
            }

            ByteBuf decompressed = ctx.alloc().buffer(uncompressedSize);
            Inflater inflater = inflaters.get();
            inflater.reset();

            byte[] inBytes = new byte[compressedLen];
            in.readBytes(inBytes);
            inflater.setInput(inBytes);

            byte[] temp = new byte[Math.min(uncompressedSize, 4096)];
            try {
                while (!inflater.finished() && decompressed.writerIndex() < uncompressedSize) {
                    int count = inflater.inflate(temp);
                    if (count == 0 && inflater.needsInput()) break;
                    decompressed.writeBytes(temp, 0, count);
                }
                out.add(decompressed);
            } catch (Exception e) {
                decompressed.release();
                throw e;
            }
        }
    }
}
