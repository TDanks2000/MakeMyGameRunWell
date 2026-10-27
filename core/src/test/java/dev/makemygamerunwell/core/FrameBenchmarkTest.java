package dev.makemygamerunwell.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameBenchmarkTest {
    private static final long MS = 1_000_000;
    private static final Hardware STRONG = new Hardware(8, 4, 32768, 8192, Hardware.Gpu.HIGH_END, 1920, 1080, 144);
    private static final TuningPlan HIGH = TuningPlan.forHardware(STRONG);
    private static final TuningPlan FLOOR = TuningPlan.forHardware(new Hardware(0, 0, 0, 0, Hardware.Gpu.UNKNOWN, 0, 0, 0));
    private static final Set<String> ALL = Arrays.stream(PlanAdjustment.values()).map(PlanAdjustment::option).collect(Collectors.toSet());

    private static FrameBenchmark benchmark(TuningPlan plan) {
        return new FrameBenchmark(plan, 32, TuningPreferences.DEFAULT, 60, Set.of());
    }

    private static List<TuningPlan> run(FrameBenchmark benchmark, ToLongFunction<TuningPlan> frameMillis) {
        var applied = new ArrayList<TuningPlan>();
        long now = 0;
        double progress = 0;
        while (!benchmark.done()) {
            TuningPlan candidate = benchmark.candidate();
            if (applied.isEmpty() || !applied.getLast().equals(candidate)) applied.add(candidate);
            now += frameMillis.applyAsLong(candidate) * MS;
            benchmark.onFrame(now, true);
            assertTrue(benchmark.progress() >= progress);
            progress = benchmark.progress();
            assertTrue(now < 300_000 * MS, "benchmark did not finish");
        }
        assertEquals(1, benchmark.progress());
        return applied;
    }

    @Test void fastMachineKeepsItsDetailAfterOneTrial() {
        var b = benchmark(HIGH);
        assertEquals(List.of(HIGH), run(b, p -> 5));
        assertEquals(HIGH, b.result());
        assertTrue(b.targetMet());
        assertEquals(200, b.resultStats().averageFps(), 0.001);
    }

    @Test void ineffectiveChangesAreRevertedAndEntityReductionCanPreserveClouds() {
        var b = benchmark(HIGH);
        run(b, p -> p.entityDistance() <= 1 ? 5 : 25);
        assertEquals(1, b.result().entityDistance());
        assertTrue(b.result().clouds());
        assertTrue(b.result().shadows());
        assertTrue(b.result().ambientOcclusion());
        assertEquals(HIGH.renderDistance(), b.result().renderDistance());
        assertTrue(b.targetMet());
    }

    @Test void usefulPartialImprovementsAreRetained() {
        var b = benchmark(HIGH);
        run(b, p -> p.renderDistance() + (p.clouds() ? 6 : 0));
        assertFalse(b.result().clouds());
        assertTrue(b.result().renderDistance() < HIGH.renderDistance());
        assertTrue(b.result().shadows());
        assertTrue(b.targetMet());
    }

    @Test void lowestHardwareProfileIsMeasuredAndCanMissTheTarget() {
        var b = benchmark(FLOOR);
        run(b, p -> 100);
        assertEquals(FLOOR, b.result());
        assertFalse(b.targetMet());
        assertTrue(b.resultStats().frames() > 0);
        assertEquals(10, b.resultStats().averageFps(), 0.001);
    }

    @Test void keptSettingsAreNeverProposedAsTrials() {
        var keep = Set.of("renderClouds", "renderDistance", "entityDistanceScaling");
        var b = new FrameBenchmark(HIGH, 32, TuningPreferences.DEFAULT, 60, keep);
        for (var plan : run(b, p -> 30)) {
            assertEquals(HIGH.clouds(), plan.clouds());
            assertEquals(HIGH.renderDistance(), plan.renderDistance());
            assertEquals(HIGH.entityDistance(), plan.entityDistance());
        }
    }

    @Test void withEveryAdjustmentKeptOnlyTheBaselineIsMeasured() {
        var b = new FrameBenchmark(HIGH, 32, TuningPreferences.DEFAULT, 60, ALL);
        assertEquals(List.of(HIGH), run(b, p -> 100));
        assertFalse(b.targetMet());
    }

    @Test void aHigherTargetRequiresMorePerformance() {
        var b = new FrameBenchmark(HIGH, 32, TuningPreferences.DEFAULT, 144, ALL);
        run(b, p -> 8);
        assertFalse(b.targetMet());
        var normal = new FrameBenchmark(HIGH, 32, TuningPreferences.DEFAULT, 60, ALL);
        run(normal, p -> 8);
        assertTrue(normal.targetMet());
    }

    @Test void beautifulAcceptsLessHeadroomThanSmoothAndQuiet() {
        for (var goal : TuningPreferences.Goal.values()) {
            var b = new FrameBenchmark(HIGH, 32, new TuningPreferences(goal, 60), 60, ALL);
            run(b, p -> 12);
            assertEquals(goal == TuningPreferences.Goal.BEAUTIFUL, b.targetMet());
        }
    }

    @Test void slowFramesCanFailAnOtherwiseFastAverage() {
        var b = new FrameBenchmark(HIGH, 32, TuningPreferences.DEFAULT, 60, ALL);
        long now = 0;
        for (int i = 0; !b.done(); i++) {
            now += (i % 10 == 9 ? 40 : 3) * MS;
            b.onFrame(now, true);
        }
        assertTrue(b.resultStats().averageFps() > 90);
        assertFalse(b.targetMet());
    }

    @Test void rebuildingFramesAreNotMeasured() {
        var b = benchmark(HIGH);
        long now = 0;
        while (!b.done()) {
            boolean rebuilding = now < 3_000 * MS;
            now += (rebuilding ? 100 : 5) * MS;
            b.onFrame(now, !rebuilding);
        }
        assertTrue(b.targetMet());
        assertEquals(200, b.resultStats().averageFps(), 0.001);
    }

    @Test void aRendererThatNeverSettlesStillFinishes() {
        var b = benchmark(HIGH);
        long now = 0;
        while (!b.done()) {
            now += 5 * MS;
            b.onFrame(now, false);
            assertTrue(now < 20_000 * MS);
        }
        assertTrue(b.targetMet());
    }

    @Test void unloadedDistanceNeedsExtraHeadroomUnlessRenderDistanceIsKept() {
        var b = new FrameBenchmark(HIGH, 5, TuningPreferences.DEFAULT, 60, ALL.stream()
                .filter(key -> !key.equals("renderDistance")).collect(Collectors.toSet()));
        run(b, p -> 8);
        assertTrue(b.result().renderDistance() < HIGH.renderDistance());
        var kept = new FrameBenchmark(HIGH, 5, TuningPreferences.DEFAULT, 60, ALL);
        run(kept, p -> 8);
        assertTrue(kept.targetMet());
        assertEquals(HIGH, kept.result());
    }

    @Test void aNegativeMonotonicClockOriginDoesNotPreventSettling() {
        var b = benchmark(HIGH);
        long now = -10_000 * MS;
        for (int i = 0; !b.done() && i < 2_000; i++) {
            now += 5 * MS;
            b.onFrame(now, true);
        }
        assertTrue(b.done());
        assertTrue(b.targetMet());
    }

    @Test void measuredFramesAndProgressAreStableAfterCompletion() {
        var b = benchmark(HIGH);
        run(b, p -> 5);
        FrameStats stats = b.resultStats();
        assertTrue(stats.frames() >= 60);
        b.onFrame(Long.MAX_VALUE / 2, true);
        assertEquals(stats, b.resultStats());
        assertEquals(1, b.progress());
    }
}
