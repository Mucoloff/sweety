package dev.sweety.project.usage;

import dev.sweety.core.time.StopWatch;
import dev.sweety.netty.loadbalancer.refact.backend.MetricSampler;
import dev.sweety.netty.loadbalancer.refact.common.metrics.SmoothedLoad;

public class UsageTest {



    // === OUTPUT PER LB ===


    public static void main(String[] args) {
        MetricSampler usage = new MetricSampler();
        StopWatch timer = new StopWatch();

        while (true) {
            if (!timer.hasPassed(1000L)) {
                Thread.onSpinWait();
                continue;
            }
            timer.reset();

            SmoothedLoad l = usage.sample();

            System.out.printf(
                    "cpu=%.4f ram=%.4f cpuSys=%.3f ramSys=%.3f state=%s%n",
                    l.cpu(),
                    l.ram(),
                    l.cpuTotal(),
                    l.ramTotal(),
                    l.state()
            );
        }
    }
}
