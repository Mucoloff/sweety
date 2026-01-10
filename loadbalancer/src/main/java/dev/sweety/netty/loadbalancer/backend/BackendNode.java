package dev.sweety.netty.loadbalancer.backend;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.loadbalancer.packets.ForwardPacket;
import dev.sweety.netty.loadbalancer.packets.MetricsUpdatePacket;
import dev.sweety.netty.loadbalancer.packets.WrappedPacket;
import dev.sweety.netty.loadbalancer.LoadBalancerServer;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rappresenta un nodo di backend.
 * Gestisce il carico delle richieste in corso e inoltra le risposte al Load Balancer.
 */
@Getter
public class BackendNode extends Client {

    private final SimpleLogger logger;
    @Setter
    private LoadBalancerServer loadBalancer;

    private final AtomicLong networkLoad = new AtomicLong(0);
    private final Map<Long, Integer> pendingRequestLoads = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------
    // Latency tracking
    // -----------------------------------------------------------------
    // Mappa correlationId -> timestamp di invio (in nano)
    private final Map<Long, Long> pendingRequestTimestamps = new ConcurrentHashMap<>();
    private final AtomicLong totalLatencyNs = new AtomicLong(0);
    private final AtomicLong completedRequests = new AtomicLong(0);

    // EMA (exponential moving average) in nanosecondi per latenza
    // usiamo volatile + sincronizzazione per aggiornamento numerico semplice
    private volatile float emaLatencyNs = 0.0f;
    private static final float EMA_ALPHA = 0.15f; // smoothing factor

    // -----------------------------------------------------------------

    // Pesi per il calcolo del carico combinato
    private static final float W_NET = 0.4f; // Peso del carico di rete
    private static final float W_CPU = 0.1f; // Peso del carico CPU
    private static final float W_RAM = 0.5f; // Peso del carico RAM

    // Valori per la normalizzazione
    private static final long MAX_NETWORK_LOAD = 1 * 1024 * 1024; // 1 MB

    private volatile float cpuLoad = 0.0f;
    private volatile float ramUsage = 0.0f;

    public BackendNode(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.logger = new SimpleLogger("node - " + port).fallback();
    }

    /**
     * Chiamato quando il backend invia una risposta al load balancer.
     * Usiamo questo evento per decrementare il contatore di carico.
     */
    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {

        if (packet instanceof MetricsUpdatePacket metrics) {
            this.cpuLoad = metrics.getCpuLoad();
            this.ramUsage = metrics.getRamUsage();
            return;
        }

        if (loadBalancer == null) return;

        if (!(packet instanceof WrappedPacket wrapped)){
            logger.warn("Ricevuto un pacchetto non wrappato: " + packet);
            return;
        }

        long correlationId = wrapped.getCorrelationId();
        boolean isClosing = wrapped.isClosing();

        if (isClosing) requestCompleted(correlationId);

        Packet original;
        try {
            original = getPacketRegistry().constructPacket(wrapped.getOriginalId(), wrapped.getOriginalTimestamp(), wrapped.getOriginalData()).rewind();
        } catch (Exception e) {
            return;
        }

        loadBalancer.forwardResponseToClient(correlationId, original, isClosing);
    }

    /**
     * Inoltra un pacchetto a questo backend, anteponendo l'ID di correlazione.
     */
    public void forwardPacket(Packet packet, long correlationId) {
        if (!this.running()) {
            logger.info("backend non attivo, ignorando");
            return;
        }

        int packetSize = packet.buffer().readableBytes();
        pendingRequestLoads.put(correlationId, packetSize);
        networkLoad.addAndGet(packetSize);
        // registra il timestamp di invio (nanoTime per misure di latenza)
        pendingRequestTimestamps.put(correlationId, System.nanoTime());

        packet.rewind();

        ForwardPacket forward = new ForwardPacket(correlationId, packet);

        logger.info("forwarding packet", forward, Long.toHexString(correlationId));
        sendPacket(forward);
    }

    /**
     * Decrementa il contatore di carico.
     */
    public void requestCompleted(long correlationId) {
        Integer requestLoad = pendingRequestLoads.remove(correlationId);
        if (requestLoad != null) {
            networkLoad.addAndGet(-requestLoad);
        } else {
            logger.warn("Completata una richiesta con correlationId #" + Long.toHexString(correlationId) + " non tracciato.");
        }

        // calcola latenza se avevamo registrato il timestamp d'invio
        Long startNs = pendingRequestTimestamps.remove(correlationId);
        if (startNs != null) {
            long elapsedNs = Math.max(0L, System.nanoTime() - startNs);
            totalLatencyNs.addAndGet(elapsedNs);
            completedRequests.incrementAndGet();
            updateEmaLatency(elapsedNs);
        } else {
            // se non abbiamo lo start, non possiamo misurare latenza ma non è critico
            logger.debug("No timestamp per correlationId #" + Long.toHexString(correlationId) + ", impossibile misurare latenza.");
        }
    }

    /**
     * Aggiorna la EMA della latenza (in nanosecondi). Sincronizzato perché scriviamo su float volatile.
     */
    private synchronized void updateEmaLatency(long elapsedNs) {
        if (emaLatencyNs == 0.0) {
            emaLatencyNs = (float) elapsedNs;
        } else {
            emaLatencyNs = EMA_ALPHA * (float) elapsedNs + (1.0f - EMA_ALPHA) * emaLatencyNs;
        }
    }

    /**
     * Verifica se il backend è attivo usando il metodo ereditato da Messenger.
     */
    public boolean isActive() {
        return this.channel != null && this.channel.isActive() && this.running();
    }

    /**
     * Calcola un punteggio di carico combinato basato su rete, CPU e RAM.
     *
     * @return Un valore float che rappresenta il carico totale. Più alto è, più il nodo è occupato.
     */
    public float getCombinedLoad() {
        // Normalizza il carico di rete in un range [0, 1]
        float normalizedNetwork = (float) Math.min(1.0, (float) networkLoad.get() / MAX_NETWORK_LOAD);

        /*
        logger.info("Carichi - Rete: " + String.format("%.2f", normalizedNetwork) +
                ", CPU: " + String.format("%.2f", cpuLoad) +
                ", RAM: " + String.format("%.2f", ramUsage));

         */

        // Calcola il punteggio pesato
        return (normalizedNetwork * W_NET) + (cpuLoad * W_CPU) + (ramUsage * W_RAM);
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Errore sulla connessione al backend " + host + ":" + port, throwable);
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.info("Load Balancer connesso al backend: " + host + ":" + port);
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.warn("Backend disconnesso: " + host + ":" + port);
        this.cpuLoad = 0.0f;
        this.ramUsage = 0.0f;
        this.networkLoad.set(0);
        this.pendingRequestLoads.clear();
        this.pendingRequestTimestamps.clear();
    }

    /**
     * Restituisce la latenza media osservata in millisecondi.
     * Utilizza la media aritmetica (total/cnt) se disponibile, altrimenti la EMA.
     */
    public float getAverageLatency() {
        long completed = completedRequests.get();
        if (completed > 0) {
            float avgNs = (float) totalLatencyNs.get() / (float) completed;
            return (avgNs / 1_000_000.0f); // ms
        }
        // fallback sulla EMA se non abbiamo completate sufficienti misure
        return (emaLatencyNs > 0.0f ? emaLatencyNs / 1_000_000.0f : 0.0f);
    }
}
