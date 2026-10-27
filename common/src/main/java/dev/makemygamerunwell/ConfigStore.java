package dev.makemygamerunwell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.makemygamerunwell.core.ModConfig;
import dev.makemygamerunwell.core.ModConfigFormat;
import dev.makemygamerunwell.core.TuningPreferences;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads {@code config/makemygamerunwell.json}, creating it with documented defaults on first use. */
final class ConfigStore {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String lastText;
    private static ModConfig lastConfig = ModConfig.DEFAULT;
    private static String reportedFailure;

    private ConfigStore() {}

    static synchronized boolean savePreferences(Minecraft mc, TuningPreferences preferences) {
        try {
            Path file = path(mc);
            JsonObject json = Files.isRegularFile(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                    : JsonParser.parseString(ModConfigFormat.TEMPLATE).getAsJsonObject();
            json.addProperty("goal", preferences.goal().name().toLowerCase(Locale.ROOT));
            json.addProperty("targetFps", preferences.targetFps());
            AtomicFiles.write(file, GSON.toJson(json) + System.lineSeparator());
            load(mc);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not save tuning preferences; configuration left unchanged", e);
            return false;
        }
    }

    private static Path path(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("config/makemygamerunwell.json");
    }

    /**
     * The current configuration. Never throws: an invalid file yields defaults for the bad settings, and a file
     * that cannot be read keeps the last good configuration. Each problem is logged once, so a bad file cannot
     * take the game down or flood the log.
     */
    static synchronized ModConfig load(Minecraft mc) {
        Path file = path(mc);
        try {
            if (!Files.isRegularFile(file)) AtomicFiles.write(file, ModConfigFormat.TEMPLATE);
            String text = Files.readString(file);
            if (!text.equals(lastText)) {
                lastConfig = ModConfigFormat.parse(text, OwnedOptions.of(mc.options).keySet(),
                        problem -> LOG.warn("{}: {}", file.getFileName(), problem));
                lastText = text;
            }
            reportedFailure = null;
        } catch (IOException | RuntimeException e) {
            if (!e.toString().equals(reportedFailure)) {
                reportedFailure = e.toString();
                LOG.warn("Could not read {}; keeping the previous configuration", file, e);
            }
        }
        return lastConfig;
    }
}
