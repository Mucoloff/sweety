package dev.sweety.netty.backend;

import dev.sweety.netty.metrics.EMA;
import dev.sweety.netty.metrics.SmoothedLoad;
import dev.sweety.netty.metrics.state.LoadGate;
import dev.sweety.netty.metrics.state.NodeState;
import dev.sweety.thread.ThreadManager;
import dev.sweety.time.StopWatch;
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
        if (dev.sweety.util.system.OperatingSystem.WINDOWS.isThis()) {
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

            this.gate = new LoadGate(0.35, 0.55,
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
        double cpuProcRawVal = currProcess.getProcessCpuLoadBetweenTicks(prevProcess);
        double cpuProcRaw = (Double.isNaN(cpuProcRawVal) ? 0 : cpuProcRawVal);
        final int logicalProcessorCount = Math.max(1, cpu.getLogicalProcessorCount());
        double cpuProcNorm = cpuProcRaw / logicalProcessorCount;
        this.cpuEma.update(cpuProcNorm);

        this.prevProcess = currProcess;
    }

    private void sampleProcessRam() {
        OSProcess currProcess = os.getProcess(pid);
        if (currProcess == null) return;
        long rss = currProcess.getResidentSetSize();
        double ramProcNorm = (double) rss / Math.max(1, mem.getTotal());
        this.ramEma.update(ramProcNorm);
    }

    private void sampleTotalCpu() {
        long[] currCpuTicks = cpu.getSystemCpuLoadTicks();
        double cpuSystemNorm = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = currCpuTicks;
        if (cpuSystemNorm > 0) this.totCpuEma.update(cpuSystemNorm);
    }

    private void sampleTotalRam() {
        double ramSystemNorm = 1 - ((double) mem.getAvailable() / Math.max(1, mem.getTotal()));
        this.totRamEma.update(ramSystemNorm);
    }

    private void sampleOpenFiles() {
        OSProcess currProcess = os.getProcess(pid);
        if (currProcess == null) return;
        long openFiles = currProcess.getOpenFiles();
        long maxOpenFiles = currProcess.getSoftOpenFileLimit();
        if (maxOpenFiles <= 0) maxOpenFiles = currProcess.getHardOpenFileLimit();
        double openFilesNorm = maxOpenFiles > 0 ? (double) openFiles / maxOpenFiles : 0;
        this.openFilesEma.update(openFilesNorm);
    }

    private void sampleThreadPressure() {
        OSProcess process = os.getProcess(pid);
        if (process == null) return;
        double val = calculatePressure(process);
        this.threadPressureEma.update(val);
    }

    private void sampleSystemLoad() {
        double[] loads = cpu.getSystemLoadAverage(3);
        double systemLoad = loads[0] * 0.6f + loads[1] * 0.3f + loads[2] * 0.1;
        if (systemLoad < 0) systemLoad = 0;
        final int logicalProcessorCount = Math.max(1, cpu.getLogicalProcessorCount());
        double systemLoadNorm = systemLoad / logicalProcessorCount;
        this.systemLoadEma.update(systemLoadNorm);
    }

    public synchronized SmoothedLoad get() {
        double cpuSmooth = cpuEma.get();
        double ramSmooth = ramEma.get();
        double totCpuSmooth = totCpuEma.get();
        double totRamSmooth = totRamEma.get();
        double openFilesSmooth = openFilesEma.get();
        double threadPressureSmooth = threadPressureEma.get();
        double systemLoadSmooth = systemLoadEma.get();

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

    private double calculatePressure(OSProcess process) {
        int threadCount = process.getThreadCount();
        // Fail-safe if thread count > 0 but details list is empty (OSHI limitation sometimes)
        if (threadCount == 0) return 0;

        double rawPressureSum = 0;

        // Try to get thread details. If empty, fall back to "smart" estimation
        var threads = process.getThreadDetails();

        // Fix: If list is empty but threadCount > 0, it means OSHI didn't fetch details.
        // We can assume normal pressure distribution for a running app if we can't see threads.
        if (threads.isEmpty())
            return pressure(process.getState()) * 0.55 + 0.05; // Assume 55% running, 5% sleeping, rest low load

        for (OSThread thread : threads) {
            rawPressureSum += pressure(thread.getState());
        }

        // Avoid division by zero
        return rawPressureSum / Math.max(1, threads.size());
    }

    private double pressure(OSProcess.State state) {
        return switch (state) {
            case RUNNING -> 1.0;
            // WAITING implies blocked/waiting, SLEEPING implies timed wait. Both low load.
            case WAITING, SLEEPING -> 0.15;
            // On some OS/JVM combinations, threads might appear as UNKNOWN/OTHER but still consume resources.
            // We apply a safe default.
            default -> 0.1;
        };
    }

    public static class Config {

        // use negative values to disable
        public static class EMAConfig {
            public double cpu = 0.35;
            public double ram = 0.25;
            public double totCpu = 0.75;
            public double totRam = 0.45;
            public double threadPressure = 0.5;
            public double openFiles = 0.15;
            public double systemLoad = 0.5;
        }

        public static class OverrideConfig {
            public double cpuThreshold = 0.85;
            public double ramThreshold = 0.90;
            public double systemLoadThreshold = 0.90;
            public long cooldownMs = 5000;
        }

        public static class Timings {
            // Reduced from 150ms to avoid hammering /proc via OSHI under load (was ~7% CPU from sampler-thread).
            // threadPressure iterates all process threads via getThreadDetails() — keep it rare.
            public long cpu = 500, ram = 500, cpuTotal = 1000, ramTotal = 1000, openFiles = 5000, threadPressure = 5000, systemLoad = 2000;
        }

        public EMAConfig ema = new EMAConfig();
        public OverrideConfig override = new OverrideConfig();
        public Timings timings = new Timings();

        public LoadGate gate; // If null, built from limits below

        public LoadGate.Limits cpuLimits = new LoadGate.Limits(0.55, 0.70, 0.6);
        public LoadGate.Limits ramLimits = new LoadGate.Limits(0.60, 0.75, 0.4);
        public LoadGate.Limits openFilesLimits = new LoadGate.Limits(0.60, 0.80, 0.5);
        public LoadGate.Limits aggressiveFilesLimits = new LoadGate.Limits(0.40, 0.60, 0.7); // Aggressive weight, lower threshold
        public LoadGate.Limits threadPressureLimits = new LoadGate.Limits(0.50, 0.70, 0.5);
        public LoadGate.Limits systemLoadLimits = new LoadGate.Limits(0.55, 0.75, 0.5);

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