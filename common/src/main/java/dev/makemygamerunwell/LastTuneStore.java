package dev.makemygamerunwell;

import com.google.gson.Gson;
import dev.makemygamerunwell.core.Hardware;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The machine the settings were last tuned for, so the player can be told when it has changed. */
final class LastTuneStore {
    private static final Logger LOG = LoggerFactory.getLogger(LastTuneStore.class);
    private static final Gson GSON = new Gson();

    private LastTuneStore() {}

    private static Path path(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("config/makemygamerunwell/last-tune.json");
    }

    /** Best effort: this file only drives a hint, so a failure to write it never fails a tune. */
    static void write(Minecraft mc, Hardware hardware) {
        try {
            AtomicFiles.write(path(mc), GSON.toJson(hardware));
        } catch (IOException e) {
            LOG.warn("Could not record the hardware this tune was made for", e);
        }
    }

    /** Empty if nothing was recorded or the file is unreadable. */
    static Optional<Hardware> read(Minecraft mc) {
        Path file = path(mc);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            Hardware hardware = GSON.fromJson(Files.readString(file), Hardware.class);
            return hardware == null || hardware.gpu() == null ? Optional.empty() : Optional.of(hardware);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Ignoring unreadable record of the last tune", e);
            return Optional.empty();
        }
    }

    static void delete(Minecraft mc) {
        try {
            Files.deleteIfExists(path(mc));
        } catch (IOException e) {
            LOG.warn("Could not remove the record of the last tune", e);
        }
    }
}
