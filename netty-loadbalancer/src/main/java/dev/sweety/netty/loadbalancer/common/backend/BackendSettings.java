package dev.sweety.netty.loadbalancer.common.backend;

import lombok.Setter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BackendSettings {

    // Backend settings
    @Setter
    public long METRICS_DELAY_MS = 2500L;
    @Setter
    public long REQUEST_TIMEOUT_SECONDS = 20L;
    @Setter
    public float EMA_ALPHA = 0.35f;

    // BackendNode settings
    @Setter
    public int MAX_IN_FLIGHT = 50;
    @Setter
    public int IN_FLIGHT_ACCEPTABLE = 35;
    @Setter
    public float MAX_EXPECTED_LATENCY = 1000f;

}
