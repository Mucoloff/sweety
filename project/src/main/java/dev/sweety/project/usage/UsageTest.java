package dev.sweety.project.usage;

import dev.sweety.core.thread.ProfileThread;
import dev.sweety.core.time.StopWatch;
import dev.sweety.netty.loadbalancer.backend.MetricSampler;

public class UsageTest {

    // === OUTPUT PER LB ===

    public static void main(String[] args) {
        final MetricSampler sampler = new MetricSampler();
        final StopWatch timer = new StopWatch();

        sampler.startSampling();

        while (true) {
            if (!timer.hasPassedMillis(1000L)) {
                Thread.onSpinWait();
                continue;
            }

            timer.reset();
            System.out.println(sampler.get());
        }
    }
}
