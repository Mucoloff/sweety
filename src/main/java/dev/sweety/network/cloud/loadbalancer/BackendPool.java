package dev.sweety.network.cloud.loadbalancer;

import dev.sweety.core.logger.EcstacyLogger;
import org.slf4j.event.Level;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BackendPool {

    private final List<BackendNode> backends;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public BackendPool(List<BackendNode> backends) {
        this.backends = backends;
    }

    /**
     * Inizializza il pool, connettendo ogni backend.
     */
    public void initialize() {
        if (backends.isEmpty()) {
            EcstacyLogger.log(Level.WARN, "backend-pool", "Nessun backend configurato.");
            return;
        }
        // Ogni nodo è un Client, quindi ha il suo metodo connect()
        backends.forEach(BackendNode::connect);
    }

    /**
     * Seleziona il prossimo backend disponibile.
     *
     * @return Il BackendNode selezionato, o null se nessuno è disponibile.
     */
    public BackendNode nextBackend() {
        if (backends.isEmpty()) return null;

        return backends.stream()
                .filter(BackendNode::isActive)
                .min(Comparator.comparingLong(BackendNode::getLoadValue))
                .orElse(null);
    }
}
