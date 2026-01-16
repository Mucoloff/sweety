package dev.sweety.netty.loadbalancer.backend;

import dev.sweety.netty.loadbalancer.common.metrics.EMA;
import dev.sweety.netty.loadbalancer.common.metrics.state.LoadGate;
import dev.sweety.netty.loadbalancer.common.metrics.state.NodeState;
import dev.sweety.netty.loadbalancer.common.metrics.SmoothedLoad;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

public class MetricSampler {

    private final SystemInfo si = new SystemInfo();
    private final HardwareAbstractionLayer hw = si.getHardware();
    private final OperatingSystem os = si.getOperatingSystem();

    private final CentralProcessor cpu = hw.getProcessor();
    private final GlobalMemory mem = hw.getMemory();

    private OSProcess prevProcess;
    private long[] prevCpuTicks;

    // smoothing
    private final EMA cpuEma = new EMA(0.35f);
    private final EMA ramEma = new EMA(0.25f);
    private final EMA totCpuEma = new EMA(0.75f);
    private final EMA totRamEma = new EMA(0.45f);
    private final LoadGate gate = new LoadGate();

    public MetricSampler() {
        this.prevProcess = os.getCurrentProcess();
        this.prevCpuTicks = cpu.getSystemCpuLoadTicks();
    }

    public void reset() {
        this.prevProcess = os.getCurrentProcess();
        this.prevCpuTicks = cpu.getSystemCpuLoadTicks();
        this.cpuEma.reset();
        this.ramEma.reset();
        this.totCpuEma.reset();
        this.totRamEma.reset();
        this.gate.reset();
        lastOverrideTime = 0L;
    }

    private long lastOverrideTime = 0;
    private static final long OVERRIDE_COOLDOWN_MS = 5000;

    public SmoothedLoad sample() {
        // ===== PROCESSO =====
        OSProcess currProcess = os.getProcess(prevProcess.getProcessID());

        float cpuProcRaw =
                (float) currProcess.getProcessCpuLoadBetweenTicks(prevProcess);

        float cpuProcNorm =
                cpuProcRaw / cpu.getLogicalProcessorCount(); // 0–1

        long rss = currProcess.getResidentSetSize();
        float ramProcNorm =
                (float) rss / mem.getTotal();

        // ===== SISTEMA =====
        long[] currCpuTicks = cpu.getSystemCpuLoadTicks();
        float cpuSystemNorm =
                (float) cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);

        float ramSystemNorm =
                1f - ((float) mem.getAvailable() / mem.getTotal());


        // ===== UPDATE SNAPSHOT =====
        prevProcess = currProcess;
        prevCpuTicks = currCpuTicks;

        // ===== EMA =====
        float cpuSmooth = cpuEma.update(cpuProcNorm);
        float ramSmooth = ramEma.update(ramProcNorm);
        float totCpuSmooth = totCpuEma.update(cpuSystemNorm);
        float totRamSmooth = totRamEma.update(ramSystemNorm);

        NodeState state = gate.update(cpuSmooth, ramSmooth);

        NodeState finalState;
        if (totCpuSmooth > 0.85f || totRamSmooth > 0.90f) {
            if (System.currentTimeMillis() - lastOverrideTime > OVERRIDE_COOLDOWN_MS) {
                lastOverrideTime = System.currentTimeMillis();
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
                cpuSystemNorm,
                ramSystemNorm,
                finalState
        );
    }

}
