package dev.sweety.netty.messaging.crypto;

import dev.sweety.netty.messaging.compress.PacketCompressionCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class PacketCryptoAndCompressionTest {

    @Test
    public void testAesGcmCipherRoundtrip() {
        byte[] key = new byte[16]; // 128-bit AES key
        Arrays.fill(key, (byte) 0x42);

        EmbeddedChannel encryptChannel = new EmbeddedChannel(PacketCipherCodec.of(key));
        EmbeddedChannel decryptChannel = new EmbeddedChannel(PacketCipherCodec.of(key));

        String payload = "Hello Sweety Secure Protocol!";
        ByteBuf plain = Unpooled.wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8));

        // Encrypt (outbound)
        assertTrue(encryptChannel.writeOutbound(plain));
        ByteBuf encrypted = encryptChannel.readOutbound();
        assertNotNull(encrypted);
        assertNotEquals(payload, encrypted.toString(StandardCharsets.UTF_8));

        // Decrypt (inbound)
        assertTrue(decryptChannel.writeInbound(encrypted));
        ByteBuf decrypted = decryptChannel.readInbound();
        assertNotNull(decrypted);

        assertEquals(payload, decrypted.toString(StandardCharsets.UTF_8));
        decrypted.release();
    }

    @Test
    public void testCompressionRoundtripUnderAndOverThreshold() {
        EmbeddedChannel compressChannel = new EmbeddedChannel(PacketCompressionCodec.withThreshold(64));
        EmbeddedChannel decompressChannel = new EmbeddedChannel(PacketCompressionCodec.withThreshold(64));

        // 1. Under threshold: 10 bytes (uncompressed pass-through)
        String smallPayload = "Short text";
        ByteBuf smallBuf = Unpooled.wrappedBuffer(smallPayload.getBytes(StandardCharsets.UTF_8));
        assertTrue(compressChannel.writeOutbound(smallBuf));
        ByteBuf smallOut = compressChannel.readOutbound();
        assertNotNull(smallOut);

        assertTrue(decompressChannel.writeInbound(smallOut));
        ByteBuf smallDecompressed = decompressChannel.readInbound();
        assertEquals(smallPayload, smallDecompressed.toString(StandardCharsets.UTF_8));
        smallDecompressed.release();

        // 2. Over threshold: 500 bytes (compressed)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("Sweety High-Throughput Engine ");
        String largePayload = sb.toString();

        ByteBuf largeBuf = Unpooled.wrappedBuffer(largePayload.getBytes(StandardCharsets.UTF_8));
        assertTrue(compressChannel.writeOutbound(largeBuf));
        ByteBuf largeOut = compressChannel.readOutbound();
        assertNotNull(largeOut);

        assertTrue(decompressChannel.writeInbound(largeOut));
        ByteBuf largeDecompressed = decompressChannel.readInbound();
        assertEquals(largePayload, largeDecompressed.toString(StandardCharsets.UTF_8));
        largeDecompressed.release();
    }
}
