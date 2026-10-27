package dev.makemygamerunwell.core;

import java.util.List;
import java.util.Set;

/** The plain-text report a player can paste into a bug report. Contains no personal data beyond hardware names. */
public final class DiagnosticReport {
    private DiagnosticReport() {}

    /**
     * @param environment game, loader and Java versions, one per line
     * @param gpuName the graphics device's own name, as reported by the driver
     */
    public static String format(List<String> environment, Hardware hardware, String gpuName, ModConfig config,
                                HeapAdvice heap, Set<HardwareChange> sinceLastTune, List<SettingChange> changes) {
        StringBuilder text = new StringBuilder("Make My Game Run Well report\n");
        section(text, "Environment");
        environment.forEach(line -> text.append(line).append('\n'));
        section(text, "Hardware");
        text.append("CPU: ").append(hardware.physicalCores()).append(" cores, ")
                .append(hardware.cpuGhz() > 0 ? hardware.cpuGhz() + " GHz" : "clock unknown").append('\n')
                .append("Memory: ").append(hardware.memoryMiB()).append(" MiB installed, ")
                .append(hardware.heapMiB()).append(" MiB available to Minecraft (").append(heap.status());
        if (heap.status() != HeapAdvice.Status.OK) text.append(", suggested ").append(heap.suggestedMiB()).append(" MiB");
        text.append(")\n")
                .append("Graphics: ").append(gpuName).append(" (").append(hardware.gpu()).append(")\n")
                .append("Display: ").append(hardware.width()).append('x').append(hardware.height())
                .append(" at ").append(hardware.refreshRate()).append(" Hz\n");
        if (!sinceLastTune.isEmpty()) text.append("Changed since the last tune: ").append(sinceLastTune).append('\n');
        section(text, "Configuration");
        text.append(config).append('\n');
        section(text, "Settings changed by the mod");
        if (changes.isEmpty()) text.append("none\n");
        changes.forEach(change -> text.append(change.option()).append(": ").append(change.original())
                .append(" -> ").append(change.current()).append('\n'));
        return text.toString();
    }

    private static void section(StringBuilder text, String title) {
        text.append("\n== ").append(title).append(" ==\n");
    }
}
