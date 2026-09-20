package dev.sweety.netty.messaging.handshake;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.exception.PacketDecodeException;
import dev.sweety.netty.messaging.listener.decoder.NettyDecoder;
import dev.sweety.netty.messaging.listener.decoder.PacketDecoder;
import dev.sweety.netty.messaging.listener.encoder.NettyEncoder;
import dev.sweety.netty.messaging.listener.encoder.PacketEncoder;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionHandshakeTest {

    public static class HandshakeTestPacket extends Packet {
        private String payload;

        public HandshakeTestPacket() {}

        public HandshakeTestPacket(String payload) {
            this.payload = payload;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeString(payload != null ? payload : "");
        }

        @Override
        public void read(BufferReader buffer) {
            this.payload = buffer.readString();
        }

        public String payload() {
            return payload;
        }
    }

    @Test
    public void testEcdhKeyExchangeAndSharedSecret() {
        KeyPair serverPair = SessionHandshakeEngine.generateX25519KeyPair();
        KeyPair clientPair = SessionHandshakeEngine.generateX25519KeyPair();

        byte[] rawServerPub = SessionHandshakeEngine.extractRawPublicKey(serverPair.getPublic());
        byte[] rawClientPub = SessionHandshakeEngine.extractRawPublicKey(clientPair.getPublic());

        assertEquals(32, rawServerPub.length);
        assertEquals(32, rawClientPub.length);

        PublicKey decodedServerPub = SessionHandshakeEngine.decodeRawPublicKey(rawServerPub);
        PublicKey decodedClientPub = SessionHandshakeEngine.decodeRawPublicKey(rawClientPub);

        byte[] serverSecret = SessionHandshakeEngine.computeSharedSecret(serverPair.getPrivate(), decodedClientPub);
        byte[] clientSecret = SessionHandshakeEngine.computeSharedSecret(clientPair.getPrivate(), decodedServerPub);

        assertNotNull(serverSecret);
        assertNotNull(clientSecret);
        assertEquals(32, serverSecret.length);
        assertEquals(32, clientSecret.length);
        assertArrayEquals(serverSecret, clientSecret);
    }

    @Test
    public void testPowChallengeSolveAndVerify() {
        int difficultyBits = 8; // 8 leading bits for fast execution
        SessionHandshakeEngine.PoWChallenge challenge = SessionHandshakeEngine.createChallenge(difficultyBits);

        long nonce = SessionHandshakeEngine.solveChallenge(challenge);
        assertTrue(nonce >= 0, "Solver should find a valid nonce");

        boolean verified = SessionHandshakeEngine.verifyChallenge(challenge, nonce, 5000);
        assertTrue(verified, "Server should verify correct nonce within expiry window");

        // Expired challenge should fail
        SessionHandshakeEngine.PoWChallenge expiredChallenge = new SessionHandshakeEngine.PoWChallenge(
                challenge.serverSalt(), challenge.difficultyBits(), System.currentTimeMillis() - 10000
        );
        boolean expired = SessionHandshakeEngine.verifyChallenge(expiredChallenge, nonce, 5000);
        assertFalse(expired, "Expired challenge must fail verification");

        // Tampered nonce should fail (with inverted bits)
        assertFalse(SessionHandshakeEngine.verifyChallenge(challenge, ~nonce, 5000));
    }

    @Test
    public void testDynamicSessionSeedDerivation() {
        KeyPair serverPair = SessionHandshakeEngine.generateX25519KeyPair();
        KeyPair clientPair = SessionHandshakeEngine.generateX25519KeyPair();

        byte[] serverSecret = SessionHandshakeEngine.computeSharedSecret(serverPair.getPrivate(), clientPair.getPublic());
        byte[] clientSecret = SessionHandshakeEngine.computeSharedSecret(clientPair.getPrivate(), serverPair.getPublic());

        long powNonce = 123456789L;
        long serverSalt = 987654321L;

        int serverSeed = SessionHandshakeEngine.deriveSessionSeed(serverSecret, powNonce, serverSalt);
        int clientSeed = SessionHandshakeEngine.deriveSessionSeed(clientSecret, powNonce, serverSalt);

        assertEquals(serverSeed, clientSeed);
        assertNotEquals(0, serverSeed);
        assertNotEquals(SessionHandshakeEngine.PROTOCOL_BOOTSTRAP_SEED, serverSeed);
    }

    @Test
    public void testPacketCodecWithMatchingDynamicSeed() throws Exception {
        PacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(1, HandshakeTestPacket.class);

        int dynamicSeed = 0x7A1C8E2D;

        PacketEncoder encoder = new PacketEncoder(registry);
        encoder.setSessionSeed(dynamicSeed);

        PacketDecoder decoder = new PacketDecoder(registry);
        decoder.setSessionSeed(dynamicSeed);

        HandshakeTestPacket packet = new HandshakeTestPacket("sweety-dynamic-seed-ok");
        PacketBuffer buffer = PacketBufferAllocator.DEFAULT.buffer();

        encoder.encode(packet, buffer);

        List<Packet> out = new ArrayList<>();
        decoder.decode(buffer, out);

        assertEquals(1, out.size());
        assertInstanceOf(HandshakeTestPacket.class, out.get(0));
        assertEquals("sweety-dynamic-seed-ok", ((HandshakeTestPacket) out.get(0)).payload());

        buffer.release();
    }

    @Test
    public void testPacketCodecRejectionWithMismatchedSeed() throws Exception {
        PacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(1, HandshakeTestPacket.class);

        int seedA = 0x11223344;
        int seedB = 0x55667788;

        PacketEncoder encoder = new PacketEncoder(registry);
        encoder.setSessionSeed(seedA);

        PacketDecoder decoder = new PacketDecoder(registry);
        decoder.setSessionSeed(seedB);

        HandshakeTestPacket packet = new HandshakeTestPacket("tampered-seed-test");
        PacketBuffer buffer = PacketBufferAllocator.DEFAULT.buffer();

        encoder.encode(packet, buffer);

        List<Packet> out = new ArrayList<>();
        assertThrows(PacketDecodeException.class, () -> {
            decoder.decode(buffer, out);
        });

        buffer.release();
    }

    @Test
    public void testChannelAttributeSyncInNettyCodec() throws Exception {
        PacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(1, HandshakeTestPacket.class);

        int dynamicSeed = 0x4E5F6A7B;

        EmbeddedChannel channel = new EmbeddedChannel(
                new NettyDecoder(registry),
                new NettyEncoder(registry)
        );

        // Bind dynamic seed via channel attribute
        channel.attr(Messenger.SESSION_SEED).set(dynamicSeed);

        HandshakeTestPacket packet = new HandshakeTestPacket("embedded-channel-test");

        // Write outbound: NettyEncoder reads channel attribute and encodes with dynamicSeed
        assertTrue(channel.writeOutbound(packet));
        ByteBuf encodedBuf = channel.readOutbound();
        assertNotNull(encodedBuf);

        // Write inbound: NettyDecoder reads channel attribute and decodes with dynamicSeed
        assertTrue(channel.writeInbound(encodedBuf));
        HandshakeTestPacket decodedPacket = channel.readInbound();
        assertNotNull(decodedPacket);
        assertEquals("embedded-channel-test", decodedPacket.payload());

        channel.finishAndReleaseAll();
    }
}
