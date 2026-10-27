package dev.makemygamerunwell.core;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HardwareChangeTest {
    private static final Hardware BEFORE = new Hardware(8, 3.6, 16384, 4096, Hardware.Gpu.DISCRETE, 1920, 1080, 60);

    private static Hardware with(int cores, long memory, long heap, Hardware.Gpu gpu, int width, int refresh) {
        return new Hardware(cores, 3.6, memory, heap, gpu, width, 1080, refresh);
    }

    @Test void anIdenticalMachineHasNoChange() {
        assertEquals(Set.of(), HardwareChange.between(BEFORE, BEFORE));
    }

    @Test void resizingTheWindowIsNotAHardwareChange() {
        assertEquals(Set.of(), HardwareChange.between(BEFORE, with(8, 16384, 4096, Hardware.Gpu.DISCRETE, 800, 60)));
    }

    @Test void everyAspectIsDetected() {
        assertEquals(EnumSet.allOf(HardwareChange.class),
                HardwareChange.between(BEFORE, with(4, 8192, 4096, Hardware.Gpu.INTEGRATED, 1920, 144)));
    }

    @Test void aMoreGenerousHeapCountsAsAMemoryChange() {
        assertEquals(Set.of(HardwareChange.MEMORY),
                HardwareChange.between(BEFORE, with(8, 16384, 8192, Hardware.Gpu.DISCRETE, 1920, 60)));
    }

    @Test void aFigureThatCouldNotBeReadNeverCountsAsAChange() {
        assertEquals(Set.of(), HardwareChange.between(BEFORE, with(8, 0, 0, Hardware.Gpu.UNKNOWN, 1920, 0)));
    }
}
