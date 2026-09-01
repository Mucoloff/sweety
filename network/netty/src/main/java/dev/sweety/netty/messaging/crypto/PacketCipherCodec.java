package dev.sweety.netty.messaging.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.List;

/**
 * Zero-copy AEAD (AES-GCM) cipher codec for Netty streaming pipelines.
 * Uses native JDK 21 in-place {@link ByteBuffer} crypto without intermediate byte[] copies.
 */
public final class PacketCipherCodec extends ByteToMessageCodec<ByteBuf> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // 96-bit standard GCM IV
    private static final int TAG_LENGTH_BITS = 128; // 128-bit authentication tag

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public PacketCipherCodec(byte[] keyBytes) {
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public static PacketCipherCodec of(byte[] keyBytes) {
        return new PacketCipherCodec(keyBytes);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        if (!msg.isReadable()) return;

        // 1. Generate 12-byte IV
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        out.writeBytes(iv);

        // 2. Setup cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

        // 3. Encrypt directly from msg ByteBuffer to out ByteBuffer
        ByteBuffer inNio = msg.nioBuffer();
        int outputSize = cipher.getOutputSize(inNio.remaining());
        out.ensureWritable(outputSize);

        ByteBuffer outNio = out.internalNioBuffer(out.writerIndex(), outputSize);
        int written = cipher.doFinal(inNio, outNio);
        out.writerIndex(out.writerIndex() + written);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < IV_LENGTH + (TAG_LENGTH_BITS / 8)) {
            // Wait for at least IV + Tag
            return;
        }

        in.markReaderIndex();

        // 1. Read 12-byte IV
        byte[] iv = new byte[IV_LENGTH];
        in.readBytes(iv);

        // 2. Setup cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        // 3. Decrypt ciphertext + tag directly into decoded ByteBuf
        int cipherLen = in.readableBytes();
        ByteBuffer inNio = in.nioBuffer(in.readerIndex(), cipherLen);

        int plainSize = cipher.getOutputSize(cipherLen);
        ByteBuf plainBuf = ctx.alloc().buffer(plainSize);
        ByteBuffer outNio = plainBuf.internalNioBuffer(0, plainSize);

        try {
            int written = cipher.doFinal(inNio, outNio);
            plainBuf.writerIndex(written);
            in.skipBytes(cipherLen);
            out.add(plainBuf);
        } catch (Exception e) {
            plainBuf.release();
            in.resetReaderIndex();
            throw e;
        }
    }
}
