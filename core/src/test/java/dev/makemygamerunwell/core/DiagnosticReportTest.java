package dev.makemygamerunwell.core;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticReportTest {
    private static final Hardware HARDWARE = new Hardware(8, 3.6, 16384, 2048, Hardware.Gpu.DISCRETE, 1920, 1080, 144);

    @Test void reportsHardwareAdviceAndChanges() {
        String text = DiagnosticReport.format(List.of("Minecraft 26.2"), HARDWARE, "RTX 2080 SUPER", ModConfig.DEFAULT,
                new HeapAdvice(HeapAdvice.Status.TOO_SMALL, 4096), Set.of(HardwareChange.GRAPHICS),
                List.of(new SettingChange("renderDistance", "23", "8")));
        for (String expected : List.of("Minecraft 26.2", "8 cores, 3.6 GHz", "2048 MiB available to Minecraft (TOO_SMALL, suggested 4096 MiB)",
                "RTX 2080 SUPER (DISCRETE)", "1920x1080 at 144 Hz", "Changed since the last tune: [GRAPHICS]", "renderDistance: 23 -> 8")) {
            assertTrue(text.contains(expected), expected + " missing from:\n" + text);
        }
    }

    @Test void saysSoWhenNothingWasChanged() {
        String text = DiagnosticReport.format(List.of(), HARDWARE, "unknown", ModConfig.DEFAULT, HeapAdvice.OK, Set.of(), List.of());
        assertTrue(text.contains("== Settings changed by the mod ==\nnone\n"));
        assertEquals(-1, text.indexOf("Changed since the last tune"));
    }
}
