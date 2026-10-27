package dev.makemygamerunwell.core;

import java.util.List;
import java.util.Set;
import static dev.makemygamerunwell.core.PlanAdjustment.*;

/** Orders trials by the player's preferences, without claiming to know the bottleneck. */
public final class SelectiveTuning {
    private static final double IMPROVEMENT_RATIO = 1.05;
    private static final double MIN_RETAINED_RATIO = 0.95;
    private static final List<PlanAdjustment> SMOOTH = List.of(CLOUDS, SHADOWS, WEATHER, PARTICLES, BIOME_BLEND,
            ENTITY_DISTANCE, RENDER_DISTANCE, SIMULATION_DISTANCE, AMBIENT_OCCLUSION, LEAVES);
    private static final List<PlanAdjustment> BEAUTIFUL = List.of(ENTITY_DISTANCE, SIMULATION_DISTANCE,
            RENDER_DISTANCE, WEATHER, PARTICLES, SHADOWS, BIOME_BLEND, CLOUDS, AMBIENT_OCCLUSION, LEAVES);
    private static final List<PlanAdjustment> QUIET = List.of(CLOUDS, SHADOWS, WEATHER, PARTICLES, ENTITY_DISTANCE,
            RENDER_DISTANCE, SIMULATION_DISTANCE, BIOME_BLEND, AMBIENT_OCCLUSION, LEAVES);

    private SelectiveTuning() {}

    public static List<PlanAdjustment> adjustments(TuningPreferences preferences, Set<String> keep) {
        List<PlanAdjustment> order = switch (preferences.goal()) {
            case SMOOTH -> SMOOTH;
            case BEAUTIFUL -> BEAUTIFUL;
            case QUIET -> QUIET;
        };
        return order.stream().filter(step -> !keep.contains(step.option())).toList();
    }

    /** Reject noise and changes that trade a noticeable loss in one metric for a gain in the other. */
    public static boolean improves(FrameStats candidate, FrameStats best) {
        return notWorse(candidate, best) && (candidate.averageFps() >= best.averageFps() * IMPROVEMENT_RATIO
                || candidate.lowFps() >= best.lowFps() * IMPROVEMENT_RATIO);
    }

    static boolean notWorse(FrameStats candidate, FrameStats best) {
        return candidate.averageFps() >= best.averageFps() * MIN_RETAINED_RATIO
                && candidate.lowFps() >= best.lowFps() * MIN_RETAINED_RATIO;
    }
}
