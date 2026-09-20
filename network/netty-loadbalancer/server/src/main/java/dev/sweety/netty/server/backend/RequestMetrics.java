package dev.sweety.netty.server.backend;

import dev.sweety.math.pool.ObjectPool;
import dev.sweety.netty.metrics.EMA;
import dev.sweety.math.list.Long2ObjectConcurrentOpenHashMap;

import java.util.concurrent.atomic.AtomicLong;

public class RequestMetrics {

    private final Long2ObjectConcurrentOpenHashMap<RequestInfo> pendingRequests = Long2ObjectConcurrentOpenHashMap.create();

    private final AtomicLong currentLoad = new AtomicLong(0L);
    private final EMA latencyEma = new EMA(0.75f);  // per latency media
    private final EMA totalLoadEma = new EMA(0.25f);  // per average bandwidth load
    private final EMA currentLoadEma = new EMA(0.35f); // per current pending load medio

    public void addRequest(long requestId, int load) {
        pendingRequests.put(requestId, RequestInfo.of(System.nanoTime(), load));
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
        info.release();
    }

    public void timeoutRequest(long requestId) {
        RequestInfo info = pendingRequests.remove(requestId);
        if (info == null) return;
        long l = currentLoad.addAndGet(-info.load());
        currentLoadEma.update(l);
        info.release();
    }

    public double getAverageLatency() {
        return latencyEma.get();
    }

    public double getAverageBandwidthLoad() {
        return totalLoadEma.get();
    }

    public double getCurrentAverageBandwidthLoad() {
        long pending = pendingRequests.size();
        double realAvg = pending == 0 ? 0 : (double) currentLoad.get() / pending;
        double smoothed = currentLoadEma.get();

        return 0.5f * (realAvg + smoothed);
    }

    public void reset() {
        pendingRequests.forEachEntry((k, v) -> v.release());
        pendingRequests.clear();
        latencyEma.reset();
        totalLoadEma.reset();
        currentLoadEma.reset();
        currentLoad.set(0L);
    }

    public static final class RequestInfo {
        private static final ObjectPool<RequestInfo> POOL = ObjectPool.shared(RequestInfo::new)
                .reset(RequestInfo::reset)
                .build();

        private long timestamp;
        private int load;

        public RequestInfo() {
            this(0L, 0);
        }

        public RequestInfo(long timestamp, int load) {
            this.timestamp = timestamp;
            this.load = load;
        }

        public static RequestInfo of(long timestamp, int load) {
            RequestInfo info = POOL.acquire();
            info.timestamp = timestamp;
            info.load = load;
            return info;
        }

        public void release() {
            POOL.release(this);
        }

        public void reset() {
            this.timestamp = 0L;
            this.load = 0;
        }

        public long timestamp() {
            return timestamp;
        }

        public int load() {
            return load;
        }
    }

}
