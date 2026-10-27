package dev.makemygamerunwell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Saves {@code options.txt}, proves the owned values reached disk, and refreshes the renderer. */
final class OptionsPersistence {
    private OptionsPersistence() {}

    /**
     * @param before snapshot taken before the options changed, used to decide what needs reloading
     * @throws IOException if any owned option did not make it to {@code options.txt}
     */
    static void commit(Minecraft mc, JsonObject before) throws IOException {
        mc.options.save();
        // Options.save logs IO errors instead of throwing, so check the file ourselves.
        Map<String, String> onDisk = readOptionsFile(mc);
        JsonObject after = OwnedOptions.capture(mc.options);
        for (var entry : after.entrySet()) {
            String saved = onDisk.get(entry.getKey());
            if (saved == null || !JsonParser.parseString(saved).equals(entry.getValue()))
                throw new IOException("Option was not saved: " + entry.getKey());
        }

        GameCompat.reloadLevelRenderer(mc);
        if (OwnedOptions.TEXTURE_KEYS.stream().anyMatch(key -> !before.get(key).equals(after.get(key)))) {
            mc.updateMaxMipLevel(mc.options.mipmapLevels().get());
            mc.delayTextureReload();
        }
    }

    private static Map<String, String> readOptionsFile(Minecraft mc) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(mc.options.getFile().toPath())) {
            int colon = line.indexOf(':');
            if (colon > 0) values.put(line.substring(0, colon), line.substring(colon + 1));
        }
        return values;
    }
}
