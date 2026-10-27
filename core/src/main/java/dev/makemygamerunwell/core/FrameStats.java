package dev.makemygamerunwell.core;

import java.util.Arrays;

/**
 * How a stretch of frames performed.
 *
 * @param frames number of frames measured
 * @param averageFps frames divided by the time they took
 * @param lowFps the frame rate of the slowest of every twenty frames, so hitches count against a profile
 */
public record FrameStats(int frames, double averageFps, double lowFps) {
    public static final FrameStats NONE = new FrameStats(0, 0, 0);
    private static final double NANOS_PER_SECOND = 1e9;
    private static final double LOW_PERCENTILE = 0.95;

    /** @param intervalNanos time between consecutive frames; the first {@code count} entries are used */
    public static FrameStats of(long[] intervalNanos, int count) {
        if (count <= 0) return NONE;
        long[] sorted = Arrays.copyOf(intervalNanos, count);
        long total = 0;
        for (long interval : sorted) total += Math.max(1, interval);
        Arrays.sort(sorted);
        int slowest = (int) Math.ceil(count * LOW_PERCENTILE) - 1;
        return new FrameStats(count, count * NANOS_PER_SECOND / total, NANOS_PER_SECOND / Math.max(1, sorted[slowest]));
    }
}
