package dev.sweety.project.usage;

import dev.sweety.core.thread.ProfileThread;
import dev.sweety.core.thread.ThreadManager;
import dev.sweety.core.time.StopWatch;
import dev.sweety.netty.loadbalancer.backend.MetricSampler;
import dev.sweety.netty.loadbalancer.common.metrics.SmoothedLoad;

public class UsageTest {

    // === OUTPUT PER LB ===

    public static void main(String[] args) {
        MetricSampler usage = new MetricSampler();
        StopWatch timer = new StopWatch();

        int count = 0;

        ProfileThread t = new ProfileThread("sampler");

        while (true) {
            if (!timer.hasPassedMillis(250L)) {
                Thread.onSpinWait();
                continue;
            }

            count++;
            timer.reset();

            if (count == 4){
                count = 0;
                t.execute(() -> System.out.println(usage.sample()));
            }
        }
    }
}
