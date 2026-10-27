package dev.makemygamerunwell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists the original value of every option this mod has changed, so Restore survives a restart.
 * An option enters the backup the first time the mod changes it and is never overwritten after that.
 */
final class BackupStore {
    private static final Logger LOG = LoggerFactory.getLogger(BackupStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BackupStore() {}

    private static Path path(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("config/makemygamerunwell/previous-settings.json");
    }

    /** Cheap existence check, safe to call every time a screen is built. */
    static boolean exists(Minecraft mc) {
        return Files.isRegularFile(path(mc));
    }

    static JsonObject read(Minecraft mc) throws IOException {
        try {
            return JsonParser.parseString(Files.readString(path(mc))).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Backup is not a valid settings file", e);
        }
    }

    /** The existing backup, or empty if there is none or it is unreadable. */
    static Optional<JsonObject> readIfValid(Minecraft mc) {
        if (!exists(mc)) return Optional.empty();
        try {
            return Optional.of(read(mc));
        } catch (IOException e) {
            LOG.warn("Ignoring unreadable settings backup", e);
            return Optional.empty();
        }
    }

    /** Keeps an unreadable backup next to the original for inspection so it can be replaced. */
    static void quarantine(Minecraft mc) throws IOException {
        Path file = path(mc);
        Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
    }

    static void write(Minecraft mc, JsonObject snapshot) throws IOException {
        AtomicFiles.write(path(mc), GSON.toJson(snapshot));
    }

    static void delete(Minecraft mc) throws IOException {
        Files.deleteIfExists(path(mc));
    }

    /** Puts the backup back to {@code contents}, or removes it if there was none. */
    static void restoreTo(Minecraft mc, Optional<JsonObject> contents) throws IOException {
        if (contents.isPresent()) write(mc, contents.get());
        else delete(mc);
    }
}
