package dev.sweety.network.cloud.loadbalancer.backend;

import com.sun.management.OperatingSystemMXBean;
import dev.sweety.network.cloud.impl.PacketRegistry;
import dev.sweety.network.cloud.loadbalancer.packet.MetricsUpdatePacket;
import dev.sweety.network.cloud.messaging.Server;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
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

    public BackendServer(String host, int port, PacketOut... packets) {
        super(host, port, packets);
        this.metricsScheduler.scheduleAtFixedRate(this::sendMetrics, 1, 1, TimeUnit.SECONDS);
    }

    private void sendMetrics() {
        double cpuLoad = getCpuLoad();
        double ramUsage = getRamUsage();

        MetricsUpdatePacket.Out metricsPacket = new MetricsUpdatePacket.Out(cpuLoad, ramUsage);
        sendAll(metricsPacket);
    }

    /**
     * Metodo astratto che le implementazioni di backend devono definire.
     * Riceve il pacchetto dal client e deve restituire un array di pacchetti di risposta.
     * La gestione del correlationId è completamente trasparente.
     *
     * @param ctx    Il contesto del canale.
     * @param packet Il pacchetto in arrivo.
     * @return Un array di PacketOut da inviare come risposta.
     */
    public abstract PacketOut[] handlePackets(ChannelHandlerContext ctx, PacketIn packet);

    @Override
    public final void onPacketReceive(ChannelHandlerContext ctx, PacketIn packet) {
        if (packet.getId() == PacketRegistry.METRICS.id()) return;
        if (!ctx.channel().isActive()) return;

        // 1. Leggi il correlationId, che è sempre all'inizio.
        long correlationId = packet.getBuffer().readLong();

        // 2. Crea un nuovo pacchetto "pulito" con solo i dati originali per la logica di business.
        PacketIn originalClientPacket = new PacketIn(packet.getId(), packet.getTimestamp(), packet.getBuffer().readByteArray());

        // 3. Chiama la logica di business dell'utente.
        PacketOut[] responses = handlePackets(ctx, originalClientPacket);

        if (responses != null && responses.length != 0) {// 4. Invia le risposte wrappate.
            for (PacketOut response : responses) {
                response.getBuffer().wrapData(buffer -> buffer.writeLong(correlationId));

            }

            send(ctx, responses);
        }

        PacketOut closingPacket = new PacketOut(PacketRegistry.CLOSING.id());
        closingPacket.getBuffer().writeLong(correlationId);
        send(ctx, closingPacket);
    }

    private double getCpuLoad() {
        long now = System.nanoTime();
        long currentCpuTime = osBean.getProcessCpuTime();

        long timeDelta = now - lastMeasuredTime;
        long cpuDelta = currentCpuTime - lastCpuTime;

        lastMeasuredTime = now;
        lastCpuTime = currentCpuTime;

        if (timeDelta <= 0) return 0.0;

        // Calcola il load totale (CPU process time / tempo reale * numero core)
        double cpuUsage = (double) cpuDelta / timeDelta;

        // Normalizza in base ai core
        cpuUsage /= osBean.getAvailableProcessors();

        return Math.max(0.0, Math.min(1.0, cpuUsage));
    }


    private double getRamUsage() {
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
        return (double) processUsed / totalPhysical;
    }


    @Override
    public void stop() {
        super.stop();
        metricsScheduler.shutdown();
    }


}
