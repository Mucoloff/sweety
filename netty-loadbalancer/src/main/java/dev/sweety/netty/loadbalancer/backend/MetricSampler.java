package dev.sweety.netty.loadbalancer.backend;

import dev.sweety.core.thread.ThreadManager;
import dev.sweety.core.time.StopWatch;
import dev.sweety.core.util.ObjectUtils;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static java.lang.Thread.State.TIMED_WAITING;

public class MetricSampler {

    private final SystemInfo si;
    private final HardwareAbstractionLayer hw;
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

    // Async thread pressure calculation
    private final AtomicReference<Float> asyncThreadPressure = new AtomicReference<>(0f);
    private final AtomicBoolean isThreadPressureUpdating = new AtomicBoolean(false);

    private OSProcess prevProcess;
    private long[] prevCpuTicks;

    static {
        if (dev.sweety.core.system.OperatingSystem.detectOS().equals(dev.sweety.core.system.OperatingSystem.WINDOWS)) {
            //System.setProperty("oshi.os.windows.loadaverage", "true");
            GlobalConfig.set("oshi.os.windows.loadaverage", true);
        }
    }

    public MetricSampler() {
        this(new Config());
    }

    public MetricSampler(Config config) {
        this.config = config;

        this.si = new SystemInfo();
        this.hw = si.getHardware();
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
                    openFilesLimits
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
    }

    private final ThreadManager threadManager = new ThreadManager("sampler-thread-manager");

    public synchronized SmoothedLoad sample() {
        // ===== PROCESS =====
        // Process might be dead or not found, fallback to previous to avoid crash
        OSProcess currProcess = ObjectUtils.nullOption(os.getProcess(prevProcess.getProcessID()), prevProcess);

        // Sampling CPU
        float cpuProcRawVal = (float) currProcess.getProcessCpuLoadBetweenTicks(prevProcess);
        float cpuProcRaw =  (Float.isNaN(cpuProcRawVal) ? 0f : cpuProcRawVal);
        final int logicalProcessorCount = Math.max(1, cpu.getLogicalProcessorCount());
        float cpuProcNorm = cpuProcRaw / logicalProcessorCount;
        float cpuSmooth = cpuEma.update(cpuProcNorm);

        // Sampling RAM
        long rss = currProcess.getResidentSetSize();
        float ramProcNorm = (float) rss / Math.max(1, mem.getTotal());
        float ramSmooth = ramEma.update(ramProcNorm);

        // Sampling TOTAL CPU
        long[] currCpuTicks = cpu.getSystemCpuLoadTicks();
        float cpuSystemNorm = (float) cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = currCpuTicks;
        float totCpuSmooth = cpuSystemNorm > 0f ? totCpuEma.update(cpuSystemNorm) : totCpuEma.get(); // Avoid updating with 0 on invalid reads

        // Sampling TOTAL RAM
        float ramSystemNorm = 1f - ((float) mem.getAvailable() / Math.max(1, mem.getTotal()));
        float totRamSmooth = totRamEma.update(ramSystemNorm);

        // Sampling OPEN FILES
        long openFiles = currProcess.getOpenFiles();
        long maxOpenFiles = currProcess.getSoftOpenFileLimit(); // Soft limit is usually what matters for crash
        if (maxOpenFiles <= 0) maxOpenFiles = currProcess.getHardOpenFileLimit();
        float openFilesNorm = maxOpenFiles > 0 ? (float) openFiles / maxOpenFiles : 0f;
        float openFilesSmooth = openFilesEma.update(openFilesNorm);


        // ASYNC PRESSURE UPDATE
        if (isThreadPressureUpdating.compareAndSet(false, true)) {
            // Must fetch a FRESH snapshot for thread details, as getProcess() snapshot might not fully populate threads deep list
            // or the list might be empty if not specifically requested depending on OSHI version/flags.
            // Actually getThreadDetails() should work on the snapshot if valid.
            // Let's create a fresh thread update task.
            int pid = currProcess.getProcessID();

            CompletableFuture.runAsync(() -> {
                try {
                    // Fetch fresh process process to ensure we have threads
                    // Note: In OSHI 6+, getProcess(pid) attempts to populate everything.
                    OSProcess freshProcess = os.getProcess(pid);
                    if (freshProcess != null) {
                        float val = calculatePressure(freshProcess);
                        asyncThreadPressure.set(val);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isThreadPressureUpdating.set(false);
                }
            });
        }

        float currentPressureAvg = asyncThreadPressure.get();
        float threadPressureSmooth = threadPressureEma.update(currentPressureAvg);

        // ===== SYSTEM =====
        double[] loads = cpu.getSystemLoadAverage(3);
        float systemLoad = (float) (loads[0] * 0.6f + loads[1] * 0.3f + loads[2] * 0.1f);
        if (systemLoad < 0) systemLoad = 0f;
        float systemLoadNorm = systemLoad / logicalProcessorCount;
        float systemLoadSmooth = systemLoadEma.update(systemLoadNorm); // Quick EMA for smoothing spikes

        // ===== UPDATE SNAPSHOT =====
        prevProcess = currProcess;

        // state
        NodeState state = gate.update(cpuSmooth, ramSmooth, openFilesSmooth);
        NodeState finalState;
        if (totCpuSmooth > config.override.cpuThreshold || totRamSmooth > config.override.ramThreshold || systemLoadSmooth > config.override.systemLoadThreshold) {
            if (overrideCooldownWatch.hasPassedMillis(config.override.cooldownMs)) {
                overrideCooldownWatch.reset();
                finalState = NodeState.DEGRADED;
            } else {
                finalState = state;
            }
        } else {
            finalState = state;
        }

        return new SmoothedLoad(
                cpuSmooth,
                ramSmooth,
                totCpuSmooth,
                totRamSmooth,
                openFilesSmooth,
                threadPressureSmooth,
                systemLoadSmooth,
                finalState
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

        public EMAConfig ema = new EMAConfig();
        public OverrideConfig override = new OverrideConfig();


        public LoadGate gate; // If null, built from limits below

        public LoadGate.Limits cpuLimits = new LoadGate.Limits(0.55f, 0.70f, 0.6f);
        public LoadGate.Limits ramLimits = new LoadGate.Limits(0.60f, 0.75f, 0.4f);

        // Calculate limits based on soft and hard open file limits
        public BiFunction<Long, Long, LoadGate.Limits> openFilesLimitsCalculator = (soft, hard) -> {
            // Example dynamic logic: if limit is low (< 4096), be more conservative
            if (soft > 0 && soft < 4096) {
                return new LoadGate.Limits(0.40f, 0.60f, 0.7f); // Aggressive weight, lower threshold
            }
            return new LoadGate.Limits(0.60f, 0.80f, 0.5f);
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
