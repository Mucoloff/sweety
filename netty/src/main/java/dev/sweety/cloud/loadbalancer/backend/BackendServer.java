package dev.sweety.cloud.loadbalancer.backend;

import com.sun.management.OperatingSystemMXBean;
import dev.sweety.cloud.loadbalancer.packets.ForwardPacket;
import dev.sweety.cloud.loadbalancer.packets.MetricsUpdatePacket;
import dev.sweety.cloud.loadbalancer.packets.WrappedPacket;
import dev.sweety.cloud.messaging.Server;
import dev.sweety.cloud.packet.model.Packet;
import dev.sweety.cloud.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class BackendServer extends Server {

    private final ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor();
    private final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private long lastCpuTime = 0;
    private long lastMeasuredTime = System.nanoTime();

    public BackendServer(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry, packets);
        this.metricsScheduler.scheduleAtFixedRate(this::sendMetrics, 1, 1, TimeUnit.SECONDS);
    }

    private void sendMetrics() {
        float cpuLoad = getCpuLoad();
        float ramUsage = getRamUsage();

        MetricsUpdatePacket metricsPacket = new MetricsUpdatePacket(cpuLoad, ramUsage);
        broadcastPacket(metricsPacket);
    }

    /**
     * Metodo astratto che le implementazioni di backend devono definire.
     * Riceve il pacchetto dal client e deve restituire un array di pacchetti di risposta.
     * La gestione del correlationId è completamente trasparente.
     *
     * @param ctx    Il contesto del canale.
     * @param packet Il pacchetto in arrivo.
     * @return Un array di Packet da inviare come risposta.
     */
    public abstract Packet[] handlePackets(ChannelHandlerContext ctx, Packet packet);

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof MetricsUpdatePacket) return;
        if (!ctx.channel().isActive()) return;

        if (!(packet instanceof ForwardPacket forward)) return;
        forward.buffer().resetReaderIndex();

        // 1. Leggi il correlationId, che è sempre all'inizio.
        long correlationId = forward.getCorrelationId();

        Packet original;
        try {
            original = getPacketRegistry().constructPacket(forward.getOriginalId(), forward.getOriginalTimestamp(), forward.getOriginalData());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }


        // 3. Chiama la logica di business dell'utente.
        Packet[] responses = handlePackets(ctx, original);

        if (responses != null && responses.length != 0) {// 4. Invia le risposte wrappate.
            for (int i = 0; i < responses.length; i++) {
                responses[i] = new WrappedPacket(correlationId, i == responses.length - 1, getPacketRegistry().getPacketId(responses[i].getClass()), responses[i]);


            }

            sendPacket(ctx, responses);
        }

    }

    private float getCpuLoad() {
        long now = System.nanoTime();
        long currentCpuTime = osBean.getProcessCpuTime();

        long timeDelta = now - lastMeasuredTime;
        long cpuDelta = currentCpuTime - lastCpuTime;

        lastMeasuredTime = now;
        lastCpuTime = currentCpuTime;

        if (timeDelta <= 0) return 0.0f;

        // Calcola il load totale (CPU process time / tempo reale * numero core)
        float cpuUsage = (float) cpuDelta / timeDelta;

        // Normalizza in base ai core
        cpuUsage /= osBean.getAvailableProcessors();

        return (float) Math.max(0.0, Math.min(1.0, cpuUsage));
    }


    private float getRamUsage() {
        long totalPhysical = osBean.getTotalMemorySize();

        // Heap (Java)
        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();

        // Metaspace
        long metaspaceUsed = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getName().contains("Metaspace"))
                .mapToLong(pool -> pool.getUsage().getUsed())
                .sum();


        long processUsed = heapUsed + metaspaceUsed;
        return (float) processUsed / totalPhysical;
    }


    @Override
    public void stop() {
        super.stop();
        metricsScheduler.shutdown();
    }


}
