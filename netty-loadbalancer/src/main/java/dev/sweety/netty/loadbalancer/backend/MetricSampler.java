package dev.sweety.netty.loadbalancer.backend;

import dev.sweety.core.thread.ThreadManager;
import dev.sweety.core.time.StopWatch;
import dev.sweety.netty.loadbalancer.common.metrics.EMA;
import dev.sweety.netty.loadbalancer.common.metrics.SmoothedLoad;
import dev.sweety.netty.loadbalancer.common.metrics.state.LoadGate;
import dev.sweety.netty.loadbalancer.common.metrics.state.NodeState;
import org.jetbrains.annotations.NotNull;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OSThread;
import oshi.software.os.OperatingSystem;
import oshi.util.GlobalConfig;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class MetricSampler {

    private final OperatingSystem os;

    private final CentralProcessor cpu;
    private final GlobalMemory mem;

    private final EMA cpuEma;
    private final EMA ramEma;
    private final EMA totCpuEma;
    private final EMA totRamEma;
    private final EMA threadPressureEma;
    private final EMA openFilesEma;
    private final EMA systemLoadEma;
    private final LoadGate gate;
    private final Config config;

    private final StopWatch overrideCooldownWatch = new StopWatch();

    private final int pid;
    private volatile OSProcess prevProcess;
    private volatile long[] prevCpuTicks;

    static {
        if (dev.sweety.core.system.OperatingSystem.detectOS().equals(dev.sweety.core.system.OperatingSystem.WINDOWS)) {
            //System.setProperty("oshi.os.windows.loadaverage", "true");
            GlobalConfig.set("oshi.os.windows.loadaverage", true);
        }
    }

    public MetricSampler() {
        this(new Config());
    }

    public MetricSampler(final Config config) {
        this.config = config;

        final SystemInfo si = new SystemInfo();
        final HardwareAbstractionLayer hw = si.getHardware();
        this.os = si.getOperatingSystem();
        this.cpu = hw.getProcessor();
        this.mem = hw.getMemory();

        this.cpuEma = new EMA(config.ema.cpu);
        this.ramEma = new EMA(config.ema.ram);
        this.totCpuEma = new EMA(config.ema.totCpu);
        this.totRamEma = new EMA(config.ema.totRam);
        this.threadPressureEma = new EMA(config.ema.threadPressure);
        this.openFilesEma = new EMA(config.ema.openFiles);
        this.systemLoadEma = new EMA(config.ema.systemLoad);

        this.prevProcess = os.getCurrentProcess();
        this.pid = prevProcess.getProcessID();
        this.prevCpuTicks = cpu.getSystemCpuLoadTicks();

        if (config.gate != null) {
            this.gate = config.gate;
        } else {
            long soft = prevProcess.getSoftOpenFileLimit();
            long hard = prevProcess.getHardOpenFileLimit();
            LoadGate.Limits openFilesLimits = config.openFilesLimitsCalculator.apply(soft, hard);

            this.gate = new LoadGate(0.35f, 0.55f,
                    config.cpuLimits,
                    config.ramLimits,
                    openFilesLimits,
                    config.threadPressureLimits,
                    config.systemLoadLimits
            );
        }
    }

    public synchronized void reset() {
        this.prevProcess = os.getCurrentProcess();
        this.prevCpuTicks = cpu.getSystemCpuLoadTicks();
        this.cpuEma.reset();
        this.ramEma.reset();
        this.totCpuEma.reset();
        this.totRamEma.reset();
        this.threadPressureEma.reset();
        this.openFilesEma.reset();
        this.gate.reset();
        this.overrideCooldownWatch.reset();
        this.threadManager.shutdown();
    }

    private final ThreadManager threadManager = new ThreadManager("sampler-thread-manager");

    private void sample(Runnable runnable, long timeMs) {
        if (timeMs <= 0) return;
        this.threadManager.getAvailableProfileThread().scheduleWithFixedDelay(runnable, timeMs, timeMs, TimeUnit.MILLISECONDS);
    }

    public void startSampling() {
        sample(this::sampleProcessCpu, config.timings.cpu);
        sample(this::sampleProcessRam, config.timings.ram);
        sample(this::sampleTotalCpu, config.timings.cpuTotal);
        sample(this::sampleTotalRam, config.timings.ramTotal);
        sample(this::sampleOpenFiles, config.timings.openFiles);
        sample(this::sampleThreadPressure, config.timings.threadPressure);
        sample(this::sampleSystemLoad, config.timings.systemLoad);
    }

    private void sampleProcessCpu() {
        OSProcess currProcess = os.getProcess(prevProcess.getProcessID());
        if (currProcess == null) currProcess = prevProcess;
        float cpuProcRawVal = (float) currProcess.getProcessCpuLoadBetweenTicks(prevProcess);
        float cpuProcRaw = (Float.isNaN(cpuProcRawVal) ? 0f : cpuProcRawVal);
        final int logicalProcessorCount = Math.max(1, cpu.getLogicalProcessorCount());
        float cpuProcNorm = cpuProcRaw / logicalProcessorCount;
        this.cpuEma.update(cpuProcNorm);

        this.prevProcess = currProcess;
    }

    private void sampleProcessRam() {
        OSProcess currProcess = os.getProcess(pid);
        if (currProcess == null) return;
        long rss = currProcess.getResidentSetSize();
        float ramProcNorm = (float) rss / Math.max(1, mem.getTotal());
        this.ramEma.update(ramProcNorm);
    }

    private void sampleTotalCpu() {
        long[] currCpuTicks = cpu.getSystemCpuLoadTicks();
        float cpuSystemNorm = (float) cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = currCpuTicks;
        if (cpuSystemNorm > 0f) this.totCpuEma.update(cpuSystemNorm);
    }

    private void sampleTotalRam() {
        float ramSystemNorm = 1f - ((float) mem.getAvailable() / Math.max(1, mem.getTotal()));
        this.totRamEma.update(ramSystemNorm);
    }

    private void sampleOpenFiles() {
        OSProcess currProcess = os.getProcess(pid);
        if (currProcess == null) return;
        long openFiles = currProcess.getOpenFiles();
        long maxOpenFiles = currProcess.getSoftOpenFileLimit();
        if (maxOpenFiles <= 0) maxOpenFiles = currProcess.getHardOpenFileLimit();
        float openFilesNorm = maxOpenFiles > 0 ? (float) openFiles / maxOpenFiles : 0f;
        this.openFilesEma.update(openFilesNorm);
    }

    private void sampleThreadPressure() {
        OSProcess process = os.getProcess(pid);
        if (process == null) return;
        float val = calculatePressure(process);
        this.threadPressureEma.update(val);
    }

    private void sampleSystemLoad() {
        double[] loads = cpu.getSystemLoadAverage(3);
        float systemLoad = (float) (loads[0] * 0.6f + loads[1] * 0.3f + loads[2] * 0.1f);
        if (systemLoad < 0) systemLoad = 0f;
        final int logicalProcessorCount = Math.max(1, cpu.getLogicalProcessorCount());
        float systemLoadNorm = systemLoad / logicalProcessorCount;
        this.systemLoadEma.update(systemLoadNorm);
    }

    public synchronized SmoothedLoad get() {
        float cpuSmooth = cpuEma.get();
        float ramSmooth = ramEma.get();
        float totCpuSmooth = totCpuEma.get();
        float totRamSmooth = totRamEma.get();
        float openFilesSmooth = openFilesEma.get();
        float threadPressureSmooth = threadPressureEma.get();
        float systemLoadSmooth = systemLoadEma.get();

        // state

        final boolean degraded = totCpuSmooth > config.override.cpuThreshold || totRamSmooth > config.override.ramThreshold || systemLoadSmooth > config.override.systemLoadThreshold;
        final boolean timer = overrideCooldownWatch.hasPassedMillis(config.override.cooldownMs);

        final NodeState state;
        if (degraded && timer) {
            overrideCooldownWatch.reset();
            state = NodeState.DEGRADED;
        } else {
            state = gate.update(cpuSmooth, ramSmooth, openFilesSmooth, threadPressureSmooth, systemLoadSmooth);
        }

        return new SmoothedLoad(
                cpuSmooth,
                ramSmooth,
                totCpuSmooth,
                totRamSmooth,
                openFilesSmooth,
                threadPressureSmooth,
                systemLoadSmooth,
                state
        );
    }

    private float calculatePressure(OSProcess process) {
        int threadCount = process.getThreadCount();
        // Fail-safe if thread count > 0 but details list is empty (OSHI limitation sometimes)
        if (threadCount == 0) return 0f;

        float rawPressureSum = 0f;

        // Try to get thread details. If empty, fall back to "smart" estimation
        var threads = process.getThreadDetails();

        // Fix: If list is empty but threadCount > 0, it means OSHI didn't fetch details.
        // We can assume normal pressure distribution for a running app if we can't see threads.
        if (threads.isEmpty())
            return pressure(process.getState()) * 0.55f + 0.05f; // Assume 55% running, 5% sleeping, rest low load

        for (OSThread thread : threads) {
            rawPressureSum += pressure(thread.getState());
        }

        // Avoid division by zero
        return rawPressureSum / Math.max(1, threads.size());
    }

    private float pressure(OSProcess.State state) {
        return switch (state) {
            case RUNNING -> 1.0f;
            // WAITING implies blocked/waiting, SLEEPING implies timed wait. Both low load.
            case WAITING, SLEEPING -> 0.15f;
            // On some OS/JVM combinations, threads might appear as UNKNOWN/OTHER but still consume resources.
            // We apply a safe default.
            default -> 0.1f;
        };
    }

    public static class Config {

        // use negative values to disable
        public static class EMAConfig {
            public float cpu = 0.35f;
            public float ram = 0.25f;
            public float totCpu = 0.75f;
            public float totRam = 0.45f;
            public float threadPressure = 0.5f;
            public float openFiles = 0.15f;
            public float systemLoad = 0.5f;
        }

        public static class OverrideConfig {
            public float cpuThreshold = 0.85f;
            public float ramThreshold = 0.90f;
            public float systemLoadThreshold = 0.90f;
            public long cooldownMs = 5000;
        }

        public static class Timings {
            public long cpu = 150, ram = 150, cpuTotal = 150, ramTotal = 150, openFiles = 1000, threadPressure = 1000, systemLoad = 1000;
        }

        public EMAConfig ema = new EMAConfig();
        public OverrideConfig override = new OverrideConfig();
        public Timings timings = new Timings();

        public LoadGate gate; // If null, built from limits below

        public LoadGate.Limits cpuLimits = new LoadGate.Limits(0.55f, 0.70f, 0.6f);
        public LoadGate.Limits ramLimits = new LoadGate.Limits(0.60f, 0.75f, 0.4f);
        public LoadGate.Limits openFilesLimits = new LoadGate.Limits(0.60f, 0.80f, 0.5f);
        public LoadGate.Limits aggressiveFilesLimits = new LoadGate.Limits(0.40f, 0.60f, 0.7f); // Aggressive weight, lower threshold
        public LoadGate.Limits threadPressureLimits = new LoadGate.Limits(0.50f, 0.70f, 0.5f);
        public LoadGate.Limits systemLoadLimits = new LoadGate.Limits(0.55f, 0.75f, 0.5f);

        // Calculate limits based on soft and hard open file limits
        public BiFunction<Long, Long, LoadGate.Limits> openFilesLimitsCalculator = (soft, hard) -> {
            // Example dynamic logic: if limit is low (< 4096), be more conservative
            if (soft > 0 && soft < 4096) return aggressiveFilesLimits;
            return openFilesLimits;
        };

        public Config() {
        }

        public Config withGate(@NotNull LoadGate gate) {
            this.gate = gate;
            return this;
        }

        public Config withEMA(EMAConfig ema) {
            this.ema = ema;
            return this;
        }

        public Config withOverride(OverrideConfig override) {
            this.override = override;
            return this;
        }
    }

}
