package dev.makemygamerunwell.core;

import java.util.Set;

/**
 * Settings a player or modpack author can change in {@code config/makemygamerunwell.json}.
 *
 * @param showButton whether the Options screen gets the Make My Game Run Well and Restore buttons
 * @param benchmark whether the button measures the frame rate in a world before choosing; otherwise it applies the
 *                  hardware's suggestion at once
 * @param limits what the tuning must respect on top of the hardware
 * @param keepOptions {@code options.txt} names of options the mod must never change
 * @param preferences the player's performance goal and target FPS
 */
public record ModConfig(boolean showButton, boolean benchmark, TuningLimits limits, Set<String> keepOptions,
                        TuningPreferences preferences) {
    public ModConfig {
        keepOptions = Set.copyOf(keepOptions);
    }

    public static final ModConfig DEFAULT = new ModConfig(true, true, TuningLimits.NONE, Set.of(), TuningPreferences.DEFAULT);

    /** Options that cap the frame rate: while one is kept, the measured frame rate says nothing about a profile. */
    private static final Set<String> FRAME_RATE_OPTIONS = Set.of("maxFps", "enableVsync");

    /** Whether measuring can change the outcome: it is on, no profile is forced, and no frame rate option is kept. */
    public boolean benchmarks() {
        return benchmark && limits.forcedTier().isEmpty()
                && keepOptions.stream().noneMatch(FRAME_RATE_OPTIONS::contains);
    }

    /** The options the mod must leave alone: all of them under a forced profile, which already decides everything. */
    public Set<String> untouchable(Set<String> allOptions) {
        return limits.forcedTier().isPresent() ? allOptions : keepOptions;
    }
}
