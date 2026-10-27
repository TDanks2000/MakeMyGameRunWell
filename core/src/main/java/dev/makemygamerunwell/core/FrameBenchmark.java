package dev.makemygamerunwell.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Measures a baseline, then reversible single-setting trials. Retains only measurable improvements. */
public final class FrameBenchmark {
    private static final long SECOND = 1_000_000_000L;
    private static final long MIN_SETTLE_NANOS = SECOND;
    private static final long MAX_SETTLE_NANOS = 8 * SECOND;
    private static final long SAMPLE_NANOS = 5 * SECOND / 2;
    private static final int MAX_SAMPLES = 20_000;
    private static final int MAX_TRIALS = 24;
    private static final double MAX_UNLOADED_DISTANCE_SCALE = 2;
    private static final double SETTLE_SHARE = 0.4;

    private final int loadedDistance;
    private final int target;
    private final TuningPreferences preferences;
    private final boolean keepRenderDistance;
    private final List<PlanAdjustment> adjustments;
    private final Set<TuningPlan> tested = new HashSet<>();
    private final long[] intervals = new long[MAX_SAMPLES];
    private TuningPlan candidate;
    private TuningPlan best;
    private FrameStats bestStats = FrameStats.NONE;
    private boolean settling = true;
    private long phaseStart;
    private boolean phaseStarted;
    private long lastFrame;
    private int count;
    private int trials;
    private double progress;
    private boolean done;

    public FrameBenchmark(TuningPlan suggested, int loadedDistance, TuningPreferences preferences, int target,
                          Set<String> keep) {
        this.candidate = suggested;
        this.best = suggested;
        this.loadedDistance = loadedDistance;
        this.preferences = preferences;
        this.target = target;
        this.keepRenderDistance = keep.contains("renderDistance");
        this.adjustments = SelectiveTuning.adjustments(preferences, keep);
    }

    public TuningPlan candidate() { return candidate; }
    public boolean done() { return done; }
    public TuningPlan result() { return best; }
    public FrameStats resultStats() { return bestStats; }
    public boolean targetMet() { return passes(bestStats, best); }
    public double progress() { return done ? 1 : progress; }

    public void onFrame(long nowNanos, boolean settled) {
        if (done) return;
        if (!phaseStarted) {
            phaseStart = nowNanos;
            phaseStarted = true;
        }
        long elapsed = nowNanos - phaseStart;
        if (settling) {
            if (elapsed >= MAX_SETTLE_NANOS || (elapsed >= MIN_SETTLE_NANOS && settled)) {
                settling = false;
                phaseStart = nowNanos;
                lastFrame = nowNanos;
                count = 0;
            }
            updateProgress(SETTLE_SHARE * Math.min(1, (double) elapsed / MIN_SETTLE_NANOS));
            return;
        }
        long interval = nowNanos - lastFrame;
        if (interval <= 0) return;
        intervals[count++] = interval;
        lastFrame = nowNanos;
        if (elapsed >= SAMPLE_NANOS || count == MAX_SAMPLES) finishCandidate();
        else updateProgress(SETTLE_SHARE + (1 - SETTLE_SHARE) * elapsed / SAMPLE_NANOS);
    }

    private void finishCandidate() {
        FrameStats stats = FrameStats.of(intervals, count);
        tested.add(candidate);
        trials++;
        boolean passed = passes(stats, candidate);
        if (bestStats.frames() == 0 || passed || improvesConfidence(stats) || SelectiveTuning.improves(stats, bestStats)) {
            best = candidate;
            bestStats = stats;
        }
        if (passed || trials >= MAX_TRIALS) {
            done = true;
            return;
        }
        for (PlanAdjustment adjustment : adjustments) {
            TuningPlan next = adjustment.lower(best);
            if (!next.equals(best) && !tested.contains(next)) {
                candidate = next;
                settling = true;
                phaseStarted = false;
                return;
            }
        }
        done = true;
    }

    private boolean improvesConfidence(FrameStats stats) {
        // Reducing distance we could not draw makes the estimate more representative even at the same measured FPS.
        return !keepRenderDistance && best.renderDistance() > loadedDistance
                && candidate.renderDistance() < best.renderDistance()
                && SelectiveTuning.notWorse(stats, bestStats);
    }

    private boolean passes(FrameStats stats, TuningPlan plan) {
        int measured = Math.max(1, Math.min(plan.renderDistance(), loadedDistance));
        double ratio = keepRenderDistance ? 1 : (double) plan.renderDistance() / measured;
        double scale = Math.min(MAX_UNLOADED_DISTANCE_SCALE, Math.max(1, ratio * ratio));
        return preferences.passes(stats, target * scale, true);
    }

    private void updateProgress(double withinTrial) {
        progress = Math.max(progress, Math.min(0.99, (trials + withinTrial) / MAX_TRIALS));
    }
}
