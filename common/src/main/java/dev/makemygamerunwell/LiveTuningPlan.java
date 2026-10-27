package dev.makemygamerunwell;

import dev.makemygamerunwell.core.TuningPlan;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/** Reads the current settings for a gameplay recommendation. It never applies a hardware preset. */
final class LiveTuningPlan {
    private LiveTuningPlan() {}

    static TuningPlan capture(Options o, TuningPlan suggested) {
        return new TuningPlan(suggested.tier(), TuningPlan.Limit.NONE, o.renderDistance().get(),
                o.simulationDistance().get(), o.entityDistanceScaling().get(), o.framerateLimit().get(),
                o.cutoutLeaves().get(), o.cloudStatus().get() != CloudStatus.OFF, o.entityShadows().get(),
                o.ambientOcclusion().get(), o.particles().get() == ParticleStatus.ALL, o.vignette().get(),
                o.biomeBlendRadius().get(), o.weatherRadius().get(), o.cloudRange().get(), o.mipmapLevels().get());
    }
}
