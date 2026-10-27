package dev.makemygamerunwell.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameCapTest {
    @Test void anUnlimitedSliderWithoutVsyncLimitsNothing() {
        assertEquals(0, FrameCap.effective(FrameCap.UNLIMITED, false, 144));
    }

    @Test void aSetLimitIsTheCap() {
        assertEquals(90, FrameCap.effective(90, false, 144));
    }

    @Test void vsyncCapsAtTheRefreshRate() {
        assertEquals(144, FrameCap.effective(FrameCap.UNLIMITED, true, 144));
        assertEquals(60, FrameCap.effective(120, true, 60));
    }

    @Test void theStricterOfLimitAndVsyncWins() {
        assertEquals(60, FrameCap.effective(60, true, 144));
    }

    @Test void vsyncOnAnUnknownDisplayAddsNoCap() {
        assertEquals(0, FrameCap.effective(FrameCap.UNLIMITED, true, 0));
        assertEquals(90, FrameCap.effective(90, true, 0));
    }
}
