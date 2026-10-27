package dev.makemygamerunwell;

import dev.makemygamerunwell.core.TuningPlan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.server.level.ParticleStatus;

/** Turns a {@link TuningPlan} into option changes. Only ever produces changes to {@link OwnedOptions}. */
final class SettingsApplier {
    /** The lowest value the option accepts: 2x anisotropic filtering. */
    private static final int MIN_ANISOTROPY_BIT = 1;

    private SettingsApplier() {}

    /**
     * The changes a plan makes, keyed by {@code options.txt} name, minus any option in {@code keep}.
     * Nothing changes until the returned actions are run, so the caller can back up what they will touch first.
     */
    static Map<String, Runnable> changes(Options o, TuningPlan p, Set<String> keep) {
        var all = new LinkedHashMap<String, Runnable>();
        all.put("renderDistance", () -> o.renderDistance().set(p.renderDistance()));
        all.put("simulationDistance", () -> o.simulationDistance().set(p.simulationDistance()));
        all.put("entityDistanceScaling", () -> o.entityDistanceScaling().set(p.entityDistance()));
        all.put("maxFps", () -> o.framerateLimit().set(p.fpsLimit()));
        all.put("renderClouds", () -> o.cloudStatus().set(p.clouds() ? CloudStatus.FAST : CloudStatus.OFF));
        all.put("cloudRange", () -> o.cloudRange().set(p.cloudRange()));
        all.put("weatherRadius", () -> o.weatherRadius().set(p.weatherRadius()));
        all.put("cutoutLeaves", () -> o.cutoutLeaves().set(p.fancy()));
        all.put("ao", () -> o.ambientOcclusion().set(p.ambientOcclusion()));
        all.put("particles", () -> o.particles().set(p.allParticles() ? ParticleStatus.ALL : ParticleStatus.DECREASED));
        all.put("entityShadows", () -> o.entityShadows().set(p.shadows()));
        all.put("biomeBlendRadius", () -> o.biomeBlendRadius().set(p.biomeBlend()));
        all.put("mipmapLevels", () -> o.mipmapLevels().set(p.mipmapLevels()));
        all.put("vignette", () -> o.vignette().set(p.vignette()));

        // The same on every machine: these only cost performance.
        all.put("enableVsync", () -> o.enableVsync().set(false));
        all.put("improvedTransparency", () -> o.improvedTransparency().set(false));
        all.put("textureFiltering", () -> o.textureFiltering().set(TextureFilteringMethod.NONE));
        all.put("maxAnisotropyBit", () -> o.maxAnisotropyBit().set(MIN_ANISOTROPY_BIT));
        all.put("prioritizeChunkUpdates", () -> o.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NONE));
        all.put("chunkSectionFadeInTime", () -> o.chunkSectionFadeInTime().set(0.0));
        all.put("menuBackgroundBlurriness", () -> o.menuBackgroundBlurriness().set(0));

        Set<String> owned = OwnedOptions.of(o).keySet();
        if (!owned.equals(all.keySet())) {
            throw new IllegalStateException("Applier and owned options disagree: " + all.keySet() + " vs " + owned);
        }
        all.keySet().removeAll(keep);
        return all;
    }
}
