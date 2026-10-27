package dev.makemygamerunwell.core;

/** A bounded sample of active play. Menu, focus and world checks are supplied by the game adapter. */
public final class GameplaySample {
    public static final long DURATION_NANOS = 120_000_000_000L;
    private static final int MAX_SAMPLES = 240_000;
    private final long duration;
    private final long[] intervals = new long[MAX_SAMPLES];
    private long previous;
    private boolean hasPrevious;
    private long activeNanos;
    private int count;

    public GameplaySample() {
        this(DURATION_NANOS);
    }

    public GameplaySample(long duration) {
        if (duration <= 0) throw new IllegalArgumentException("Sample duration must be positive");
        this.duration = duration;
    }

    public void onFrame(long now, boolean active) {
        if (done()) return;
        if (!active) {
            hasPrevious = false;
            return;
        }
        if (hasPrevious && now > previous) {
            long interval = now - previous;
            intervals[count++] = interval;
            activeNanos += interval;
        }
        previous = now;
        hasPrevious = true;
    }

    public boolean done() { return activeNanos >= duration || count == MAX_SAMPLES; }
    public double progress() { return done() ? 1 : (double) activeNanos / duration; }
    public FrameStats stats() { return FrameStats.of(intervals, count); }
}
