package dev.makemygamerunwell.core;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModConfigTest {

    private static ModConfig config(boolean benchmark, Optional<TuningPlan.Tier> forced, Set<String> keep) {
        var limits = new TuningLimits(forced, TuningPlan.Tier.HIGH, 0, 0, 0, Optional.empty());
        return new ModConfig(true, benchmark, limits, keep, TuningPreferences.DEFAULT);
    }

    @Test void byDefaultAnyMachineAboveTheFloorIsBenchmarked() {
        assertTrue(ModConfig.DEFAULT.benchmarks());
    }

    @Test void theConfigurationCanTurnTheBenchmarkOff() {
        var off = config(false, Optional.empty(), Set.of());
        assertFalse(off.benchmarks());
    }

    @Test void aForcedProfileIsNeverBenchmarked() {
        var forced = config(true, Optional.of(TuningPlan.Tier.HIGH), Set.of());
        assertFalse(forced.benchmarks());
    }

    @Test void keepingAFrameRateCapMeansTheFrameRateCannotBeMeasured() {
        for (String kept : new String[]{"maxFps", "enableVsync"}) {
            assertFalse(config(true, Optional.empty(), Set.of(kept)).benchmarks(), kept);
        }
        assertTrue(config(true, Optional.empty(), Set.of("renderClouds", "renderDistance")).benchmarks());
    }

    @Test void aForcedProfileLeavesNoOptionToTheMod() {
        Set<String> all = Set.of("renderDistance", "maxFps", "ao");
        assertEquals(all, config(true, Optional.of(TuningPlan.Tier.LOW), Set.of("ao")).untouchable(all));
        assertEquals(Set.of("ao"), config(true, Optional.empty(), Set.of("ao")).untouchable(all));
    }
}
