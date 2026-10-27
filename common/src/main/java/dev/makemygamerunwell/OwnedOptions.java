package dev.makemygamerunwell;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only options this mod ever writes or restores, keyed by their {@code options.txt} name.
 * Snapshots of them are what the backup file and the rollback path store.
 */
final class OwnedOptions {
    private static final Logger LOG = LoggerFactory.getLogger(OwnedOptions.class);

    /** Options whose change needs a texture reload to take effect. */
    static final List<String> TEXTURE_KEYS = List.of("mipmapLevels", "maxAnisotropyBit", "textureFiltering");

    private OwnedOptions() {}

    static Map<String, OptionInstance<?>> of(Options o) {
        var m = new LinkedHashMap<String, OptionInstance<?>>();
        m.put("renderDistance", o.renderDistance());
        m.put("simulationDistance", o.simulationDistance());
        m.put("entityDistanceScaling", o.entityDistanceScaling());
        m.put("maxFps", o.framerateLimit());
        m.put("enableVsync", o.enableVsync());
        m.put("renderClouds", o.cloudStatus());
        m.put("cloudRange", o.cloudRange());
        m.put("weatherRadius", o.weatherRadius());
        m.put("cutoutLeaves", o.cutoutLeaves());
        m.put("improvedTransparency", o.improvedTransparency());
        m.put("ao", o.ambientOcclusion());
        m.put("particles", o.particles());
        m.put("entityShadows", o.entityShadows());
        m.put("biomeBlendRadius", o.biomeBlendRadius());
        m.put("mipmapLevels", o.mipmapLevels());
        m.put("textureFiltering", o.textureFiltering());
        m.put("maxAnisotropyBit", o.maxAnisotropyBit());
        m.put("prioritizeChunkUpdates", o.prioritizeChunkUpdates());
        m.put("chunkSectionFadeInTime", o.chunkSectionFadeInTime());
        m.put("menuBackgroundBlurriness", o.menuBackgroundBlurriness());
        m.put("vignette", o.vignette());
        return m;
    }

    static JsonObject capture(Options o) {
        JsonObject json = new JsonObject();
        of(o).forEach((key, option) -> encode(json, key, option));
        return json;
    }

    /**
     * Restores a snapshot. Every value is decoded before any option changes, so a bad file changes nothing.
     * Options missing from the snapshot (never changed by this mod, or backed up by an older version) keep their value.
     */
    static void restore(Options o, JsonObject snapshot) {
        List<Runnable> changes = new ArrayList<>();
        of(o).forEach((key, option) -> {
            if (snapshot.has(key)) changes.add(decode(snapshot, key, option));
            else LOG.debug("Saved settings have no value for {}; leaving it unchanged", key);
        });
        if (changes.isEmpty()) throw new IllegalArgumentException("Saved settings contain none of the expected options");
        changes.forEach(Runnable::run);
    }

    private static <T> void encode(JsonObject json, String key, OptionInstance<T> option) {
        json.add(key, option.codec().encodeStart(JsonOps.INSTANCE, option.get()).getOrThrow());
    }

    private static <T> Runnable decode(JsonObject json, String key, OptionInstance<T> option) {
        T value = option.codec().parse(JsonOps.INSTANCE, json.get(key)).getOrThrow();
        return () -> option.set(value);
    }
}
