package dev.makemygamerunwell.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineSpecsTest {
    @Test void reportedValuesPassThrough() {
        var specs = MachineSpecs.sanitize(8, 3_600_000_000L, 32768, 16, 0);
        assertEquals(new MachineSpecs(8, 3.6, 32768), specs);
    }

    @Test void unknownCoresAssumeTwoThreadsPerCore() {
        assertEquals(4, MachineSpecs.sanitize(0, 0, 8192, 8, 0).physicalCores());
        assertEquals(4, MachineSpecs.sanitize(-1, 0, 8192, 8, 0).physicalCores());
    }

    @Test void atLeastOneCoreEvenOnASingleThread() {
        assertEquals(1, MachineSpecs.sanitize(0, 0, 8192, 1, 0).physicalCores());
        assertEquals(1, MachineSpecs.sanitize(0, 0, 8192, 0, 0).physicalCores());
    }

    @Test void unknownOrAbsurdClockSpeedIsReportedAsZero() {
        assertEquals(0, MachineSpecs.sanitize(4, -1, 8192, 8, 0).cpuGhz());
        assertEquals(0, MachineSpecs.sanitize(4, 0, 8192, 8, 0).cpuGhz());
        assertEquals(0, MachineSpecs.sanitize(4, 3_600_000_000_000L, 8192, 8, 0).cpuGhz());
    }

    @Test void memoryFallsBackToTheJvmFigureThenToZero() {
        assertEquals(16384, MachineSpecs.sanitize(4, 0, 0, 8, 16384).memoryMiB());
        assertEquals(8192, MachineSpecs.sanitize(4, 0, 8192, 8, 16384).memoryMiB());
        assertEquals(0, MachineSpecs.sanitize(4, 0, -1, 8, -1).memoryMiB());
    }
}
