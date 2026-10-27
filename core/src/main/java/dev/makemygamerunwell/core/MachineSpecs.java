package dev.makemygamerunwell.core;

/** CPU and RAM figures that are safe to feed into {@link Hardware}; unknown clock speed is {@code 0}. */
public record MachineSpecs(int physicalCores, double cpuGhz, long memoryMiB) {
    private static final double MAX_PLAUSIBLE_GHZ = 10;

    /**
     * Replaces values a platform failed to report (zero, negative, absurd) with the best estimate available.
     *
     * @param physicalCores reported physical core count
     * @param maxFrequencyHz reported maximum clock in hertz; many platforms report {@code -1}
     * @param memoryMiB reported installed RAM
     * @param logicalProcessors the JVM's own processor count, assumed to be two threads per core when cores are unknown
     * @param jvmMemoryMiB RAM as reported by the JVM, used only when {@code memoryMiB} is unknown
     */
    public static MachineSpecs sanitize(long physicalCores, long maxFrequencyHz, long memoryMiB,
                                        int logicalProcessors, long jvmMemoryMiB) {
        int cores = physicalCores > 0 ? (int) Math.min(physicalCores, Integer.MAX_VALUE)
                : Math.max(1, logicalProcessors / 2);
        double ghz = maxFrequencyHz / 1e9;
        return new MachineSpecs(cores,
                ghz > 0 && ghz <= MAX_PLAUSIBLE_GHZ ? ghz : 0,
                memoryMiB > 0 ? memoryMiB : Math.max(0, jvmMemoryMiB));
    }
}
