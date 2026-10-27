package dev.makemygamerunwell.core;

import java.util.EnumSet;
import java.util.Set;

/**
 * What differs between the machine at the last tune and the machine now. Window size is ignored because it changes
 * whenever the player resizes the window; a figure either side could not read never counts as a change.
 */
public enum HardwareChange {
    PROCESSOR, MEMORY, GRAPHICS, REFRESH_RATE;

    private static final double MEMORY_TOLERANCE = 0.05;

    public static Set<HardwareChange> between(Hardware before, Hardware now) {
        Set<HardwareChange> changed = EnumSet.noneOf(HardwareChange.class);
        if (before.physicalCores() != now.physicalCores()) changed.add(PROCESSOR);
        if (differs(before.heapMiB(), now.heapMiB()) || differs(before.memoryMiB(), now.memoryMiB())) changed.add(MEMORY);
        if (before.gpu() != now.gpu() && before.gpu() != Hardware.Gpu.UNKNOWN && now.gpu() != Hardware.Gpu.UNKNOWN) {
            changed.add(GRAPHICS);
        }
        if (before.refreshRate() > 0 && now.refreshRate() > 0 && before.refreshRate() != now.refreshRate()) {
            changed.add(REFRESH_RATE);
        }
        return changed;
    }

    private static boolean differs(long before, long now) {
        return before > 0 && now > 0 && Math.abs(before - now) > Math.max(before, now) * MEMORY_TOLERANCE;
    }
}
