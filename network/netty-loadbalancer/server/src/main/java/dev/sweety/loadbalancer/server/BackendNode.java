package dev.sweety.loadbalancer.server;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public final class BackendNode {

    private final String id;
    private final InetSocketAddress address;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private volatile boolean healthy = true;

    public BackendNode(String id, InetSocketAddress address) {
        this.id = id;
        this.address = address;
    }

    public String id() { return id; }
    public InetSocketAddress address() { return address; }
    public int activeConnections() { return activeConnections.get(); }
    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }

    public void incrementConnections() { activeConnections.incrementAndGet(); }
    public void decrementConnections() { activeConnections.decrementAndGet(); }
}
