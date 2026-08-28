package dev.sweety.netty.service;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: two {@link ServiceClient}s + a {@link HubServer} in one JVM exercise RPC round-trip,
 * fire-and-forget delivery, unknown-receiver drop, and reconnect-then-re-identify.
 */
class ServiceMeshTest {

    private static final int ECHO_ID = 10;
    private static final int SERVICE_A = 1;
    private static final int SERVICE_B = 2;
    private static final String HOST = "127.0.0.1";

    /** A trivial application packet the mesh carries as its opaque payload. */
    public static final class EchoPacket extends Packet {
        private String text = "";
        public EchoPacket() {}
        public EchoPacket(String text) { this.text = text; }
        public String text() { return text; }
        @Override public void write(BufferWriter buffer) { buffer.writeString(text); }
        @Override public void read(BufferReader buffer) { text = buffer.readString(); }
    }

    private static PacketRegistry registry() throws Exception {
        PacketRegistry registry = new OptimizedPacketRegistry();
        ServicePackets.registerInto(registry);
        registry.registerPacket(ECHO_ID, EchoPacket.class);
        return registry;
    }

    /** Echoes {@code "echo:"+text} for RPCs and counts down a latch on every message it receives. */
    private static final class EchoService extends ServiceClient {
        final CountDownLatch received = new CountDownLatch(1);
        EchoService(int port, PacketRegistry registry) { super(HOST, port, SERVICE_B, registry); }
        @Override protected Packet handle(int senderId, Packet request) {
            received.countDown();
            if (request instanceof EchoPacket echo) return new EchoPacket("echo:" + echo.text());
            return null;
        }
    }

    /** Caller side — never receives inbound requests. */
    private static final class CallerService extends ServiceClient {
        CallerService(int port, PacketRegistry registry) { super(HOST, port, SERVICE_A, registry); }
        @Override protected Packet handle(int senderId, Packet request) { return null; }
    }

    @Test
    void rpc_fireAndForget_unknownReceiver_and_reconnect() throws Exception {
        int port = freePort();
        HubServer hub = new HubServer(HOST, port, registry());
        hub.start();

        CallerService a = new CallerService(port, registry());
        EchoService b = new EchoService(port, registry());
        a.start();
        b.start();

        try {
            // both services identify with the hub
            assertTrue(waitUntil(() -> hub.connectedServices().containsAll(java.util.Set.of(SERVICE_A, SERVICE_B)), 5000),
                    "both services should identify");

            // RPC round-trip A → B → A
            Packet reply = a.sendRequest(SERVICE_B, new EchoPacket("hi")).get(3, TimeUnit.SECONDS);
            assertInstanceOf(EchoPacket.class, reply);
            assertEquals("echo:hi", ((EchoPacket) reply).text());

            // fire-and-forget A → B (no reply)
            a.sendFireAndForget(SERVICE_B, new EchoPacket("ping"));
            assertTrue(b.received.await(3, TimeUnit.SECONDS), "B should receive the fire-and-forget");

            // unknown receiver → hub replies with no-route → caller future completes exceptionally
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> a.sendRequest(99, new EchoPacket("void"), 800).get(3, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof java.io.IOException || ex.getCause() instanceof java.util.concurrent.TimeoutException);

            // reconnect: kill the hub, stand a new one on the same port, RPC must recover
            hub.stop();
            HubServer hub2 = new HubServer(HOST, port, registry());
            assertTrue(waitUntil(() -> tryStart(hub2), 8000), "new hub should bind the freed port");
            assertTrue(waitUntil(() -> hub2.connectedServices().containsAll(java.util.Set.of(SERVICE_A, SERVICE_B)), 12000),
                    "services should auto-reconnect and re-identify");
            Packet reply2 = a.sendRequest(SERVICE_B, new EchoPacket("again")).get(3, TimeUnit.SECONDS);
            assertEquals("echo:again", ((EchoPacket) reply2).text());
            hub2.stop();
        } finally {
            a.stop();
            b.stop();
        }
    }

    private static boolean tryStart(HubServer hub) {
        try { hub.start(); return true; } catch (Exception e) { return false; }
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }
}
