package dev.makemygamerunwell;

import com.google.gson.JsonObject;
import dev.makemygamerunwell.core.FrameStats;
import dev.makemygamerunwell.core.GameplayAdvice;
import dev.makemygamerunwell.core.Hardware;
import dev.makemygamerunwell.core.ModConfig;
import dev.makemygamerunwell.core.TuningPlan;
import dev.makemygamerunwell.core.TuningPreferences;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates tuning, learning and restoration. Both are all-or-nothing: if anything fails,
 * the options are put back as they were and the failure is logged.
 *
 * <p>Inside a world, tuning first benchmarks the real frame rate (see {@link BenchmarkRun}) and applies the best
 * measured plan; everywhere else, or when the configuration turns it off, it applies the profile the
 * hardware suggests at once.
 */
public final class AutoTuner {
    private static final Logger LOG = LoggerFactory.getLogger(AutoTuner.class);

    /** Where a plan came from, so the player can be told how far to trust it. */
    public enum Basis {
        /** Chosen from the hardware; nothing was measured and nothing was expected to be. */
        HARDWARE,
        /** Chosen from the hardware because there was no world to measure. */
        NO_WORLD,
        /** Chosen from the hardware because the benchmark could not run. */
        UNAVAILABLE,
        /** Chosen by measuring the frame rate. */
        BENCHMARKED,
        /** A suggested reduction, based on observing the previous settings; not a test of the new settings. */
        GAMEPLAY
    }

    /**
     * Gameplay statistics describe the previous settings; benchmark statistics describe the resulting plan.
     *
     * @param shaderPack whether a shader pack was rendering, so its cost is part of what was measured
     */
    public record Result(TuningPlan plan, Basis basis, FrameStats stats, boolean targetMet, boolean shaderPack) {}

    public record LearningResult(FrameStats stats, GameplayAdvice advice, int targetFps) {}

    /** Receives what happens after {@link #start}. Called on the render thread. */
    public interface Listener {
        void progress(int percent);

        /** @param result the plan that was applied and saved, or empty if it failed and was rolled back */
        void finished(Optional<Result> result);

        /** The benchmark was stopped before it chose anything. The settings are as they were. */
        void interrupted();
    }

    private static GameplayRun learning;
    private static BenchmarkRun current;
    private static Listener listener;

    private AutoTuner() {}

    /** Warms up hardware detection; call when the screen with the button opens. */
    public static void prepare() {
        HardwareProbe.prefetch();
    }

    /** Whether the configuration asks for the buttons to be shown. */
    public static boolean buttonsEnabled() {
        return ConfigStore.load(Minecraft.getInstance()).showButton();
    }

    public static boolean canRestore() {
        return BackupStore.exists(Minecraft.getInstance());
    }

    public static boolean running() {
        return current != null;
    }

    /** Sends the running benchmark's progress to a rebuilt screen. */
    public static void attach(Listener newListener) {
        listener = newListener;
    }

    /** Called by the frame hook at the start of every frame. */
    public static void onFrame() {
        BenchmarkRun run = current;
        if (run != null) run.onFrame();
        if (learning != null) {
            if (!learning.sameWorld()) learning = null;
            else learning.onFrame();
        }
    }

    /** Starts tuning. Finishes before returning unless a benchmark runs; then it finishes some seconds later. */
    public static void start(Listener newListener) {
        if (current != null) return;
        cancelLearning();
        listener = newListener;
        Minecraft mc = Minecraft.getInstance();
        JsonObject before = OwnedOptions.capture(mc.options);
        Optional<Result> result;
        try {
            ModConfig config = ConfigStore.load(mc);
            Hardware hardware = HardwareProbe.detect(mc);
            TuningPlan suggested = TuningPlan.forHardware(hardware, config.limits(), config.preferences());
            if (!config.benchmarks()) {
                result = applyPlan(mc, before, config, hardware, suggested, Basis.HARDWARE, FrameStats.NONE, false);
            } else if (mc.level == null) {
                result = applyPlan(mc, before, config, hardware, suggested, Basis.NO_WORLD, FrameStats.NONE, false);
            } else {
                current = new BenchmarkRun(mc, before, suggested, config.keepOptions(), config.preferences(),
                        config.preferences().target(hardware.refreshRate(), config.limits().fpsLimit()),
                        new Session(mc, before, config, hardware, suggested));
                return;
            }
        } catch (Exception e) {
            LOG.error("Auto tuning failed; restoring the settings from before this click", e);
            rollback(mc, before);
            result = Optional.empty();
        }
        listener.finished(result);
    }

