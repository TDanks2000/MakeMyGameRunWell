package dev.makemygamerunwell.core;

public record Hardware(int physicalCores, double cpuGhz, long memoryMiB, long heapMiB,
                       Gpu gpu, int width, int height, int refreshRate) {
    public enum Gpu { UNKNOWN, SOFTWARE, INTEGRATED, DISCRETE, HIGH_END }
}
