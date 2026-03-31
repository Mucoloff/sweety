package dev.sweety.saas.backend.monitoring;


import dev.sweety.saas.backend.Service;
import dev.sweety.saas.service.packet.global.monitoring.request.MonitoringMetricReportRequest;
import dev.sweety.saas.service.packet.global.monitoring.transaction.MonitoringMetricReportTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class for services to report metrics to the Monitoring service
 */
public class MetricsReporter {

    private final Service service;
    private final Map<String, AtomicLong> counters;
    private long lastReportTime;
    private static final long REPORT_INTERVAL = 5000; // 5 seconds

    public MetricsReporter(Service service) {
        this.service = service;
        this.counters = new HashMap<>();
        this.lastReportTime = System.currentTimeMillis();
    }

    /**
     * Increment a counter metric
     */
    public void increment(String metricName) {
        counters.computeIfAbsent(metricName, k -> new AtomicLong(0)).incrementAndGet();
        tryReport();
    }

    /**
     * Add a value to a metric
     */
    public void add(String metricName, long value) {
        counters.computeIfAbsent(metricName, k -> new AtomicLong(0)).addAndGet(value);
        tryReport();
    }

    /**
     * Set a metric value
     */
    public void set(String metricName, long value) {
        counters.put(metricName, new AtomicLong(value));
        tryReport();
    }

    /**
     * Try to report metrics if enough time has passed
     */
    private void tryReport() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime >= REPORT_INTERVAL) {
            report();
        }
    }

    /**
     * Force immediate report
     */
    public void report() {

        // Collect current counter values
        final Map<String, Long> metrics = new HashMap<>();
        counters.forEach((key, value) -> {
            long val = value.get();
            if (val > 0) {
                metrics.put(key, val);
                value.set(0); // Reset counter after reporting
            }
        });

        if (metrics.isEmpty()) return;

        // Send metrics report
        final MonitoringMetricReportRequest request = new MonitoringMetricReportRequest(service.type(), metrics);
        service.sendHubTransaction(new MonitoringMetricReportTransaction(request)).whenComplete((r, t) -> {
        });

        lastReportTime = System.currentTimeMillis();
    }

    /**
     * Get current counter value (without resetting)
     */
    public long get(String metricName) {
        return counters.getOrDefault(metricName, new AtomicLong(0)).get();
    }
}


