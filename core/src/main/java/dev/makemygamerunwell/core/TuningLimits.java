package dev.makemygamerunwell.core;

import java.util.Optional;

/**
 * What an installation or modpack asks of the tuning, on top of what the hardware allows.
 *
 * @param forcedTier use this profile whatever the hardware is
 * @param maxTier the highest profile automatic selection may reach
 * @param maxRenderDistance cap in chunks, {@code 0} for none
 * @param maxSimulationDistance cap in chunks, {@code 0} for none
 * @param fpsLimit frame rate limit to use instead of the automatic one, {@code 0} for automatic
 * @param gpuClass treat the graphics device as this class instead of detecting it
 */
public record TuningLimits(Optional<TuningPlan.Tier> forcedTier, TuningPlan.Tier maxTier, int maxRenderDistance,
                           int maxSimulationDistance, int fpsLimit, Optional<Hardware.Gpu> gpuClass) {
    public static final TuningLimits NONE = new TuningLimits(Optional.empty(), TuningPlan.Tier.HIGH, 0, 0, 0,
            Optional.empty());
}
