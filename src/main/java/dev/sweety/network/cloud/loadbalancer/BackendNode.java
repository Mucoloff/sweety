package dev.sweety.network.cloud.loadbalancer;

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.messaging.Client;
import dev.sweety.network.cloud.packet.buffer.PacketBuffer;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rappresenta un nodo di backend.
 * Estende 'Client' per riutilizzare la logica di connessione e comunicazione di Messenger.
 */
@Getter
public class BackendNode extends Client {

    private final AtomicInteger load = new AtomicInteger(0);
    private final Map<Long, Integer> pendingRequestLoads = new ConcurrentHashMap<>();

    private final EcstacyLogger logger;

    @Setter
    private LoadBalancerServer loadBalancer; // Riferimento al server principale

    public BackendNode(String host, int port) {
        super(host, port);
        this.logger = new EcstacyLogger("node - " + port).fallback();
    }

    public int getPort() {
        return super.port;
    }

    /**
     * Chiamato quando il backend invia una risposta al load balancer.
     * Usiamo questo evento per decrementare il contatore di carico.
     */
    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, PacketIn packet) {
        if (loadBalancer == null) return;

        // Controlla se il pacchetto è un nostro wrapper interno
        long correlationId = packet.getBuffer().readLong();

        boolean isClosing = packet.getId() == BackendServer.CLOSING_ID;

        if (isClosing) requestCompleted(correlationId);

        loadBalancer.forwardResponseToClient(correlationId, packet.getId(), packet.getData(), isClosing);
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Errore sulla connessione al backend " + host + ":" + port, throwable.getMessage());
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.info("Load Balancer connesso al backend: " + host + ":" + port);
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.warn("Backend disconnesso: " + host + ":" + port);
    }

    /**
     * Inoltra un pacchetto a questo backend, anteponendo l'ID di correlazione.
     */
    public void forwardPacket(PacketIn packet, long correlationId) {
        if (!this.running()) return;

        int packetSize = packet.getBuffer().readableBytes();
        pendingRequestLoads.put(correlationId, packetSize);
        load.addAndGet(packetSize);

        PacketOut forwardedPacket = new PacketOut(packet.getId(), packet.getTimestamp());
        PacketBuffer buffer = forwardedPacket.getBuffer();
        buffer.writeLong(correlationId);
        buffer.writeBytesArray(packet.getData());
        sendPacket(forwardedPacket);
    }

    /**
     * Decrementa il contatore di carico.
     */
    public void requestCompleted(long correlationId) {
        Integer requestLoad = pendingRequestLoads.remove(correlationId);
        if (requestLoad != null) {
            load.addAndGet(-requestLoad);
        } else {
            logger.warn("Completata una richiesta con correlationId #" + Long.toHexString(correlationId) + " non tracciato.");
        }
    }

    /**
     * Verifica se il backend è attivo usando il metodo ereditato da Messenger.
     */
    public boolean isActive() {
        return this.channel != null && this.channel.isActive() && this.running();
    }

    public int getLoadValue() {
        return load.get();
    }
}
