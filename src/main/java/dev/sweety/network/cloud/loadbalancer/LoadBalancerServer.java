package dev.sweety.network.cloud.loadbalancer;

import dev.sweety.core.crypt.ChecksumUtils;
import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.core.math.vector.queue.LinkedQueue;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.network.cloud.loadbalancer.backend.pool.BackendPool;
import dev.sweety.network.cloud.loadbalancer.backend.pool.IBackendPool;
import dev.sweety.network.cloud.impl.loadbalancer.PacketQueue;
import dev.sweety.network.cloud.messaging.Server;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * Il server del Load Balancer. Estende 'Server' per gestire le connessioni dei client.
 * La sua unica responsabilità è inoltrare i pacchetti al BackendPool.
 */
public class LoadBalancerServer extends Server {

    private final IBackendPool backendPool;
    private final EcstacyLogger logger = new EcstacyLogger(LoadBalancerServer.class).fallback();

    private final LinkedQueue<PacketQueue> pendingPackets = new LinkedQueue<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<Long, ChannelHandlerContext> clientRequestContexts = new ConcurrentHashMap<>();

    public LoadBalancerServer(int port, String host, BackendPool backendPool, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        (this.backendPool = backendPool).pool().forEach(node -> node.setLoadBalancer(this));
        // Avvia le connessioni verso i backend
        this.backendPool.initialize();

        scheduler.scheduleAtFixedRate(this::drainPending, 50, 50, TimeUnit.MILLISECONDS);
    }

    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        BackendNode backend = backendPool.nextBackend(packet, ctx);

        logger.info("received packet", packet);
        if (packet instanceof TextPacket text){
            logger.info("content: " + text.getText());
        }

        if (backend == null) {
            pendingPackets.enqueue(new PacketQueue(packet, ctx));
            logger.warn("Nessun backend disponibile. Pacchetto aggiunto in coda: pending packets = " + pendingPackets.size());
            return;
        }

        long correlationId = generateCorrelation();
        clientRequestContexts.put(correlationId, ctx);

        // Schedula la rimozione per timeout
        scheduler.schedule(() -> {
            ChannelHandlerContext timedOutCtx = clientRequestContexts.remove(correlationId);
            if (timedOutCtx != null) {
                logger.warn("Timeout richiesta per #" + Long.toHexString(correlationId) + ". Contesto rimosso.");
            }
        }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        backend.forwardPacket(packet, correlationId);
    }

    /**
     * Inoltra una risposta dal backend al client originale.
     *
     * @param correlationId L'ID per trovare il client.
     * @param packet        Il pacchetto di risposta.
     * @param isClosing     Se true, il contesto del client viene rimosso.// Inoltra i dati del wrapper al LoadBalancer
     */
    public void forwardResponseToClient(long correlationId, Packet packet, boolean isClosing) {
        // Se è un pacchetto di chiusura, rimuoviamo il contesto. Altrimenti, lo cerchiamo soltanto.
        ChannelHandlerContext clientCtx = isClosing
                ? clientRequestContexts.remove(correlationId)
                : clientRequestContexts.get(correlationId);

        if (clientCtx == null || !clientCtx.channel().isActive()) {
            logger.warn("Contesto client non trovato per #" + Long.toHexString(correlationId));
            return;
        }


        logger.info("Inoltro pacchetto (" + packet + ") response #" + Long.toHexString(correlationId) + " al client");

        clientCtx.channel().writeAndFlush(packet);

    }

    private void drainPending() {
        if (pendingPackets.isEmpty()) return;

        int queueSize = pendingPackets.size();
        for (int i = 0; i < queueSize; i++) {
            PacketQueue pq = pendingPackets.dequeue();
            if (pq == null) break;
            final Packet packet = pq.packet();

            BackendNode backend = backendPool.nextBackend(pq.packet(), pq.ctx());
            if (backend == null) {
                pendingPackets.enqueue(pq);
                break;
            }

            long correlationId = generateCorrelation();
            clientRequestContexts.put(correlationId, pq.ctx());
            System.out.println();
            logger.info("Inoltro pacchetto (" + packet.getId() + ") pending #" + Long.toHexString(correlationId) + " al backend " + backend.getPort());
            backend.forwardPacket(packet, correlationId);
        }
    }

    private long generateCorrelation() {
        CRC32 crc = ChecksumUtils.crc32(true);
        byte[] randomBytes = new byte[RandomUtils.range(8, 16)];
        RandomUtils.RANDOM.nextBytes(randomBytes);
        crc.update(randomBytes);
        return crc.getValue();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Errore su connessione client: ", throwable);
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.info("Client connesso al Load Balancer: ", ctx.channel().remoteAddress());
        super.addClient(ctx, ctx.channel().remoteAddress());
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.warn("Client disconnesso dal Load Balancer: ", ctx.channel().remoteAddress());
        super.removeClient(ctx.channel().remoteAddress());
    }

}
