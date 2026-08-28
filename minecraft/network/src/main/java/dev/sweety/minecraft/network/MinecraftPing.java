package dev.sweety.minecraft.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sweety.minecraft.network.io.PacketInputStream;
import dev.sweety.minecraft.network.io.PacketOutputStream;
import dev.sweety.minecraft.network.packet.C2SPacket;
import dev.sweety.minecraft.network.packet.S2CPacket;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.DataFormatException;

public class MinecraftPing {

    public static final int DEFAULT_PORT        = 25565;
    public static final int DEFAULT_TIMEOUT_MS  = 3000;
    /** MC protocol version for 1.21.4 (770). Servers accept any version for status queries. */
    public static final int DEFAULT_PROTOCOL    = 770;

    // ── Low-level API ─────────────────────────────────────────────────────────

    /**
     * Opens a connection via {@code connectionProvider} and runs each handler in sequence.
     * Handlers share the same {@link PacketOutputStream}/{@link PacketInputStream} pair.
     */
    public static void run(
            ConnectionProvider connectionProvider,
            String host, int port,
            ConnectionHandler... handlers
    ) throws IOException, DataFormatException {
        try (Socket socket = connectionProvider.openSock(host, port);
             PacketOutputStream pos = new PacketOutputStream(socket.getOutputStream(), false);
             PacketInputStream  pis = new PacketInputStream(socket.getInputStream(), true, pos::setCompressionEnabled)
        ) {
            for (ConnectionHandler handler : handlers) handler.execute(pos, pis);
        }
    }

    // ── High-level status API ─────────────────────────────────────────────────

    /** Fetch full server status + measure round-trip latency. Uses default port/timeout/protocol. */
    public static ServerStatus status(String host) throws IOException, DataFormatException {
        return status(host, DEFAULT_PORT, DEFAULT_PROTOCOL, DEFAULT_TIMEOUT_MS);
    }

    public static ServerStatus status(String host, int port) throws IOException, DataFormatException {
        return status(host, port, DEFAULT_PROTOCOL, DEFAULT_TIMEOUT_MS);
    }

    public static ServerStatus status(String host, int port, int protocolVersion, int timeoutMs) throws IOException, DataFormatException {
        final String[] jsonRef    = {null};
        final long[]   latencyRef = {-1};

        run(
            (h, p) -> openSock(h, p, null, s -> s.setSoTimeout(timeoutMs)),
            host, port,
            (pos, pis) -> {
                // Handshake → Status state
                pos.write(new C2SPacket(0x00, w -> w
                        .writeVarInt(protocolVersion)
                        .writeString(host)
                        .writeShort(port)
                        .writeVarInt(1)
                ));
                pos.write(new C2SPacket(0x00)); // Status Request
                jsonRef[0] = pis.read().packetReader().readString();

                // Ping/Pong — measure actual round-trip
                long sent = System.currentTimeMillis();
                pos.write(new C2SPacket(0x01, w -> w.writeLong(sent)));
                pis.read().packetReader().readLong(); // consume echoed timestamp
                latencyRef[0] = System.currentTimeMillis() - sent;
            }
        );

        return ServerStatus.parse(jsonRef[0], (int) latencyRef[0]);
    }

    // ── Ping-only API ─────────────────────────────────────────────────────────

    /** Returns round-trip latency in ms, or -1 on failure. Does not throw. */
    public static int ping(String host) {
        return ping(host, DEFAULT_PORT, DEFAULT_TIMEOUT_MS);
    }

    public static int ping(String host, int port) {
        return ping(host, port, DEFAULT_TIMEOUT_MS);
    }

