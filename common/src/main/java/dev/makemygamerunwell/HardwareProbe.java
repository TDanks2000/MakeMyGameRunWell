package dev.makemygamerunwell;

import com.sun.management.OperatingSystemMXBean;
import dev.makemygamerunwell.core.GpuClassifier;
import dev.makemygamerunwell.core.Hardware;
import dev.makemygamerunwell.core.MachineSpecs;
import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;

final class HardwareProbe {
    private static final Logger LOG = LoggerFactory.getLogger(HardwareProbe.class);
    private static final long MIB = 1048576;
    /** How long a click may wait for OSHI before using the JDK's own numbers instead. */
    private static final long PROBE_TIMEOUT_SECONDS = 3;

    private static CompletableFuture<MachineSpecs> machine;
    private static boolean timedOut;

    private HardwareProbe() {}

    /** Starts the slow CPU/RAM query off-thread so the button click does not stall the game. */
    static void prefetch() {
        machine();
    }

    private static synchronized CompletableFuture<MachineSpecs> machine() {
        if (machine == null) {
            machine = CompletableFuture.supplyAsync(HardwareProbe::readMachine, task -> {
                Thread thread = new Thread(task, "MMGRW hardware probe");
                thread.setDaemon(true);
                thread.start();
            });
        }
        return machine;
    }

    static Hardware detect(Minecraft mc) {
        MachineSpecs m = awaitMachine();
        var window = mc.getWindow();
        return new Hardware(m.physicalCores(), m.cpuGhz(), m.memoryMiB(), Runtime.getRuntime().maxMemory() / MIB,
                detectGpu(), window.getWidth(), window.getHeight(), window.getRefreshRate());
    }

    /** The hardware if the background query has finished, so a screen being built never waits for it. */
    static Optional<Hardware> detectIfReady(Minecraft mc) {
        CompletableFuture<MachineSpecs> query = machine();
        return query.isDone() && !query.isCompletedExceptionally() ? Optional.of(detect(mc)) : Optional.empty();
    }

    /** The graphics device's own name, for reports; the classification used for tuning is separate. */
    static String gpuName() {
        try {
            return GameCompat.gpu().name();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    /**
     * Never blocks the render thread for long: a hung or failed probe degrades to the JDK's numbers. After one
     * timeout later calls do not wait again, but use the real figures once the query has finished.
     */
    private static MachineSpecs awaitMachine() {
        CompletableFuture<MachineSpecs> query = machine();
        try {
            MachineSpecs specs = timedOut ? query.getNow(null) : query.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (specs != null) return specs;
        } catch (TimeoutException e) {
            timedOut = true;
            LOG.warn("Hardware query took over {}s; using the JVM's own CPU and memory figures", PROBE_TIMEOUT_SECONDS);
        } catch (ExecutionException e) {
            LOG.warn("Hardware query failed; using the JVM's own CPU and memory figures", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return MachineSpecs.sanitize(0, 0, 0, Runtime.getRuntime().availableProcessors(), jvmMemoryMiB());
    }

    private static MachineSpecs readMachine() {
        long cores = 0;
        long hertz = 0;
        long memory = 0;
        try {
            var hardware = new SystemInfo().getHardware();
            var cpu = hardware.getProcessor();
            cores = cpu.getPhysicalProcessorCount();
            hertz = cpu.getMaxFreq();
            memory = hardware.getMemory().getTotal() / MIB;
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Could not read CPU and memory details; using the JVM's own figures", e);
        }
        return MachineSpecs.sanitize(cores, hertz, memory, Runtime.getRuntime().availableProcessors(), jvmMemoryMiB());
    }

    private static long jvmMemoryMiB() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os
                ? os.getTotalMemorySize() / MIB : 0;
    }

    private static Hardware.Gpu detectGpu() {
        try {
            GpuInfo gpu = GameCompat.gpu();
            return GpuClassifier.classify(gpu.type(), gpu.name());
        } catch (RuntimeException e) {
            LOG.warn("Could not identify the graphics device; assuming the most conservative profile", e);
            return Hardware.Gpu.UNKNOWN;
        }
    }
}
