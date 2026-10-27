package dev.makemygamerunwell.core;

import java.util.Optional;
import java.util.Set;

/** Observation establishes whether the current setup met a target, not which option caused a shortfall. */
public record GameplayAdvice(Status status, Optional<PlanAdjustment> adjustment) {
    public enum Status { SMOOTH, CAPPED, TRY_REDUCTION, AT_FLOOR }

    public static GameplayAdvice forSample(FrameStats stats, TuningPlan current, TuningPreferences preferences,
                                           int target, int effectiveCap, Set<String> keep) {
        if (preferences.passes(stats, target, false)) return new GameplayAdvice(Status.SMOOTH, Optional.empty());
        if (effectiveCap > 0 && effectiveCap < target * 0.95) {
            return new GameplayAdvice(Status.CAPPED, Optional.empty());
        }
        for (var step : SelectiveTuning.adjustments(preferences, keep)) {
            if (!step.lower(current).equals(current)) return new GameplayAdvice(Status.TRY_REDUCTION, Optional.of(step));
        }
        return new GameplayAdvice(Status.AT_FLOOR, Optional.empty());
    }
}
