package dev.sweety.netty.common.backend;


public final class BackendSettings {

    // Backend settings
    private static long METRICS_DELAY_MS = 2500L;
    private static long REQUEST_TIMEOUT_SECONDS = 20L;
    private static float EMA_ALPHA = 0.35f;

    // BackendNode settings
    public static int MAX_IN_FLIGHT = Integer.parseInt(System.getenv().getOrDefault("LB_MAX_IN_FLIGHT", "500"));
    public static int IN_FLIGHT_ACCEPTABLE = Integer.parseInt(System.getenv().getOrDefault("LB_IN_FLIGHT_ACCEPTABLE", "350"));
    private static float MAX_EXPECTED_LATENCY = 1000f;

    public static long METRICS_DELAY_MS() {
        return METRICS_DELAY_MS;
    }

    public static void METRICS_DELAY_MS(long metricsDelayMs) {
        METRICS_DELAY_MS = metricsDelayMs;
    }

    public static long REQUEST_TIMEOUT_SECONDS() {
        return REQUEST_TIMEOUT_SECONDS;
    }

    public static void REQUEST_TIMEOUT_SECONDS(long requestTimeoutSeconds) {
        REQUEST_TIMEOUT_SECONDS = requestTimeoutSeconds;
    }

    public static float EMA_ALPHA() {
        return EMA_ALPHA;
    }

    public static void EMA_ALPHA(float emaAlpha) {
        EMA_ALPHA = emaAlpha;
    }

    public static int MAX_IN_FLIGHT() {
        return MAX_IN_FLIGHT;
    }

    public static void MAX_IN_FLIGHT(int maxInFlight) {
        MAX_IN_FLIGHT = maxInFlight;
    }

    public static int IN_FLIGHT_ACCEPTABLE() {
        return IN_FLIGHT_ACCEPTABLE;
    }

    public static void IN_FLIGHT_ACCEPTABLE(int inFlightAcceptable) {
        IN_FLIGHT_ACCEPTABLE = inFlightAcceptable;
    }

    public static float MAX_EXPECTED_LATENCY() {
        return MAX_EXPECTED_LATENCY;
    }

    public static void MAX_EXPECTED_LATENCY(float maxExpectedLatency) {
        MAX_EXPECTED_LATENCY = maxExpectedLatency;
    }
}