    public static ModConfig configuration() {
        return ConfigStore.load(Minecraft.getInstance());
    }

    public static boolean savePreferences(TuningPreferences preferences) {
        if (running()) return false;
        if (configuration().preferences().equals(preferences)) return true;
        boolean saved = ConfigStore.savePreferences(Minecraft.getInstance(), preferences);
        if (saved) cancelLearning();
        return saved;
    }

    public static boolean learning() { return learning != null && learning.running(); }
    public static int learningPercent() { return learning == null ? 0 : learning.percent(); }

    public static Optional<LearningResult> learningResult() {
        if (learning == null || learning.running()) return Optional.empty();
        if (learning.result() == null || !learning.fresh()) {
            learning = null;
            return Optional.empty();
        }
        return Optional.ofNullable(learning.result());
    }

    public static boolean startLearning() {
        Minecraft mc = Minecraft.getInstance();
        if (running() || mc.level == null || mc.player == null) return false;
        cancelLearning();
        try {
            learning = new GameplayRun(mc, ConfigStore.load(mc), HardwareProbe.detect(mc));
            return true;
        } catch (Exception e) {
            LOG.error("Could not start gameplay learning; no settings were changed", e);
            return false;
        }
    }

    public static void cancelLearning() {
        if (learning != null) learning.cancel();
        learning = null;
    }

    /** Applies only the suggested option, after checking the sample still describes this world and setup. */
    public static Optional<Result> applyLearning() {
        if (running() || learningResult().isEmpty()) return Optional.empty();
        GameplayRun run = learning;
        var observation = run.result();
        if (observation.advice().adjustment().isEmpty()) return Optional.empty();
        var step = observation.advice().adjustment().get();
        Minecraft mc = Minecraft.getInstance();
        JsonObject before = OwnedOptions.capture(mc.options);
        TuningPlan currentPlan = LiveTuningPlan.capture(mc.options, TuningPlan.forHardware(run.hardware(), run.config().limits()));
        learning = null;
        return applyPlan(mc, before, run.config(), run.hardware(), step.lower(currentPlan), Basis.GAMEPLAY,
                observation.stats(), false, Optional.of(Set.of(step.option())));
    }

    public static boolean restore() {
        if (current != null) return false;
        cancelLearning();
        Minecraft mc = Minecraft.getInstance();
        JsonObject before = OwnedOptions.capture(mc.options);
        try {
            OwnedOptions.restore(mc.options, BackupStore.read(mc));
            OptionsPersistence.commit(mc, before);
            BackupStore.delete(mc);
            LastTuneStore.delete(mc);
            return true;
        } catch (Exception e) {
            LOG.error("Could not restore settings; backup retained", e);
            rollback(mc, before);
            return false;
        }
    }

    /**
     * Puts one option back to the player's own value, leaving the rest of the tune in place. All-or-nothing like
     * every other change.
     */
    public static boolean revert(String option) {
        if (current != null) return false;
        cancelLearning();
        return SettingsReview.revert(Minecraft.getInstance(), option);
    }

    /** What to do when the benchmark that {@link #start} began makes progress and ends. */
    private static final class Session implements BenchmarkRun.Callbacks {
        private final Minecraft mc;
        private final JsonObject before;
        private final ModConfig config;
        private final Hardware hardware;
        private final TuningPlan suggested;

        Session(Minecraft mc, JsonObject before, ModConfig config, Hardware hardware, TuningPlan suggested) {
            this.mc = mc;
            this.before = before;
            this.config = config;
            this.hardware = hardware;
            this.suggested = suggested;
        }

        @Override
        public void progress(double fraction) {
            listener.progress((int) Math.round(fraction * 100));
        }

