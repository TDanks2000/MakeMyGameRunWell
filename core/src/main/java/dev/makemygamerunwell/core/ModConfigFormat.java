package dev.makemygamerunwell.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the configuration file. A mistake in it never stops the game or the tuning: each bad
 * setting is reported and replaced by its default, and the rest of the file still applies.
 */
public final class ModConfigFormat {
    /** The file written on first launch. Comments are accepted when reading. */
    public static final String TEMPLATE = """
            // Make My Game Run Well. Edit and save; changes apply the next time the button is used.
            {
              // Show the "Make My Game Run Well" and "Restore" buttons on the Options screen.
              "showButton": true,

              // In a world, measure the frame rate and try selective reductions until the target is reached.
              // false applies the hardware's suggestion immediately, without measuring.
              "benchmark": true,

              // "smooth", "beautiful" (less reserve for busy scenes), or "quiet" (more reserve, capped FPS).
              "goal": "smooth",
              // Frame rate to aim for: 30 to 240, or 0 for display refresh (bounded to that range).
              "targetFps": 60,

              // "auto" chooses a profile from the hardware. Or force "minimal", "low", "medium" or "high" everywhere.
              "profile": "auto",

              // The highest profile "auto" may choose. Lower it for heavy modpacks.
              "maxProfile": "high",

              // Upper limits in chunks. 0 means no limit.
              "maxRenderDistance": 0,
              "maxSimulationDistance": 0,

              // Frame rate limit to use instead of the automatic one: a multiple of 10 from 10 to 260. 0 means automatic.
              "fpsLimit": 0,

              // If the graphics device is misdetected: "auto", "software", "integrated", "discrete" or "high_end".
              "gpuClass": "auto",

              // Options this mod must never change, named as in options.txt, e.g. ["enableVsync", "renderClouds"].
              "keepOptions": []
            }
            """;

    private static final String SHOW_BUTTON = "showButton";
    private static final String BENCHMARK = "benchmark";
    private static final String GOAL = "goal";
    private static final String TARGET_FPS = "targetFps";
    private static final String PROFILE = "profile";
    private static final String MAX_PROFILE = "maxProfile";
    private static final String MAX_RENDER_DISTANCE = "maxRenderDistance";
    private static final String MAX_SIMULATION_DISTANCE = "maxSimulationDistance";
    private static final String FPS_LIMIT = "fpsLimit";
    private static final String GPU_CLASS = "gpuClass";
    private static final String KEEP_OPTIONS = "keepOptions";
    private static final List<String> KNOWN = List.of(SHOW_BUTTON, BENCHMARK, PROFILE, MAX_PROFILE, MAX_RENDER_DISTANCE,
            MAX_SIMULATION_DISTANCE, FPS_LIMIT, GPU_CLASS, KEEP_OPTIONS, GOAL, TARGET_FPS);
    private static final String AUTO = "auto";
    private static final int MAX_DISTANCE = 32;
    private static final int MIN_RENDER_DISTANCE = 2;
    private static final int MIN_SIMULATION_DISTANCE = 5;
    private static final int MIN_FPS_LIMIT = 10;
    private static final int MAX_FPS_LIMIT = 260;

    private ModConfigFormat() {}

    /**
     * @param knownOptions every option name {@code keepOptions} may contain
     * @param warn receives one message per problem found
     */
    public static ModConfig parse(String text, Set<String> knownOptions, Consumer<String> warn) {
        JsonObject json = parseObject(text, warn);
        if (json == null) return ModConfig.DEFAULT;
        for (String key : json.keySet()) {
            if (!KNOWN.contains(key)) warn.accept("Unknown setting \"" + key + "\" ignored; known settings: " + KNOWN);
        }
        var limits = new TuningLimits(
                tier(json, PROFILE, warn),
                tier(json, MAX_PROFILE, warn).orElse(TuningPlan.Tier.HIGH),
                integer(json, MAX_RENDER_DISTANCE, MIN_RENDER_DISTANCE, MAX_DISTANCE, warn),
                integer(json, MAX_SIMULATION_DISTANCE, MIN_SIMULATION_DISTANCE, MAX_DISTANCE, warn),
                integer(json, FPS_LIMIT, MIN_FPS_LIMIT, MAX_FPS_LIMIT, warn),
                gpuClass(json, warn));
        return new ModConfig(bool(json, SHOW_BUTTON, warn), bool(json, BENCHMARK, warn), limits,
                optionNames(json, knownOptions, warn), preferences(json, warn));
    }

