package dev.makemygamerunwell.core;

/** What limits the frame rate in practice, whichever of the cap and VSync is stricter. */
public final class FrameCap {
    /** Vanilla's "unlimited" position on the frame rate slider. */
    public static final int UNLIMITED = 260;

    private FrameCap() {}

    /**
     * @param limitSetting the frame rate limit option, where {@link #UNLIMITED} or more means no limit
     * @param vsync whether VSync is on
     * @param refreshRate the display's refresh rate, {@code 0} when unknown
     * @return the highest frame rate the game can reach, or {@code 0} when nothing limits it
     */
    public static int effective(int limitSetting, boolean vsync, int refreshRate) {
        int cap = limitSetting >= UNLIMITED ? 0 : limitSetting;
        if (!vsync || refreshRate <= 0) return cap;
        return cap == 0 ? refreshRate : Math.min(cap, refreshRate);
    }
}
