package dev.makemygamerunwell.core;

import java.util.List;

/**
 * Whether the memory Minecraft was started with suits the render distance. The mod never changes the JVM's memory;
 * this only tells the player what to set in their launcher.
 */
public record HeapAdvice(Status status, long suggestedMiB) {
    public enum Status { OK, TOO_SMALL, TOO_LARGE }

    public static final HeapAdvice OK = new HeapAdvice(Status.OK, 0);

    private record Recommendation(int upToRenderDistance, long heapMiB) {}

    private static final List<Recommendation> RECOMMENDATIONS = List.of(
            new Recommendation(8, 2048), new Recommendation(12, 3072),
            new Recommendation(16, 4096), new Recommendation(24, 6144));
    private static final long LARGEST_RECOMMENDATION_MIB = 8192;
    private static final long STEP_MIB = 512;
    private static final double SMALL_TOLERANCE = 0.85;
    private static final double LARGE_SHARE_OF_RAM = 0.75;
    private static final double SUGGESTED_SHARE_OF_RAM = 0.5;

    /** The heap that keeps chunk and entity data comfortable at a render distance. */
    static long recommendedMiB(int renderDistance) {
        return RECOMMENDATIONS.stream().filter(step -> renderDistance <= step.upToRenderDistance())
                .findFirst().map(Recommendation::heapMiB).orElse(LARGEST_RECOMMENDATION_MIB);
    }

    /**
     * @param heapMiB the most memory the game may use
     * @param systemMiB installed RAM; {@code 0} when unknown, which gives no advice
     */
    public static HeapAdvice assess(long heapMiB, long systemMiB, int renderDistance) {
        if (heapMiB <= 0 || systemMiB <= 0) return OK;
        long share = roundDown(systemMiB * SUGGESTED_SHARE_OF_RAM);
        if (heapMiB > systemMiB * LARGE_SHARE_OF_RAM) {
            return new HeapAdvice(Status.TOO_LARGE, Math.max(STEP_MIB, share));
        }
        long recommended = recommendedMiB(renderDistance);
        long suggested = Math.min(recommended, share);
        return heapMiB < recommended * SMALL_TOLERANCE && suggested > heapMiB
                ? new HeapAdvice(Status.TOO_SMALL, suggested) : OK;
    }

    private static long roundDown(double mib) {
        return (long) (mib / STEP_MIB) * STEP_MIB;
    }
}
