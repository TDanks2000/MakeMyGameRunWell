package dev.makemygamerunwell.core;

import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TuningPlanTest {
    private Hardware machine(Hardware.Gpu gpu, long heap, int width, int height) {
        return new Hardware(8, 4.0, 32768, heap, gpu, width, height, 144);
    }

    @Test void unknownHardwareFallsBackSafely() {
        var p = TuningPlan.forHardware(new Hardware(0, 0, 0, 0, Hardware.Gpu.UNKNOWN, 0, 0, 0));
        assertEquals(5, p.renderDistance());
        assertEquals(60, p.fpsLimit());
        assertFalse(p.fancy());
        assertFalse(p.clouds());
        assertEquals(TuningPlan.Tier.MINIMAL, p.tier());
    }

    @Test void heapConstrainsEvenHighEndMachines() {
        var p = TuningPlan.forHardware(machine(Hardware.Gpu.HIGH_END, 1024, 1920, 1080));
        assertEquals(5, p.renderDistance());
        assertFalse(p.ambientOcclusion());
        assertEquals(TuningPlan.Limit.MEMORY, p.limit());
    }

    @Test void resolutionReducesGpuBudget() {
        var low = TuningPlan.forHardware(machine(Hardware.Gpu.DISCRETE, 8192, 1920, 1080));
        var high = TuningPlan.forHardware(machine(Hardware.Gpu.DISCRETE, 8192, 3840, 2160));
        assertTrue(high.renderDistance() < low.renderDistance());
        assertEquals(TuningPlan.Limit.GPU, high.limit());
    }

    @Test void highEndGpuIsNotHeldBackByFourK() {
        var p = TuningPlan.forHardware(machine(Hardware.Gpu.HIGH_END, 8192, 3840, 2160));
        assertEquals(TuningPlan.Tier.HIGH, p.tier());
        assertEquals(TuningPlan.Limit.NONE, p.limit());
    }

    @Test void cpuConstrainsSimulationAndRendering() {
        var p = TuningPlan.forHardware(new Hardware(2, 2, 32768, 8192, Hardware.Gpu.HIGH_END, 1920, 1080, 60));
        assertEquals(8, p.renderDistance());
        assertEquals(5, p.simulationDistance());
        assertEquals(60, p.fpsLimit());
        assertEquals(TuningPlan.Limit.CPU, p.limit());
    }

    @Test void softwareRendererNeverGetsFancySettings() {
        var p = TuningPlan.forHardware(machine(Hardware.Gpu.SOFTWARE, 8192, 1920, 1080));
        assertEquals(5, p.renderDistance());
        assertFalse(p.shadows());
    }

    @Test void unconstrainedMachineReachesTopTierWithNoLimit() {
        var p = TuningPlan.forHardware(machine(Hardware.Gpu.HIGH_END, 8192, 1920, 1080));
        assertEquals(TuningPlan.Tier.HIGH, p.tier());
        assertEquals(TuningPlan.Limit.NONE, p.limit());
        assertEquals(16, p.renderDistance());
        assertTrue(p.clouds() && p.shadows());
        assertEquals(60, p.fpsLimit());
    }

    @Test void preferencesChooseTheCapAtEveryHardwareTierAndOverridesStillWin() {
        for (var gpu : Hardware.Gpu.values()) {
            for (int target : new int[]{0, 30, 60, 144, 240}) {
                var h = machine(gpu, 8192, 1920, 1080);
                var preferences = new TuningPreferences(TuningPreferences.Goal.QUIET, target);
                var plan = TuningPlan.forHardware(h, TuningLimits.NONE, preferences);
                assertEquals(preferences.frameCap(h.refreshRate(), 0), plan.fpsLimit());
                assertEquals(TuningPlan.forHardware(h).tier(), plan.tier());
                var capped = TuningPlan.forHardware(h, limits(Optional.empty(), TuningPlan.Tier.HIGH, 6, 5, 90, Optional.empty()), preferences);
                assertEquals(90, capped.fpsLimit());
                assertTrue(capped.renderDistance() <= 6 && capped.simulationDistance() <= 5);
            }
        }
    }

    @Test void unknownClockSpeedCannotReachTopTierButIsNotTreatedAsSlow() {
        var p = TuningPlan.forHardware(new Hardware(16, -1, 32768, 8192, Hardware.Gpu.HIGH_END, 1920, 1080, 144));
        assertEquals(TuningPlan.Tier.MEDIUM, p.tier());
        assertEquals(TuningPlan.Limit.CPU, p.limit());
    }

    @Test void memoryIsReportedAsTheLimitBeforeOtherTiedComponents() {
        var p = TuningPlan.forHardware(new Hardware(2, 2, 4096, 1536, Hardware.Gpu.INTEGRATED, 1920, 1080, 60));
        assertEquals(TuningPlan.Tier.LOW, p.tier());
        assertEquals(TuningPlan.Limit.MEMORY, p.limit());
    }

    @Test void plansStayInsideVanillaRangesAcrossHardwareCombinations() {
        for (var gpu : Hardware.Gpu.values())
            for (int cores : new int[]{1, 2, 4, 8, 32})
                for (long heap : new long[]{512, 1536, 3072, 6144, 32768})
                    for (int width : new int[]{800, 1920, 3840, 7680}) {
                        var p = TuningPlan.forHardware(new Hardware(cores, 3.5, 32768, heap, gpu, width, width * 9 / 16, 165));
                        assertTrue(p.renderDistance() >= 2 && p.renderDistance() <= 32);
                        assertTrue(p.simulationDistance() >= 5 && p.simulationDistance() <= p.renderDistance());
                        assertTrue(p.entityDistance() >= 0.5 && p.entityDistance() <= 5);
                        assertTrue(p.fpsLimit() >= 60 && p.fpsLimit() <= 144 && p.fpsLimit() % 10 == 0);
                        assertTrue(p.cloudRange() >= 2 && p.cloudRange() <= 128);
                        assertTrue(p.biomeBlend() >= 0 && p.biomeBlend() <= 7);
                        assertTrue(p.mipmapLevels() >= 0 && p.mipmapLevels() <= 4);
                    }
    }

    private static final Hardware STRONG = new Hardware(8, 4.0, 32768, 8192, Hardware.Gpu.HIGH_END, 1920, 1080, 144);

    private static TuningLimits limits(Optional<TuningPlan.Tier> forced, TuningPlan.Tier max, int render, int simulation,
                                       int fps, Optional<Hardware.Gpu> gpu) {
        return new TuningLimits(forced, max, render, simulation, fps, gpu);
    }

    @Test void noLimitsChangeNothing() {
        var weakGpu = new Hardware(8, 4.0, 32768, 8192, Hardware.Gpu.DISCRETE, 1920, 1080, 144);
        assertEquals(TuningPlan.forHardware(weakGpu), TuningPlan.forHardware(weakGpu, TuningLimits.NONE));
        assertEquals(12, TuningPlan.forHardware(weakGpu).renderDistance());
        assertEquals(8, TuningPlan.forHardware(weakGpu).simulationDistance());
    }

    @Test void aProfileCapLowersTheChoiceAndIsReportedAsTheConfiguration() {
        var p = TuningPlan.forHardware(STRONG, limits(Optional.empty(), TuningPlan.Tier.LOW, 0, 0, 0, Optional.empty()));
        assertEquals(TuningPlan.Tier.LOW, p.tier());
        assertEquals(TuningPlan.Limit.CONFIG, p.limit());
        assertEquals(8, p.renderDistance());
        assertEquals(5, p.simulationDistance());
    }

    @Test void aProfileCapAboveWhatTheHardwareAllowsChangesNothing() {
        var p = TuningPlan.forHardware(machine(Hardware.Gpu.INTEGRATED, 8192, 1920, 1080),
                limits(Optional.empty(), TuningPlan.Tier.HIGH, 0, 0, 0, Optional.empty()));
        assertEquals(TuningPlan.Limit.GPU, p.limit());
    }

    @Test void aForcedProfileIgnoresTheHardware() {
        var weak = new Hardware(1, 1.5, 2048, 1024, Hardware.Gpu.SOFTWARE, 1920, 1080, 60);
        var p = TuningPlan.forHardware(weak, limits(Optional.of(TuningPlan.Tier.HIGH), TuningPlan.Tier.MINIMAL, 0, 0, 0, Optional.empty()));
        assertEquals(TuningPlan.Tier.HIGH, p.tier());
        assertEquals(TuningPlan.Limit.NONE, p.limit());
        assertEquals(16, p.renderDistance());
        assertEquals(8, p.simulationDistance());
    }

    @Test void distanceCapsApplyButNeverGoBelowMinecraftsMinimums() {
        var p = TuningPlan.forHardware(STRONG, limits(Optional.empty(), TuningPlan.Tier.HIGH, 10, 6, 0, Optional.empty()));
        assertEquals(10, p.renderDistance());
        assertEquals(6, p.simulationDistance());
        var tiny = TuningPlan.forHardware(STRONG, limits(Optional.empty(), TuningPlan.Tier.HIGH, 3, 1, 0, Optional.empty()));
        assertEquals(3, tiny.renderDistance());
        assertEquals(5, tiny.simulationDistance());
        assertEquals(6, tiny.cloudRange());
    }

    @Test void aCapNeverRaisesADistance() {
        var p = TuningPlan.forHardware(new Hardware(0, 0, 0, 0, Hardware.Gpu.UNKNOWN, 0, 0, 0),
                limits(Optional.empty(), TuningPlan.Tier.HIGH, 32, 32, 0, Optional.empty()));
        assertEquals(5, p.renderDistance());
        assertEquals(5, p.simulationDistance());
    }

    @Test void anFpsLimitReplacesTheAutomaticOneAndIsRoundedToTenths() {
        assertEquals(90, TuningPlan.forHardware(STRONG, limits(Optional.empty(), TuningPlan.Tier.HIGH, 0, 0, 90, Optional.empty())).fpsLimit());
        assertEquals(100, TuningPlan.forHardware(STRONG, limits(Optional.empty(), TuningPlan.Tier.HIGH, 0, 0, 97, Optional.empty())).fpsLimit());
        assertEquals(260, TuningPlan.forHardware(STRONG, limits(Optional.empty(), TuningPlan.Tier.HIGH, 0, 0, 260, Optional.empty())).fpsLimit());
        assertEquals(10, TuningPlan.forHardware(STRONG, limits(Optional.empty(), TuningPlan.Tier.HIGH, 0, 0, 10, Optional.empty())).fpsLimit());
    }

    @Test void aGpuClassOverrideReplacesDetection() {
        var misdetected = new Hardware(8, 4.0, 32768, 8192, Hardware.Gpu.UNKNOWN, 1920, 1080, 144);
        assertEquals(TuningPlan.Tier.MINIMAL, TuningPlan.forHardware(misdetected).tier());
        var p = TuningPlan.forHardware(misdetected, limits(Optional.empty(), TuningPlan.Tier.HIGH, 0, 0, 0, Optional.of(Hardware.Gpu.HIGH_END)));
        assertEquals(TuningPlan.Tier.HIGH, p.tier());
    }

    @Test void plansWithLimitsStayInsideVanillaRanges() {
        for (var forced : List.of(Optional.<TuningPlan.Tier>empty(), Optional.of(TuningPlan.Tier.MINIMAL), Optional.of(TuningPlan.Tier.HIGH)))
            for (var max : TuningPlan.Tier.values())
                for (int render : new int[]{0, 2, 3, 12, 32})
                    for (int simulation : new int[]{0, 5, 9, 32})
                        for (int fps : new int[]{0, 10, 55, 260}) {
                            var limits = limits(forced, max, render, simulation, fps, Optional.empty());
                            var p = TuningPlan.forHardware(STRONG, limits);
                            assertTrue(p.renderDistance() >= 2 && p.renderDistance() <= 32);
                            assertTrue(p.simulationDistance() >= 5 && p.simulationDistance() <= 32);
                            assertTrue(p.fpsLimit() >= 10 && p.fpsLimit() <= 260 && p.fpsLimit() % 10 == 0);
                            assertTrue(p.cloudRange() >= 2 && p.cloudRange() <= 128);
                        }
    }
}
