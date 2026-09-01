package dev.sweety.netty.messaging.demo;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.impl.DualServer;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.messaging.transport.NativeTransport;
import dev.sweety.netty.messaging.transport.TcpTransport;
import dev.sweety.netty.messaging.transport.UdpTransport;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;

public class DualProtocolDemo {

    // 1. Packet TCP: Handshake di autenticazione e sessione
    public static class AuthPacket extends Packet {
        private String username;
        private String token;

        public AuthPacket() {}
        public AuthPacket(String username, String token) {
            this.username = username;
            this.token = token;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeString(username);
            buffer.writeString(token);
        }

        @Override
        public void read(BufferReader buffer) {
            this.username = buffer.readString();
            this.token = buffer.readString();
        }
    }

    // 2. Packet UDP: Telemetria ad alta frequenza (Posizione + Sequenza)
    public static class TelemetryPacket extends Packet {
        private long connectionId;
        private long seq;
        private double x, y, z;

        public TelemetryPacket() {}
        public TelemetryPacket(long connectionId, long seq, double x, double y, double z) {
            this.connectionId = connectionId;
            this.seq = seq;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeVarLong(connectionId);
            buffer.writeVarLong(seq);
            buffer.writeDouble(x);
            buffer.writeDouble(y);
            buffer.writeDouble(z);
        }

        @Override
        public void read(BufferReader buffer) {
            this.connectionId = buffer.readVarLong();
            this.seq = buffer.readVarLong();
            this.x = buffer.readDouble();
            this.y = buffer.readDouble();
            this.z = buffer.readDouble();
        }
    }

    @org.junit.jupiter.api.Test
    public void runDemoTest() throws Exception {
        main(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("SWEETY DUAL-PROTOCOL TEST (TCP + UDP SAME PORT)");
        System.out.println("Active OS Transport: " + NativeTransport.type().description());
        System.out.println("==================================================");

        PacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(0, AuthPacket.class);
        registry.registerPacket(1, TelemetryPacket.class);

        int port = 9999;
        String host = "127.0.0.1";

        // 1. Creiamo e avviamo il DualServer (bind sia TCP che UDP su :9999)
        DualServer server = new DualServer(host, port, registry) {
            @Override
            public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
                if (packet instanceof AuthPacket auth) {
                    // Flusso TCP (Reliable)
                    Long connId = Server.connectionIdOf(ctx);
                    System.out.printf("📥 [TCP :%d] Ricevuta Auth per '%s' (Token: %s) -> Assegnato ConnectionId=%d%n",
                            port, auth.username, auth.token, connId);
                } else if (packet instanceof AddressedPacket addressed) {
                    // Flusso UDP (Fast Telemetry)
                    if (addressed.packet() instanceof TelemetryPacket telemetry) {
                        boolean accepted = endpointRegistry().acceptSeq(telemetry.connectionId, telemetry.seq);
                        if (accepted) {
                            System.out.printf("⚡ [UDP :%d] Telemetria Valida da %s: seq=%d, pos=(%.1f, %.1f, %.1f)%n",
                                    port, addressed.sender(), telemetry.seq, telemetry.x, telemetry.y, telemetry.z);
                        } else {
                            System.out.printf("⚠️ [UDP :%d] PACCHETTO SCARTATO (Replay o Duplicato): seq=%d da %s%n",
                                    port, telemetry.seq, addressed.sender());
                        }
                    }
                }
            }
        };

        server.start();
        Thread.sleep(100);

        // 2. Avviamo il client TCP per fare il login
        SimpleClient tcpClient = new SimpleClient(TcpTransport.INSTANCE, host, port, registry, -1) {
            @Override
            public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {}
        };
        tcpClient.start();
        System.out.println("🟢 [Client] Connesso al server via TCP su " + host + ":" + port);

        // Invio credenziali TCP
        tcpClient.sendPacket(new AuthPacket("Francesco", "secret_token_12345")).get();

        // 3. Avviamo il client UDP per inviare telemetria alla STESSA porta
        SimpleClient udpClient = new SimpleClient(UdpTransport.unconnected(), host, port, registry, -1) {
            @Override
            public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {}
        };
        udpClient.start();
        System.out.println("🟢 [Client] Socket UDP pronto, invio telemetria sulla STESSA porta " + port);

        InetSocketAddress serverEndpoint = new InetSocketAddress(host, port);
        long myConnectionId = 0L;

        // Simuliamo l'invio di frame di movimento UDP (anche con jitter/out-of-order e duplicati!)
        System.out.println("\n📡 --- Invio sequenza di pacchetti UDP ---");
        
        // Frame 1: In ordine
        udpClient.sendPacket(new AddressedPacket(new TelemetryPacket(myConnectionId, 1, 100.0, 64.0, 200.0), serverEndpoint));
        
        // Frame 3: Arriva PRIMA del frame 2 (simulazione network jitter)
        udpClient.sendPacket(new AddressedPacket(new TelemetryPacket(myConnectionId, 3, 102.0, 64.0, 202.0), serverEndpoint));
        
        // Frame 2: Arriva IN RITARDO rispetto al frame 3 (tollerato dalla sliding window!)
        udpClient.sendPacket(new AddressedPacket(new TelemetryPacket(myConnectionId, 2, 101.0, 64.0, 201.0), serverEndpoint));
        
        // Frame 2 DUPLICATO (replay attack o rete instabile): deve essere SCARTATO
        udpClient.sendPacket(new AddressedPacket(new TelemetryPacket(myConnectionId, 2, 101.0, 64.0, 201.0), serverEndpoint));

        // Frame 4: Regolare
        udpClient.sendPacket(new AddressedPacket(new TelemetryPacket(myConnectionId, 4, 103.0, 64.0, 203.0), serverEndpoint));

        Thread.sleep(500);

        System.out.println("\n🏁 --- Chiusura Connessioni ---");
        tcpClient.stop();
        udpClient.stop();
        server.stop();
        System.out.println("✅ Test Completato con Successo!\n");
    }
}
