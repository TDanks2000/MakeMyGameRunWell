package dev.makemygamerunwell.core;

/** The settings chosen for a machine. Pure policy: no Minecraft types, so it is unit tested. */
public record TuningPlan(Tier tier, Limit limit, int renderDistance, int simulationDistance,
                         double entityDistance, int fpsLimit, boolean fancy, boolean clouds,
                         boolean shadows, boolean ambientOcclusion, boolean allParticles, boolean vignette,
                         int biomeBlend, int weatherRadius, int cloudRange, int mipmapLevels) {

    /** Overall profile. Ordinal doubles as the index into the per-tier tables below. */
    public enum Tier { MINIMAL, LOW, MEDIUM, HIGH }

    /**
     * What kept the profile below {@link Tier#HIGH}: a hardware component, the installation's own configuration,
     * or the benchmark selecting individual reductions.
     */
    public enum Limit { NONE, MEMORY, CPU, GPU, CONFIG, BENCHMARK }

    private static final int[] RENDER_DISTANCE = {5, 8, 12, 16};
    private static final int[] SIMULATION_DISTANCE = {5, 5, 6, 8};
    private static final double[] ENTITY_DISTANCE = {0.5, 0.75, 1.0, 1.25};
    private static final double ASSUMED_GHZ_WHEN_UNKNOWN = 2.5;
    private static final int FAST_CPU_CORES = 8;
    private static final double FAST_CPU_GHZ = 3.0;
    private static final int GOOD_CPU_CORES = 4;
    private static final double GOOD_CPU_GHZ = 2.5;
    private static final int MIN_CPU_CORES = 2;
    private static final long HIGH_RESOLUTION_PIXELS = 3_700_000;
    private static final int MAX_TIER = Tier.HIGH.ordinal();
    private static final int MIN_RENDER_DISTANCE = 2;
    private static final int MIN_SIMULATION_DISTANCE = 5;
    private static final int WEATHER_RADIUS_FANCY = 8;
    private static final int WEATHER_RADIUS_BASIC = 3;
    private static final int MAX_CLOUD_RANGE = 32;
    /** Heap and installed RAM, in MiB, that each memory tier from LOW upwards needs. */
    private static final long[] TIER_HEAP_MIB = {1536, 3072, 6144};
    private static final long[] TIER_SYSTEM_MIB = {4096, 8192, 16384};
    private static final int MIPMAP_LEVELS_FANCY = 4;
    private static final int MIPMAP_LEVELS_BASIC = 2;

    public static TuningPlan forHardware(Hardware h) {
        return forHardware(h, TuningLimits.NONE);
    }

    public static TuningPlan forHardware(Hardware h, TuningLimits limits) {
        return forHardware(h, limits, TuningPreferences.DEFAULT);
    }

    /** Chooses the initial quality ceiling and the requested frame cap. */
    public static TuningPlan forHardware(Hardware detected, TuningLimits limits, TuningPreferences preferences) {
        Hardware h = limits.gpuClass().map(gpu -> withGpu(detected, gpu)).orElse(detected);
        int gpu = gpuScore(h);
        int cpu = cpuScore(h);
        int memory = memoryScore(h);
        int hardware = Math.min(gpu, Math.min(cpu, memory));
        int score = limits.forcedTier().map(Tier::ordinal).orElseGet(() -> Math.min(hardware, limits.maxTier().ordinal()));
        Limit limit = limits.forcedTier().isPresent() || score == MAX_TIER ? Limit.NONE
                : score < hardware ? Limit.CONFIG
                : hardware == memory ? Limit.MEMORY
                : hardware == cpu ? Limit.CPU
                : Limit.GPU;

        // Simulation cost follows the CPU and memory, not the GPU, unless the configuration chose the profile.
        int simulationTier = limits.forcedTier().isPresent() || score < hardware ? score : Math.min(cpu, memory);
        return build(h, limits, preferences, score, limit, simulationTier);
    }

    private static TuningPlan build(Hardware h, TuningLimits limits, TuningPreferences preferences, int score, Limit limit, int simulationTier) {
        int render = RENDER_DISTANCE[score];
        if (limits.maxRenderDistance() > 0) render = Math.max(MIN_RENDER_DISTANCE, Math.min(render, limits.maxRenderDistance()));
        int simulation = Math.min(render, SIMULATION_DISTANCE[simulationTier]);
        if (limits.maxSimulationDistance() > 0) simulation = Math.min(simulation, limits.maxSimulationDistance());
        simulation = Math.max(MIN_SIMULATION_DISTANCE, simulation);

        boolean fancy = score >= Tier.MEDIUM.ordinal();
        int fps = preferences.frameCap(h.refreshRate(), limits.fpsLimit());
        return new TuningPlan(Tier.values()[score], limit, render, simulation, ENTITY_DISTANCE[score], fps,
                fancy,                    // leaf detail
                score == MAX_TIER,        // clouds
                fancy,                    // shadows
                fancy,                    // ambient occlusion
                fancy,                    // all particles
                fancy,                    // vignette
                score,                    // biome blend radius
                fancy ? WEATHER_RADIUS_FANCY : WEATHER_RADIUS_BASIC,
                Math.min(MAX_CLOUD_RANGE, render * 2),
                fancy ? MIPMAP_LEVELS_FANCY : MIPMAP_LEVELS_BASIC);
    }

    private static Hardware withGpu(Hardware h, Hardware.Gpu gpu) {
        return new Hardware(h.physicalCores(), h.cpuGhz(), h.memoryMiB(), h.heapMiB(), gpu, h.width(), h.height(), h.refreshRate());
    }

    private static int gpuScore(Hardware h) {
        int score = switch (h.gpu()) {
            case UNKNOWN, SOFTWARE -> 0;
            case INTEGRATED -> 1;
            case DISCRETE -> 2;
            case HIGH_END -> 3;
        };
        // Pixel count directly affects GPU cost, but a high-end card still copes with 4K.
        boolean highResolution = (long) h.width() * h.height() > HIGH_RESOLUTION_PIXELS;
        return highResolution && h.gpu() != Hardware.Gpu.HIGH_END ? Math.max(0, score - 1) : score;
    }

    private static int cpuScore(Hardware h) {
        double ghz = h.cpuGhz() > 0 ? h.cpuGhz() : ASSUMED_GHZ_WHEN_UNKNOWN;
        return h.physicalCores() >= FAST_CPU_CORES && ghz >= FAST_CPU_GHZ ? Tier.HIGH.ordinal()
                : h.physicalCores() >= GOOD_CPU_CORES && ghz >= GOOD_CPU_GHZ ? Tier.MEDIUM.ordinal()
                : h.physicalCores() >= MIN_CPU_CORES ? Tier.LOW.ordinal() : Tier.MINIMAL.ordinal();
    }

    private static int memoryScore(Hardware h) {
        int tier = Tier.MINIMAL.ordinal();
        for (int i = 0; i < TIER_HEAP_MIB.length; i++) {
            if (h.heapMiB() >= TIER_HEAP_MIB[i] && h.memoryMiB() >= TIER_SYSTEM_MIB[i]) tier = i + 1;
        }
        return tier;
    }
}