    public static int ping(String host, int port, int timeoutMs) {
        try {
            return status(host, port, DEFAULT_PROTOCOL, timeoutMs).latencyMs();
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Async variants ─────────────────────────────────────────────────────────

    /** Async status fetch on the common {@link ForkJoinPool}. */
    public static CompletableFuture<ServerStatus> statusAsync(String host) {
        return statusAsync(host, DEFAULT_PORT, DEFAULT_PROTOCOL, DEFAULT_TIMEOUT_MS, ForkJoinPool.commonPool());
    }

    public static CompletableFuture<ServerStatus> statusAsync(String host, int port) {
        return statusAsync(host, port, DEFAULT_PROTOCOL, DEFAULT_TIMEOUT_MS, ForkJoinPool.commonPool());
    }

    public static CompletableFuture<ServerStatus> statusAsync(
            String host, int port, int protocolVersion, int timeoutMs, Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return status(host, port, protocolVersion, timeoutMs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /** Async ping-only. Never throws; returns -1 on failure. */
    public static CompletableFuture<Integer> pingAsync(String host) {
        return CompletableFuture.supplyAsync(() -> ping(host), ForkJoinPool.commonPool());
    }

    public static CompletableFuture<Integer> pingAsync(String host, int port) {
        return CompletableFuture.supplyAsync(() -> ping(host, port), ForkJoinPool.commonPool());
    }

    // ── Socket helpers ────────────────────────────────────────────────────────

    public static Socket openSock(String host, int port) throws IOException {
        return openSock(host, port, null, null);
    }

    public static Socket openSock(String host, int port, @Nullable Proxy proxy, @Nullable ConnectionEditor editor) throws IOException {
        Socket s = proxy != null ? new Socket(proxy) : new Socket();
        s.setSoLinger(false, 0);
        s.setSoTimeout(DEFAULT_TIMEOUT_MS);
        if (editor != null) editor.configure(s); // allows caller to override timeout etc.
        s.connect(new InetSocketAddress(host, port));
        return s;
    }

    // ── ServerStatus ──────────────────────────────────────────────────────────

    /**
     * Parsed result of a Minecraft server status query.
     *
     * @param motd            plain-text MOTD (formatting codes stripped)
     * @param onlinePlayers   current player count
     * @param maxPlayers      server player cap
     * @param version         human-readable version string (e.g. {@code "Paper 1.21.4"})
     * @param protocolVersion numeric MC protocol version reported by the server
     * @param favicon         raw PNG bytes of the server icon, or {@code null} if absent
     * @param latencyMs       round-trip ping measured during this query; -1 if not measured
     */
    public record ServerStatus(
            String  motd,
            int     onlinePlayers,
            int     maxPlayers,
            String  version,
            int     protocolVersion,
            @Nullable byte[] favicon,
            int     latencyMs
    ) {
        public boolean hasFavicon() { return favicon != null; }

        public static ServerStatus parse(String json, int latencyMs) {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            String motd = parseMotd(root.get("description"));

            int online = 0, max = 0;
            JsonElement players = root.get("players");
            if (players != null && players.isJsonObject()) {
                JsonObject p = players.getAsJsonObject();
                online = p.has("online") ? p.get("online").getAsInt() : 0;
                max    = p.has("max")    ? p.get("max").getAsInt()    : 0;
            }

            String versionName = "";
            int protocol = -1;
            JsonElement ver = root.get("version");
            if (ver != null && ver.isJsonObject()) {
                JsonObject v = ver.getAsJsonObject();
                versionName = v.has("name")     ? v.get("name").getAsString()     : "";
                protocol    = v.has("protocol") ? v.get("protocol").getAsInt()    : -1;
            }

            byte[] favicon = null;
            JsonElement fav = root.get("favicon");
            if (fav != null && !fav.isJsonNull()) {
                try {
                    favicon = Base64.getDecoder().decode(
                            fav.getAsString().replace("data:image/png;base64,", "")
                    );
                } catch (RuntimeException e) {
                    // Covers bad Base64 (IllegalArgumentException) and a non-string favicon node
                    // (Gson throws unchecked). A malformed favicon is cosmetic: the status is
                    // still valid, render without an icon rather than failing the whole ping.
                    favicon = null;
                }
            }

            return new ServerStatus(motd, online, max, versionName, protocol, favicon, latencyMs);
        }

        /** Handles both plain-string and TextComponent-object description formats. */
        private static String parseMotd(@Nullable JsonElement desc) {
            if (desc == null || desc.isJsonNull()) return "";
            if (desc.isJsonPrimitive()) return desc.getAsString();
            if (desc.isJsonObject()) {
                JsonObject obj = desc.getAsJsonObject();
                if (obj.has("text")) return obj.get("text").getAsString();
            }
            return desc.toString();
        }
    }

    // ── Interfaces ─────────────────────────────────────────────────────────────

    public interface ConnectionProvider {
        Socket openSock(String address, int port) throws IOException;
    }


    public interface ConnectionEditor {
        void configure(Socket socket) throws IOException;
    }

    public interface ConnectionHandler {
        void execute(PacketOutputStream pos, PacketInputStream pis) throws IOException, DataFormatException;
    }


}
