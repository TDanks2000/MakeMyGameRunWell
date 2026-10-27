package dev.makemygamerunwell.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HeapAdviceTest {
    @Test void aSmallHeapAtALongDistanceIsToldHowMuchToAsk() {
        assertEquals(new HeapAdvice(HeapAdvice.Status.TOO_SMALL, 4096), HeapAdvice.assess(2048, 16384, 16));
    }

    @Test void theSuggestionNeverExceedsHalfTheInstalledMemory() {
        assertEquals(new HeapAdvice(HeapAdvice.Status.TOO_SMALL, 3072), HeapAdvice.assess(1024, 6144, 24));
    }

    @Test void aMachineThatCannotSpareMoreGetsNoAdvice() {
        assertEquals(HeapAdvice.OK, HeapAdvice.assess(2048, 4096, 16));
    }

    @Test void aHeapNearTheRecommendationIsFineBecauseTheJvmReportsSlightlyLess() {
        assertEquals(HeapAdvice.OK, HeapAdvice.assess(3900, 16384, 16));
    }

    @Test void aHeapThatStarvesTheSystemIsTooLarge() {
        assertEquals(new HeapAdvice(HeapAdvice.Status.TOO_LARGE, 4096), HeapAdvice.assess(7168, 8192, 12));
    }

    @Test void aGenerousHeapOnAMachineWithPlentyOfMemoryIsLeftAlone() {
        assertEquals(HeapAdvice.OK, HeapAdvice.assess(8192, 32768, 12));
    }

    @Test void unknownFiguresGiveNoAdvice() {
        assertEquals(HeapAdvice.OK, HeapAdvice.assess(2048, 0, 32));
        assertEquals(HeapAdvice.OK, HeapAdvice.assess(0, 16384, 32));
    }

    @Test void recommendationsRiseWithDistance() {
        assertEquals(2048, HeapAdvice.recommendedMiB(2));
        assertEquals(4096, HeapAdvice.recommendedMiB(16));
        assertEquals(8192, HeapAdvice.recommendedMiB(32));
    }
}