    private static TuningPreferences preferences(JsonObject json, Consumer<String> warn) {
        TuningPreferences.Goal goal = TuningPreferences.Goal.SMOOTH;
        if (json.has(GOAL)) {
            goal = match(word(json, GOAL), List.of(TuningPreferences.Goal.values())).orElseGet(() -> {
                warn.accept("\"goal\" must be \"smooth\", \"beautiful\" or \"quiet\"; using \"smooth\"");
                return TuningPreferences.DEFAULT.goal();
            });
        }
        int target = TuningPreferences.DEFAULT.targetFps();
        if (json.has(TARGET_FPS)) {
            JsonElement element = json.get(TARGET_FPS);
            String problem = "\"targetFps\" must be a whole number from " + TuningPreferences.MIN_TARGET + " to "
                    + TuningPreferences.MAX_TARGET + ", or 0 for the display refresh rate; using " + target;
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                    && TuningPreferences.validTarget(element.getAsDouble())) {
                target = (int) element.getAsDouble();
            } else warn.accept(problem);
        }
        return new TuningPreferences(goal, target);
    }

    private static JsonObject parseObject(String text, Consumer<String> warn) {
        try {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setStrictness(Strictness.LENIENT);
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) return root.getAsJsonObject();
            warn.accept("The configuration file must contain a JSON object; using the defaults");
        } catch (JsonParseException e) {
            warn.accept("The configuration file is not valid JSON (" + e.getMessage() + "); using the defaults");
        }
        return null;
    }

    private static boolean bool(JsonObject json, String name, Consumer<String> warn) {
        JsonElement element = json.get(name);
        if (element == null) return true;
        JsonPrimitive value = element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
        if (value != null && value.isBoolean()) return value.getAsBoolean();
        warn.accept("\"" + name + "\" must be true or false; using true");
        return true;
    }

    /** @return the number, or {@code 0} (meaning "no limit" / "automatic") when absent or invalid */
    private static int integer(JsonObject json, String name, int min, int max, Consumer<String> warn) {
        JsonElement element = json.get(name);
        if (element == null) return 0;
        JsonPrimitive value = element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
        if (value != null && value.isNumber()) {
            double number = value.getAsDouble();
            if (number == 0) return 0;
            if (number == Math.rint(number) && number >= min && number <= max) return (int) number;
        }
        warn.accept("\"" + name + "\" must be 0 or a whole number from " + min + " to " + max + "; using 0");
        return 0;
    }

    private static Optional<TuningPlan.Tier> tier(JsonObject json, String name, Consumer<String> warn) {
        String value = word(json, name);
        if (AUTO.equals(value) || !json.has(name)) return Optional.empty();
        Optional<TuningPlan.Tier> tier = match(value, List.of(TuningPlan.Tier.values()));
        if (tier.isEmpty()) {
            warn.accept("\"" + name + "\" must be \"auto\", \"minimal\", \"low\", \"medium\" or \"high\"; using \"" + AUTO + "\"");
        }
        return tier;
    }

    private static Optional<Hardware.Gpu> gpuClass(JsonObject json, Consumer<String> warn) {
        String value = word(json, GPU_CLASS);
        if (AUTO.equals(value) || !json.has(GPU_CLASS)) return Optional.empty();
        Optional<Hardware.Gpu> gpu = match(value, Arrays.stream(Hardware.Gpu.values())
                .filter(candidate -> candidate != Hardware.Gpu.UNKNOWN).toList());
        if (gpu.isEmpty()) {
            warn.accept("\"" + GPU_CLASS + "\" must be \"auto\", \"software\", \"integrated\", \"discrete\" or \"high_end\"; using \"" + AUTO + "\"");
        }
        return gpu;
    }

    /** The constant whose lower-cased name is {@code word}. */
    private static <E extends Enum<E>> Optional<E> match(String word, List<E> candidates) {
        return candidates.stream().filter(candidate -> candidate.name().toLowerCase(Locale.ROOT).equals(word)).findFirst();
    }

    private static Set<String> optionNames(JsonObject json, Set<String> knownOptions, Consumer<String> warn) {
        Set<String> names = new LinkedHashSet<>();
        JsonElement value = json.get(KEEP_OPTIONS);
        if (value == null) return names;
        if (!value.isJsonArray()) {
            warn.accept("\"" + KEEP_OPTIONS + "\" must be a list of option names; ignoring it");
            return names;
        }
        for (JsonElement element : value.getAsJsonArray()) {
            String name = element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
            if (name != null && knownOptions.contains(name)) names.add(name);
            else warn.accept("\"" + KEEP_OPTIONS + "\" has " + element + ", which is not an option this mod changes; ignoring it");
        }
        return names;
    }

    /** A lower-cased string setting, or {@code null} when absent or not a string. */
    private static String word(JsonObject json, String name) {
        JsonElement element = json.get(name);
        boolean string = element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
        return string ? element.getAsString().trim().toLowerCase(Locale.ROOT) : null;
    }
}
