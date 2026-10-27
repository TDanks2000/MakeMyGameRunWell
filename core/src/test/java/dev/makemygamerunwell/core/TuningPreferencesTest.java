package dev.makemygamerunwell.core;

import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TuningPreferencesTest {
    @Test void displayTargetHandlesUnknownAndExtremeRefreshRates() {
        var p = new TuningPreferences(TuningPreferences.Goal.SMOOTH, 0);
        assertEquals(60, p.target(0, 0));
        assertEquals(60, p.target(-1, 0));
        assertEquals(30, p.target(20, 0));
        assertEquals(144, p.target(144, 0));
        assertEquals(240, p.target(500, 0));
        assertEquals(150, p.frameCap(144, 0));
    }

    @Test void explicitTargetAndExistingOverrideHaveDefinedPrecedence() {
        var p = new TuningPreferences(TuningPreferences.Goal.QUIET, 144);
        assertEquals(144, p.target(60, 0));
        assertEquals(150, p.frameCap(60, 0));
        assertEquals(90, p.target(144, 90));
        assertEquals(90, p.frameCap(144, 90));
        assertEquals(260, p.frameCap(144, 260));
    }

    @Test void malformedPreferencesCannotEnterThePolicy() {
        for (int value : new int[]{-1, 1, 29, 241, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new TuningPreferences(TuningPreferences.Goal.SMOOTH, value));
        }
        assertThrows(IllegalArgumentException.class, () -> new TuningPreferences(null, 60));
    }

    @Test void leafTrialsDoNotChangeVignetteOrTextureQuality() {
        var h = new Hardware(8, 4, 32768, 8192, Hardware.Gpu.HIGH_END, 1920, 1080, 144);
        var initial = TuningPlan.forHardware(h);
        var lower = PlanAdjustment.LEAVES.lower(initial);
        assertFalse(lower.fancy());
        assertEquals(initial.vignette(), lower.vignette());
        assertEquals(initial.mipmapLevels(), lower.mipmapLevels());
    }

    @Test void reductionsStayWithinRangesAndNeverRaiseQuality() {
        for (var tier : TuningPlan.Tier.values()) {
            var h = new Hardware(8, 4, 32768, 8192, Hardware.Gpu.HIGH_END, 1920, 1080, 144);
            var p = TuningPlan.forHardware(h, new TuningLimits(Optional.of(tier), tier, 0, 0, 0, Optional.empty()));
            for (var step : PlanAdjustment.values()) {
                var reduced = p;
                for (int i = 0; i < 40; i++) reduced = step.lower(reduced);
                assertTrue(reduced.renderDistance() >= 2 && reduced.renderDistance() <= p.renderDistance());
                assertTrue(reduced.simulationDistance() >= 5 && reduced.simulationDistance() <= p.simulationDistance());
                assertTrue(reduced.entityDistance() >= 0.5 && reduced.entityDistance() <= p.entityDistance());
                assertEquals(p.fpsLimit(), reduced.fpsLimit());
                assertEquals(reduced, step.lower(reduced));
            }
        }
    }

    @Test void goalsOfferDifferentFirstTrials() {
        assertEquals(PlanAdjustment.CLOUDS, SelectiveTuning.adjustments(TuningPreferences.DEFAULT, Set.of()).getFirst());
        assertEquals(PlanAdjustment.ENTITY_DISTANCE, SelectiveTuning.adjustments(
                new TuningPreferences(TuningPreferences.Goal.BEAUTIFUL, 60), Set.of()).getFirst());
    }
}
