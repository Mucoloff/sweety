package dev.sweety.minecraft.nework;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.sweety.minecraft.nework.io.PacketInputStream;
import dev.sweety.minecraft.nework.io.PacketOutputStream;
import dev.sweety.minecraft.nework.packet.C2SPacket;
import dev.sweety.minecraft.nework.packet.S2CPacket;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.Consumer;

public class MinecraftPing {

    public static void main(String[] args) throws Throwable {
        String address = "play.coralmc.it";
        int port = 25565;
        int protocolVersion = 773;

        try (Socket socket = openSock(address, port);
             PacketOutputStream pos = new PacketOutputStream(socket.getOutputStream(), false);
             PacketInputStream pis = new PacketInputStream(socket.getInputStream(), true, pos::setCompressionEnabled)
        ) {

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
            String json = status.packetReader().readString();

            Gson gson = new Gson().newBuilder().setPrettyPrinting().disableHtmlEscaping().create();

            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

            System.out.println("Status JSON:\n" + gson.toJson(jsonObject));

            // Ping
            long timestamp = System.currentTimeMillis();
            pos.write(new C2SPacket(0x01, w -> w.writeLong(timestamp)));

            // Leggi Pong
            S2CPacket pong = pis.read();
            long returned = pong.packetReader().readLong();
            System.out.println("Ping: " + (System.currentTimeMillis() - returned) + "ms");

            String favicon = jsonObject.get("favicon").getAsString().replace("data:image/png;base64,", "");

            Files.write(Path.of("favicon.png"), Base64.getDecoder().decode(favicon));

        }
    }

    static Socket openSock(String host, int port) throws IOException {
        return openSock(host, port, null, null);
    }

    static Socket openSock(String host, int port, @Nullable Proxy proxy, @Nullable Consumer<Socket> edit) throws IOException {
        final Socket s = proxy != null ? new Socket(proxy) : new Socket();
        s.setSoLinger(false, 0);
        s.setSoTimeout(3000);
        if (edit != null) edit.accept(s);
        s.connect(new InetSocketAddress(host, port));
        return s;
    }
}
