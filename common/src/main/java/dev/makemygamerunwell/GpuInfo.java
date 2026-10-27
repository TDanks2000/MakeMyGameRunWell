package dev.makemygamerunwell;

import dev.makemygamerunwell.core.Hardware;

/**
 * What the game reports about the graphics device.
 *
 * @param type the device kind, or {@link Hardware.Gpu#UNKNOWN} where this game version does not say
 * @param name the device's own name
 */
public record GpuInfo(Hardware.Gpu type, String name) {}
