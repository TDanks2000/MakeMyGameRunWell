package dev.makemygamerunwell.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameStatsTest {
    private static final long MS = 1_000_000;

    private static long[] frames(int count, long nanos) {
        long[] frames = new long[count];
        Arrays.fill(frames, nanos);
        return frames;
    }

    @Test void steadyFramesGiveTheSameAverageAndLowRate() {
        var stats = FrameStats.of(frames(100, 10 * MS), 100);
        assertEquals(100, stats.frames());
        assertEquals(100, stats.averageFps(), 1e-9);
        assertEquals(100, stats.lowFps(), 1e-9);
    }

    @Test void hitchesPullTheLowRateDownMuchMoreThanTheAverage() {
        long[] frames = frames(100, 10 * MS);
        for (int i = 0; i < 10; i++) frames[i * 10] = 50 * MS;
        var stats = FrameStats.of(frames, 100);
        assertEquals(20, stats.lowFps(), 1e-9);
        assertTrue(stats.averageFps() > 50);
    }

    @Test void aSingleHitchInTwentyFramesIsNotTheLowRate() {
        long[] frames = frames(20, 10 * MS);
        frames[7] = 100 * MS;
        assertEquals(100, FrameStats.of(frames, 20).lowFps(), 1e-9);
    }

    @Test void onlyTheFirstCountEntriesAreUsed() {
        long[] frames = frames(10, 10 * MS);
        Arrays.fill(frames, 5, 10, 1000 * MS);
        assertEquals(100, FrameStats.of(frames, 5).averageFps(), 1e-9);
    }

    @Test void noFramesAndZeroLengthIntervalsAreHandled() {
        assertEquals(FrameStats.NONE, FrameStats.of(new long[0], 0));
        var stats = FrameStats.of(frames(3, 0), 3);
        assertTrue(stats.averageFps() > 0 && Double.isFinite(stats.averageFps()));
        assertTrue(Double.isFinite(stats.lowFps()));
    }

    @Test void theInputArrayIsNotReordered() {
        long[] frames = {30 * MS, 10 * MS, 20 * MS};
        FrameStats.of(frames, 3);
        assertArrayEquals(new long[]{30 * MS, 10 * MS, 20 * MS}, frames);
    }
}
