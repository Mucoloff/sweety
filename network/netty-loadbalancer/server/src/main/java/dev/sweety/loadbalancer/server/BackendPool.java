package dev.sweety.loadbalancer.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance backend pool with Round-Robin and Least-Connections selection strategies.
 */
public final class BackendPool {

    public enum Strategy {
        ROUND_ROBIN,
        LEAST_CONNECTIONS
    }

    private final List<BackendNode> nodes = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Strategy strategy;

    public BackendPool(Strategy strategy) {
        this.strategy = strategy;
    }

    public static BackendPool roundRobin() {
        return new BackendPool(Strategy.ROUND_ROBIN);
    }

    public static BackendPool leastConnections() {
        return new BackendPool(Strategy.LEAST_CONNECTIONS);
    }

    public void addNode(BackendNode node) {
        nodes.add(node);
    }

    public void removeNode(String id) {
        nodes.removeIf(n -> n.id().equals(id));
    }

    public BackendNode select() {
        if (nodes.isEmpty()) return null;

        List<BackendNode> healthyNodes = nodes.stream().filter(BackendNode::isHealthy).toList();
        if (healthyNodes.isEmpty()) return null;

        if (strategy == Strategy.LEAST_CONNECTIONS) {
            BackendNode best = healthyNodes.getFirst();
            for (int i = 1; i < healthyNodes.size(); i++) {
                BackendNode cur = healthyNodes.get(i);
                if (cur.activeConnections() < best.activeConnections()) {
                    best = cur;
                }
            }
            return best;
        }

        int idx = Math.abs(roundRobinIndex.getAndIncrement() % healthyNodes.size());
        return healthyNodes.get(idx);
    }

    public List<BackendNode> nodes() {
        return nodes;
    }
}
