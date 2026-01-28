package dev.sweety.netty.loadbalancer.server.backend;

import dev.sweety.netty.loadbalancer.common.metrics.EMA;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RequestManager {

    private final Map<Long, RequestInfo> pendingRequests = new ConcurrentHashMap<>();

    private final AtomicLong currentLoad = new AtomicLong(0L);
    private final EMA latencyEma = new EMA(0.75f);  // per latency media
    private final EMA totalLoadEma = new EMA(0.25f);  // per average bandwidth load
    private final EMA currentLoadEma = new EMA(0.35f); // per current pending load medio

    public void addRequest(long requestId, int load) {
        pendingRequests.put(requestId, new RequestInfo(System.nanoTime(), load));
        totalLoadEma.update(load);
        currentLoadEma.update(load);
        currentLoad.addAndGet(load);
    }

    public void completeRequest(long requestId) {
        RequestInfo info = pendingRequests.remove(requestId);
        if (info == null) return;

        latencyEma.update(System.nanoTime() - info.timestamp());
        long l = currentLoad.addAndGet(-info.load());
        currentLoadEma.update(l);
    }

    public void timeoutRequest(long requestId) {
        RequestInfo info = pendingRequests.remove(requestId);
        if (info == null) return;
        long l = currentLoad.addAndGet(-info.load());
        currentLoadEma.update(l);
    }

    public float getAverageLatency() {
        return latencyEma.get();
    }

    public float getAverageBandwidthLoad() {
        return totalLoadEma.get();
    }

    public float getCurrentAverageBandwidthLoad() {
        long pending = pendingRequests.size();
        float realAvg = pending == 0 ? 0 : (float) currentLoad.get() / pending;
        float smoothed = currentLoadEma.get();

        return 0.5f * (realAvg + smoothed);
    }

    public void reset() {
        pendingRequests.clear();
        latencyEma.reset();
        totalLoadEma.reset();
        currentLoadEma.reset();
        currentLoad.set(0L);
    }

    public record RequestInfo(long timestamp, int load){

    }

}
