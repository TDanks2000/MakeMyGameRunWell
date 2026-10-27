package dev.makemygamerunwell;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason;
import dev.makemygamerunwell.core.FrameBenchmark;
import dev.makemygamerunwell.core.FrameStats;
import dev.makemygamerunwell.core.TuningPlan;
import dev.makemygamerunwell.core.TuningPreferences;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One benchmark in progress: applies each candidate plan to the live options (nothing is saved), feeds the game's
 * frames to a {@link FrameBenchmark}, and puts the options back if it ends any way other than completing.
 * Everything runs on the render thread.
 */
final class BenchmarkRun {
    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkRun.class);

    enum End {
        /** A plan was chosen. The live options are left as they are for the caller to replace. */
        COMPLETED,
        /** The player closed the screen, left the world, or the game throttled itself. Options were put back. */
        CANCELLED,
        /** No frames arrived, so a benchmark cannot work in this installation. Options were put back. */
        STALLED,
        /** Something threw. Options were put back. */
        FAILED
    }

    interface Callbacks {
        void progress(double fraction);

        void ended(End end, TuningPlan plan, FrameStats stats, boolean targetMet);
    }

    private final Minecraft mc;
    private final Screen screen;
    private final JsonObject before;
    private final Set<String> keep;
    private final FrameBenchmark benchmark;
    private final Callbacks callbacks;
    private TuningPlan applied;
    private boolean sawFrame;
    private boolean ended;

    BenchmarkRun(Minecraft mc, JsonObject before, TuningPlan suggested, Set<String> keep,
                 TuningPreferences preferences, int target, Callbacks callbacks) {
        this.mc = mc;
        this.screen = GameCompat.screen(mc);
        this.before = before;
        this.keep = keep;
        this.callbacks = callbacks;
        this.benchmark = new FrameBenchmark(suggested, mc.options.renderDistance().get(), preferences, target, keep);
        apply(benchmark.candidate());
        watchForFirstFrame();
    }

    void onFrame() {
        if (ended) return;
        sawFrame = true;
        try {
            if (mc.level == null || GameCompat.screen(mc) != screen) {
                LOG.info("Benchmark cancelled: the Options screen was closed");
                end(End.CANCELLED);
            } else if (!mc.isWindowActive() || mc.getFramerateLimitTracker().getThrottleReason() != FramerateThrottleReason.NONE) {
                LOG.info("Benchmark cancelled: the window lost focus or the game limited its frame rate ({})",
                        mc.getFramerateLimitTracker().getThrottleReason());
                end(End.CANCELLED);
            } else {
                benchmark.onFrame(System.nanoTime(), mc.levelRenderer.hasRenderedAllSections());
                callbacks.progress(benchmark.progress());
                if (benchmark.done()) end(End.COMPLETED);
                else if (!benchmark.candidate().equals(applied)) apply(benchmark.candidate());
            }
        } catch (Exception e) {
            LOG.error("Benchmark failed; restoring the settings from before this click", e);
            end(End.FAILED);
        }
    }

    /** Applies a trial without texture reloads, and removes the frame cap for measurement. */
    private void apply(TuningPlan plan) {
        var changes = SettingsApplier.changes(mc.options, plan, keep);
        changes.keySet().removeAll(OwnedOptions.TEXTURE_KEYS);
        changes.remove("maxFps");
        changes.values().forEach(Runnable::run);
        if (!keep.contains("maxFps")) mc.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
        GameCompat.reloadLevelRenderer(mc);
        applied = plan;
    }

    private void end(End how) {
        if (ended) return;
        ended = true;
        if (how != End.COMPLETED && !putOptionsBack()) how = End.FAILED;
        callbacks.ended(how, benchmark.result(), benchmark.resultStats(), benchmark.targetMet());
    }

    /** Saves too: the Options screen writes {@code options.txt} when it closes, which may have captured a candidate. */
    private boolean putOptionsBack() {
        try {
            JsonObject live = OwnedOptions.capture(mc.options);
            JsonObject restore = before.deepCopy();
            keep.forEach(restore::remove);
            if (!restore.isEmpty()) OwnedOptions.restore(mc.options, restore);
            OptionsPersistence.commit(mc, live);
            return true;
        } catch (Exception e) {
            LOG.error("Could not put the settings back after the benchmark", e);
            return false;
        }
    }

    private void watchForFirstFrame() {
        FrameHookWatchdog.start(mc, "MMGRW benchmark watchdog", () -> sawFrame || ended, () -> {
            // Mods such as Dynamic FPS stop rendering an unfocused window, which looks like a missing frame hook.
            if (!mc.isWindowActive()) {
                LOG.info("Benchmark cancelled: the window lost focus before the first frame");
                end(End.CANCELLED);
                return;
            }
            LOG.warn("The benchmark received no frames in {} ms; its frame hook is not active", FrameHookWatchdog.TIMEOUT_MILLIS);
            end(End.STALLED);
        });
    }
}