        @Override
        public void ended(BenchmarkRun.End end, TuningPlan plan, FrameStats stats, boolean targetMet) {
            current = null;
            switch (end) {
                case COMPLETED -> listener.finished(applyPlan(mc, before, config, hardware,
                        plan, Basis.BENCHMARKED, stats, targetMet));
                case STALLED -> listener.finished(applyPlan(mc, before, config, hardware, suggested,
                        Basis.UNAVAILABLE, FrameStats.NONE, false));
                case CANCELLED -> listener.interrupted();
                case FAILED -> listener.finished(Optional.empty());
            }
        }
    }

    /**
     * Applies and saves a plan, recording the player's own values first. {@code before} must be the options as they
     * were when the player clicked, not as a benchmark left them.
     */
    private static Optional<Result> applyPlan(Minecraft mc, JsonObject before, ModConfig config, Hardware hardware,
                                              TuningPlan plan, Basis basis, FrameStats stats, boolean targetMet) {
        return applyPlan(mc, before, config, hardware, plan, basis, stats, targetMet, Optional.empty());
    }

    private static Optional<Result> applyPlan(Minecraft mc, JsonObject before, ModConfig config, Hardware hardware,
                                              TuningPlan plan, Basis basis, FrameStats stats, boolean targetMet,
                                              Optional<Set<String>> only) {
        Optional<JsonObject> previousBackup = Optional.empty();
        boolean backupChanged = false;
        try {
            Map<String, Runnable> changes = SettingsApplier.changes(mc.options, plan, config.keepOptions());
            only.ifPresent(keys -> changes.keySet().retainAll(keys));
            previousBackup = usableBackup(mc);
            backupChanged = recordOriginals(mc, previousBackup, before, changes.keySet());
            changes.values().forEach(Runnable::run);
            OptionsPersistence.commit(mc, before);
            LOG.info("Applied settings for {} ({}, {}): {}", hardware, basis, stats, plan);
            if (basis != Basis.GAMEPLAY) LastTuneStore.write(mc, hardware);
            return Optional.of(new Result(plan, basis, stats, targetMet, ShaderPack.active()));
        } catch (Exception e) {
            LOG.error("Auto tuning failed; restoring the settings from before this click", e);
            rollback(mc, before);
            if (backupChanged) undoBackup(mc, previousBackup);
            return Optional.empty();
        }
    }

    /**
     * The existing backup. An unreadable one is set aside, otherwise it would block a usable backup forever.
     */
    private static Optional<JsonObject> usableBackup(Minecraft mc) throws IOException {
        Optional<JsonObject> backup = BackupStore.readIfValid(mc);
        if (backup.isEmpty() && BackupStore.exists(mc)) BackupStore.quarantine(mc);
        return backup;
    }

    /**
     * Adds the current value of every option about to change that the backup does not hold yet, so the backup always
     * has the user's own value and an existing entry is never overwritten with one of ours.
     *
     * @return whether the backup file was written
     */
    private static boolean recordOriginals(Minecraft mc, Optional<JsonObject> existing, JsonObject current,
                                           Set<String> touched) throws IOException {
        JsonObject backup = existing.map(JsonObject::deepCopy).orElseGet(JsonObject::new);
        touched.stream().filter(key -> !backup.has(key)).forEach(key -> backup.add(key, current.get(key)));
        if (existing.isPresent() && backup.equals(existing.get())) return false;
        if (backup.isEmpty()) return false;
        BackupStore.write(mc, backup);
        return true;
    }

    private static void undoBackup(Minecraft mc, Optional<JsonObject> previous) {
        try {
            BackupStore.restoreTo(mc, previous);
        } catch (IOException e) {
            LOG.warn("Could not undo the settings backup update", e);
        }
    }

    static void rollback(Minecraft mc, JsonObject before) {
        try {
            JsonObject failed = OwnedOptions.capture(mc.options);
            OwnedOptions.restore(mc.options, before);
            OptionsPersistence.commit(mc, failed);
        } catch (Exception e) {
            LOG.error("Could not roll back settings", e);
        }
    }
}
