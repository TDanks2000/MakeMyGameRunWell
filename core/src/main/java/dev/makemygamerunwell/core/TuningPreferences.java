package dev.makemygamerunwell.core;

/** The player's preferred frame rate and tolerance for trading detail for headroom. */
public record TuningPreferences(Goal goal, int targetFps) {
    public enum Goal {
        SMOOTH(1.5), BEAUTIFUL(1.15), QUIET(1.8);

        private final double pausedHeadroom;

        Goal(double pausedHeadroom) {
            this.pausedHeadroom = pausedHeadroom;
        }

        public double pausedHeadroom() {
            return pausedHeadroom;
        }
    }

    /** The {@code targetFps} that means "follow the display's refresh rate". */
    public static final int DISPLAY_REFRESH = 0;
    public static final int MIN_TARGET = 30;
    public static final int MAX_TARGET = 240;
    public static final TuningPreferences DEFAULT = new TuningPreferences(Goal.SMOOTH, 60);

    private static final int CAP_STEP = 10;
    private static final int FALLBACK_REFRESH_RATE = 60;
    /** While playing, a sample passes at this share of the target; the frame cap itself costs a little. */
    private static final double PLAYED_AVERAGE_SHARE = 0.95;
    private static final double PLAYED_LOW_SHARE = 0.90;

    public TuningPreferences {
        if (goal == null || (targetFps != DISPLAY_REFRESH && (targetFps < MIN_TARGET || targetFps > MAX_TARGET))) {
            throw new IllegalArgumentException("Target must be display refresh (0), or " + MIN_TARGET + " to " + MAX_TARGET);
        }
    }

    /** Whether {@code targetFps} is something a preference may hold. */
    public static boolean validTarget(double targetFps) {
        return targetFps == DISPLAY_REFRESH
                || (targetFps == Math.rint(targetFps) && targetFps >= MIN_TARGET && targetFps <= MAX_TARGET);
    }

    public int target(int refreshRate, int fpsOverride) {
        if (fpsOverride > 0) return Math.min(MAX_TARGET, fpsOverride);
        if (targetFps != DISPLAY_REFRESH) return targetFps;
        return Math.clamp(refreshRate > 0 ? refreshRate : FALLBACK_REFRESH_RATE, MIN_TARGET, MAX_TARGET);
    }

    /** Vanilla's slider uses steps of ten. Round up so a 144 FPS target is not capped at 140. */
    public int frameCap(int refreshRate, int fpsOverride) {
        return fpsOverride > 0 ? Math.clamp(Math.round(fpsOverride / (float) CAP_STEP) * CAP_STEP, CAP_STEP, FrameCap.UNLIMITED)
                : ((target(refreshRate, 0) + CAP_STEP - 1) / CAP_STEP) * CAP_STEP;
    }

    public boolean passes(FrameStats stats, double target, boolean paused) {
        double average = target * (paused ? goal.pausedHeadroom() : PLAYED_AVERAGE_SHARE);
        double low = target * (paused ? 1 : PLAYED_LOW_SHARE);
        return stats.frames() > 0 && stats.averageFps() >= average && stats.lowFps() >= low;
    }
}
