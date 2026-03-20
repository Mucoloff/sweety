package dev.sweety.minecraft.nework;

import dev.sweety.minecraft.nework.io.PacketInputStream;
import dev.sweety.minecraft.nework.io.PacketOutputStream;
import dev.sweety.minecraft.nework.packet.C2SPacket;
import dev.sweety.minecraft.nework.packet.S2CPacket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MinecraftPing {

    public static void main(String[] args) throws Throwable {
        String address = "play.hypixel.net";
        int port = 25565;

        try (Socket socket = openSock(address, port)) {
            PacketOutputStream pos = new PacketOutputStream(socket.getOutputStream(), false);
            PacketInputStream pis = new PacketInputStream(socket.getInputStream(), true, pos::setCompressionEnabled);

            int protocolVersion = 773;//MinecraftVersion.V_1_21_10.getProtocolVersion();

            // Handshake
            pos.write(new C2SPacket(0x00, w -> w
                    .writeVarInt(protocolVersion)
                    .writeString(address)
                    .writeShort(port)
                    .writeVarInt(1) // next state = Status
            ));

            // Status request
            pos.write(new C2SPacket(0x00));

            // Leggi Status Response
            S2CPacket status = pis.read();
            String json = status.getPacketReader().readString();
            System.out.println("Status JSON:\n" + json);

            // Ping
            long timestamp = System.currentTimeMillis();
            pos.write(new C2SPacket(0x01, w -> w.writeLong(timestamp)));

            // Leggi Pong
            S2CPacket pong = pis.read();
            long returned = pong.getPacketReader().readLong();
            System.out.println("Ping: " + (System.currentTimeMillis() - returned) + "ms");

            pis.close();
            pos.close();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    static Socket openSock(String a, int b) throws IOException {
        Socket s = new Socket();
        s.setSoLinger(false, 0);
        s.setSoTimeout(3000);
        s.connect(new InetSocketAddress(a, b));
        return s;
    }
}
