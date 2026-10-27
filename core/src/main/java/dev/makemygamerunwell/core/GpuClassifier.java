package dev.makemygamerunwell.core;

import java.util.Locale;
import java.util.regex.Pattern;

/** Refines the device type reported by the graphics layer using the device name. */
public final class GpuClassifier {
    private static final Pattern SOFTWARE = Pattern.compile("llvmpipe|softpipe|swrast|swiftshader|basic render|software");
    private static final Pattern INTEL_ARC_DISCRETE = Pattern.compile("\\barc\\b.*\\b[ab]\\d{2,3}\\b");
    // "AMD Radeon(TM) Graphics", "Radeon 780M" and "Radeon RX Vega 11 Graphics" are APUs; "Radeon RX 7600" is a card.
    private static final Pattern AMD_INTEGRATED = Pattern.compile(
            "radeon(\\(tm\\))? graphics|radeon(\\(tm\\))? \\d{3,4}m\\b|vega \\d+ graphics");
    private static final Pattern AMD_DISCRETE = Pattern.compile("radeon.*\\b(rx|pro w|vii)\\b");
    private static final Pattern NVIDIA = Pattern.compile("geforce|quadro|\\brtx\\b|\\bgtx\\b|nvidia");
    // Roughly GTX 1070 / RX 5700 and above: comfortably runs vanilla at 16+ chunks, even at 4K.
    private static final Pattern HIGH_END = Pattern.compile(String.join("|",
            "gtx\\s*10[78]0",
            "titan",
            "rtx\\s*(20[78]0|30[789]0|40[789]0|50[789]0)",
            "rx\\s*(5700|6[789]00|7[789]00|9070)"));

    private GpuClassifier() {}

    /**
     * @param reported class derived from the driver's device type; {@code UNKNOWN} when it said nothing useful
     * @param deviceName device name as reported by the driver, may be empty
     */
    public static Hardware.Gpu classify(Hardware.Gpu reported, String deviceName) {
        if (reported == Hardware.Gpu.SOFTWARE) return reported;
        String name = deviceName.toLowerCase(Locale.ROOT);
        if (SOFTWARE.matcher(name).find()) return Hardware.Gpu.SOFTWARE;

        Hardware.Gpu gpu = reported;
        if (name.contains("intel")) gpu = INTEL_ARC_DISCRETE.matcher(name).find() ? Hardware.Gpu.DISCRETE : Hardware.Gpu.INTEGRATED;
        else if (name.contains("apple")) gpu = Hardware.Gpu.INTEGRATED;
        else if (AMD_INTEGRATED.matcher(name).find()) gpu = Hardware.Gpu.INTEGRATED;
        else if (AMD_DISCRETE.matcher(name).find() || NVIDIA.matcher(name).find()) gpu = Hardware.Gpu.DISCRETE;

        if (gpu == Hardware.Gpu.DISCRETE && HIGH_END.matcher(name).find()) return Hardware.Gpu.HIGH_END;
        return gpu;
    }
}
