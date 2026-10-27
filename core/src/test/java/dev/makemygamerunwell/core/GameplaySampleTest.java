package dev.makemygamerunwell.core;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameplaySampleTest {
    private static final long MS = 1_000_000;
    private static final TuningPlan CURRENT = TuningPlan.forHardware(
            new Hardware(8, 4, 32768, 8192, Hardware.Gpu.HIGH_END, 1920, 1080, 144));

    @Test void inactiveTimeAndResumeGapAreExcluded() {
        var sample = new GameplaySample(100 * MS);
        sample.onFrame(0, true);
        sample.onFrame(10 * MS, true);
        sample.onFrame(20 * MS, false);
        sample.onFrame(10_000 * MS, false);
        sample.onFrame(20_000 * MS, true);
        assertEquals(0.1, sample.progress(), 0.00001);
        sample.onFrame(20_010 * MS, true);
        assertEquals(0.2, sample.progress(), 0.00001);
        assertEquals(100, sample.stats().averageFps(), 0.00001);
    }

    @Test void aNegativeMonotonicClockOriginStillMeasuresFrames() {
        var sample = new GameplaySample(100 * MS);
        for (int i = 0; i <= 10; i++) sample.onFrame((-200 + i * 10) * MS, true);
        assertTrue(sample.done());
        assertEquals(100, sample.stats().averageFps(), 0.001);
    }

    @Test void completionKeepsAStableMeasuredResult() {
        var sample = new GameplaySample(100 * MS);
        for (int i = 0; i <= 10; i++) sample.onFrame(i * 10 * MS, true);
        assertTrue(sample.done());
        var stats = sample.stats();
        sample.onFrame(1_000 * MS, true);
        assertEquals(stats, sample.stats());
        assertEquals(1, sample.progress());
    }

    @Test void aLongGameplayStallCountsAgainstSmoothness() {
        var sample = new GameplaySample(1_000 * MS);
        sample.onFrame(0, true);
        sample.onFrame(2_000 * MS, true);
        assertTrue(sample.done());
        assertTrue(sample.stats().averageFps() < 1);
    }

    @Test void aCappedSampleDoesNotRecommendRemovingDetail() {
        var advice = GameplayAdvice.forSample(new FrameStats(300, 60, 59), CURRENT,
                TuningPreferences.DEFAULT, 144, 60, Set.of());
        assertEquals(GameplayAdvice.Status.CAPPED, advice.status());
        assertTrue(advice.adjustment().isEmpty());
    }

    @Test void aSmoothSampleDoesNotRecommendAnyChange() {
        var advice = GameplayAdvice.forSample(new FrameStats(300, 60, 58), CURRENT,
                TuningPreferences.DEFAULT, 60, 60, Set.of());
        assertEquals(GameplayAdvice.Status.SMOOTH, advice.status());
        assertTrue(advice.adjustment().isEmpty());
    }

    @Test void poorGameplayOffersOnlyOneUnkeptReduction() {
        var advice = GameplayAdvice.forSample(new FrameStats(300, 30, 20), CURRENT,
                TuningPreferences.DEFAULT, 60, 0, Set.of("renderClouds"));
        assertEquals(GameplayAdvice.Status.TRY_REDUCTION, advice.status());
        assertEquals(PlanAdjustment.SHADOWS, advice.adjustment().orElseThrow());
    }

    @Test void keepingAllSupportedReductionsReportsTheFloor() {
        var keep = Arrays.stream(PlanAdjustment.values()).map(PlanAdjustment::option).collect(Collectors.toSet());
        var advice = GameplayAdvice.forSample(new FrameStats(300, 30, 20), CURRENT,
                TuningPreferences.DEFAULT, 60, 0, keep);
        assertEquals(GameplayAdvice.Status.AT_FLOOR, advice.status());
        assertTrue(advice.adjustment().isEmpty());
    }
}
