package dev.sweety.netty.server.backend;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RequestMetricsPoolTest {

    @Test
    void testRequestMetricsPoolingLifecycle() {
        RequestMetrics metrics = new RequestMetrics();

        // Add 10 requests
        for (long i = 1; i <= 10; i++) {
            metrics.addRequest(i, 50);
        }

        assertEquals(0.0, metrics.getAverageLatency());
        assertTrue(metrics.getAverageBandwidthLoad() > 0);

        // Complete 5 requests
        for (long i = 1; i <= 5; i++) {
            metrics.completeRequest(i);
        }

        // Timeout remaining 5 requests
        for (long i = 6; i <= 10; i++) {
            metrics.timeoutRequest(i);
        }

        // Reset
        metrics.reset();
        assertEquals(0.0, metrics.getCurrentAverageBandwidthLoad());
    }

    @Test
    void testConcurrentAddAndComplete() throws InterruptedException {
        RequestMetrics metrics = new RequestMetrics();
        int threads = 8;
        int countPerThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < countPerThread; i++) {
                        long id = ((long) threadId << 32) | i;
                        metrics.addRequest(id, 10);
                        metrics.completeRequest(id);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        metrics.reset();
    }
}
