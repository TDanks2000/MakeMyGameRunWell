package dev.makemygamerunwell;

import com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason;
import dev.makemygamerunwell.core.FrameCap;
import dev.makemygamerunwell.core.GameplayAdvice;
import dev.makemygamerunwell.core.GameplaySample;
import dev.makemygamerunwell.core.Hardware;
import dev.makemygamerunwell.core.ModConfig;
import dev.makemygamerunwell.core.TuningPlan;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only observation of one world with one set of options. Never changes or saves an option. */
final class GameplayRun {
    private static final Logger LOG = LoggerFactory.getLogger(GameplayRun.class);
    /** An observation older than this no longer describes the player's current play. */
    private static final long MAX_WALL_NANOS = 600_000_000_000L;

    private final Minecraft mc;
    private final WeakReference<ClientLevel> world;
    private final Map<String, OptionInstance<?>> watched;
    private final Map<String, Object> values = new HashMap<>();
    private final List<String> resourcePacks;
    private final ModConfig config;
    private final Hardware hardware;
    private final GameplaySample sample = new GameplaySample();
    private final long started = System.nanoTime();
    private boolean ended;
    private boolean sawFrame;
    private AutoTuner.LearningResult result;

    GameplayRun(Minecraft mc, ModConfig config, Hardware hardware) {
        this.mc = mc;
        this.world = new WeakReference<>(mc.level);
        this.watched = OwnedOptions.of(mc.options);
        watched.forEach((key, option) -> values.put(key, option.get()));
        this.resourcePacks = List.copyOf(mc.options.resourcePacks);
        this.config = config;
        this.hardware = hardware;
        FrameHookWatchdog.start(mc, "MMGRW learning watchdog", () -> sawFrame || ended, () -> {
            LOG.warn("Gameplay learning received no frames; its frame hook is not active");
            cancel();
        });
    }

    void onFrame() {
        sawFrame = true;
        if (ended) return;
        try {
            long now = System.nanoTime();
            if (!sameWorld() || now - started > MAX_WALL_NANOS || !settingsMatch()) {
                cancel();
                return;
            }
            boolean active = mc.player != null && GameCompat.screen(mc) == null && !GameCompat.hasOverlay(mc)
                    && !mc.isPaused() && mc.isWindowActive()
                    && mc.getFramerateLimitTracker().getThrottleReason() == FramerateThrottleReason.NONE;
            sample.onFrame(now, active);
            if (sample.done()) complete();
        } catch (Exception e) {
            LOG.error("Gameplay learning stopped; no settings were changed", e);
            cancel();
        }
    }

    private void complete() {
        if (!fresh()) {
            cancel();
            return;
        }
        TuningPlan current = LiveTuningPlan.capture(mc.options, TuningPlan.forHardware(hardware, config.limits()));
        int target = config.preferences().target(hardware.refreshRate(), config.limits().fpsLimit());
        int cap = FrameCap.effective(mc.options.framerateLimit().get(), mc.options.enableVsync().get(), hardware.refreshRate());
        var stats = sample.stats();
        var advice = GameplayAdvice.forSample(stats, current, config.preferences(), target, cap,
                config.untouchable(OwnedOptions.of(mc.options).keySet()));
        result = new AutoTuner.LearningResult(stats, advice, target);
        ended = true;
        GameCompat.showOverlayMessage(mc, Component.translatable("mmgrw.learning.ready"));
    }

    boolean sameWorld() {
        return mc.level != null && world.get() == mc.level;
    }

    private boolean settingsMatch() {
        if (mc.getWindow().getWidth() != hardware.width() || mc.getWindow().getHeight() != hardware.height()
                || mc.getWindow().getRefreshRate() != hardware.refreshRate() || !resourcePacks.equals(mc.options.resourcePacks)) {
            return false;
        }
        for (var entry : watched.entrySet()) {
            if (!values.get(entry.getKey()).equals(entry.getValue().get())) return false;
        }
        return true;
    }

    boolean fresh() {
        return sameWorld() && settingsMatch() && config.equals(ConfigStore.load(mc));
    }

    boolean running() {
        return !ended;
    }

    int percent() {
        return (int) Math.round(sample.progress() * 100);
    }

    AutoTuner.LearningResult result() {
        return result;
    }

    ModConfig config() {
        return config;
    }

    Hardware hardware() {
        return hardware;
    }

    void cancel() {
        if (ended) return;
        ended = true;
        result = null;
        GameCompat.showOverlayMessage(mc, Component.translatable("mmgrw.learning.stopped"));
    }
}
