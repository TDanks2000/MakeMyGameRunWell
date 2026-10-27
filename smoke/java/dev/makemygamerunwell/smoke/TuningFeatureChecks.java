package dev.makemygamerunwell.smoke;

import com.google.gson.JsonParser;
import dev.makemygamerunwell.AutoTuner;
import dev.makemygamerunwell.GameCompat;
import dev.makemygamerunwell.SettingsReview;
import dev.makemygamerunwell.core.GameplayAdvice;
import dev.makemygamerunwell.core.HardwareChange;
import dev.makemygamerunwell.core.PlanAdjustment;
import dev.makemygamerunwell.core.TuningPreferences;
import dev.makemygamerunwell.ui.ChangesScreen;
import dev.makemygamerunwell.ui.TuningScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import static dev.makemygamerunwell.smoke.SmokeTest.button;
import static dev.makemygamerunwell.smoke.SmokeTest.check;

/** Game-facing assertions for goals and the read-only learning/review flow. */
final class TuningFeatureChecks {
    private static final long SAMPLE_TIMEOUT_MILLIS = 240_000;
    private static final long OBSERVATION_PAUSE_MILLIS = 1_500;
    private static final long SLOW_FRAME_MILLIS = 40;
    private final SmokeTest test;
    private final Minecraft mc;
    private final Options o;

    TuningFeatureChecks(SmokeTest test, Minecraft mc) {
        this.test = test;
        this.mc = mc;
        this.o = mc.options;
    }

    void menuChecks() throws IOException {
        String baseline = test.optionsFile();
        failedSaveChecks(baseline);
        changeReviewChecks(baseline);
        test.writeConfig("""
                {"goal":"quiet", "targetFps":144, "maxRenderDistance":7, "keepOptions":["renderClouds"]}
                """);
        var screen = test.openOptions(false);
        button(screen, "Make My Game Run Well").onPress(null);
        check(o.framerateLimit().get() == 150, "144 FPS target was capped below the target");
        button(screen, "Restore").onPress(null);
        check(test.optionsFile().equals(baseline), "Target tune did not restore exactly");
        button(screen, "Tune...").onPress(null);
        check(GameCompat.screen(mc) instanceof TuningScreen, "Tune did not open the preferences screen");
        Screen tuning = GameCompat.screen(mc);
        check(!button(tuning, "Learn while I play").active, "Learning was offered outside a world");
        check(!AutoTuner.learning(), "Opening preferences started learning without consent");
        tuning.resize(320, 240);
        button(tuning, "Goal: Quiet").onPress(null);
        button(tuning, "Target: 144 FPS").onPress(null);
        button(tuning, "Save preferences").onPress(null);
        check(GameCompat.screen(mc) == screen, "Saving preferences did not return to Options");
        var saved = JsonParser.parseString(Files.readString(mc.gameDirectory.toPath()
                .resolve("config/makemygamerunwell.json"))).getAsJsonObject();
        check(saved.get("goal").getAsString().equals("smooth") && saved.get("targetFps").getAsInt() == 165,
                "The UI did not persist its goal and target");
        check(saved.get("maxRenderDistance").getAsInt() == 7 && saved.getAsJsonArray("keepOptions").size() == 1,
                "Saving preferences lost other configuration fields");
        check(test.optionsFile().equals(baseline), "Saving preferences changed game settings");
        test.writeConfig("{}");
    }

    private void changeReviewChecks(String baseline) throws IOException {
        test.writeConfig("{}");
        var lastTune = mc.gameDirectory.toPath().resolve("config/makemygamerunwell/last-tune.json");
        var screen = test.openOptions(false);
        button(screen, "Make My Game Run Well").onPress(null);
        check(Files.isRegularFile(lastTune), "A tune did not record the hardware it was made for");
        check(SettingsReview.hardwareChangedSinceLastTune().isEmpty(), "Tuning on this machine was reported as a hardware change");
        String recorded = Files.readString(lastTune);
        Files.writeString(lastTune, recorded.replaceFirst("\"physicalCores\":\\d+", "\"physicalCores\":1"));
        check(SettingsReview.hardwareChangedSinceLastTune().equals(Set.of(HardwareChange.PROCESSOR)),
                "A different processor was not noticed since the last tune");
        Files.writeString(lastTune, "not json");
        check(SettingsReview.hardwareChangedSinceLastTune().isEmpty(), "An unreadable record of the last tune caused a hint");

        String tuned = test.optionsFile();
        int backedUp = test.backup().size();
        check(SettingsReview.changedSettings().stream().anyMatch(c -> c.option().equals("renderDistance") && c.original().equals("23")),
                "The changed settings did not list the render distance with its original value");
        check(SettingsReview.report().contains("renderDistance: 23 -> ") && SettingsReview.report().contains("Minecraft "),
                "The report did not describe the change");
        button(screen, "Tune...").onPress(null);
        Screen tuning = GameCompat.screen(mc);
        button(tuning, "Changed settings...").onPress(null);
        Screen changes = GameCompat.screen(mc);
        check(changes instanceof ChangesScreen, "Changed settings did not open");
        button(changes, "Copy report").onPress(null);
        check(mc.keyboardHandler.getClipboard().startsWith("Make My Game Run Well report"), "The report was not copied");
        button(changes, "Put back").onPress(null);
        check(o.renderDistance().get() == 23, "Putting back the first setting did not restore it");
        check(test.optionsFile().equals(tuned.replaceFirst("renderDistance:\\d+", "renderDistance:23")),
                "Putting one setting back changed other settings");
        check(test.backup().size() == backedUp - 1 && !test.backup().has("renderDistance"),
                "Putting one setting back did not update the backup");
        button(changes, "Done").onPress(null);
        check(GameCompat.screen(mc) == tuning, "Changed settings did not return to Tune");
        check(AutoTuner.restore() && test.optionsFile().equals(baseline), "Restore after a partial put-back was not exact");
        check(!Files.exists(lastTune), "Restore left the record of the last tune");
        check(SettingsReview.changedSettings().isEmpty(), "Settings were listed as changed after Restore");
        GameCompat.setScreen(mc, null);
    }

    private void failedSaveChecks(String baseline) throws IOException {
        var screen = test.openOptions(false);
        int distance = o.renderDistance().get();
        int mipmaps = o.mipmapLevels().get();
        SmokeTest.skipNextSave = true;
        button(screen, "Make My Game Run Well").onPress(null);
        check(!SmokeTest.skipNextSave, "The failed-save injection did not run");
        check(button(screen, "Failed: see log").active, "A failed save was reported as success");
        check(test.optionsFile().equals(baseline) && o.renderDistance().get() == distance && o.mipmapLevels().get() == mipmaps,
                "Failed apply did not roll back both live and saved options");
        check(!AutoTuner.canRestore(), "Failed apply left a backup");
        button(screen, "Failed: see log").onPress(null);
        String tuned = test.optionsFile();
        int tunedDistance = o.renderDistance().get();
        SmokeTest.skipNextSave = true;
        check(!AutoTuner.restore(), "A failed restore save was reported as success");
        check(AutoTuner.canRestore() && test.optionsFile().equals(tuned) && o.renderDistance().get() == tunedDistance,
                "Failed restore did not retain its backup and roll back to the tuned settings");
        check(AutoTuner.restore() && test.optionsFile().equals(baseline), "Restore could not recover after a save failure");
    }

    void inWorldChecks(SmokeTest.ThrowingRunnable next) throws IOException {
        String baseline = test.optionsFile();
        test.writeConfig("{}");
        test.openOptions(true);
        SmokeTest.slowCloudFrames = true;
        var result = new AtomicReference<Optional<AutoTuner.Result>>();
        AutoTuner.start(new AutoTuner.Listener() {
            @Override public void progress(int percent) {}
            @Override public void finished(Optional<AutoTuner.Result> value) { result.set(value); }
            @Override public void interrupted() { result.set(Optional.empty()); }
        });
        test.waitFor(() -> result.get() != null, 120_000, "a selective clouds trial", () -> {
            SmokeTest.slowCloudFrames = false;
            var measured = result.get().orElseThrow(() -> new AssertionError("Selective tuning failed"));
            check(measured.basis() == AutoTuner.Basis.BENCHMARKED && measured.targetMet(), "Selective trial did not pass");
            check(!measured.shaderPack(), "A shader pack was reported although none is in use");
            check(!measured.plan().clouds() && measured.plan().shadows() && measured.plan().ambientOcclusion(),
                    "A successful clouds trial removed unrelated effects");
            check(AutoTuner.restore() && test.optionsFile().equals(baseline), "Selective tuning did not restore exactly");
            focusCancellationCheck(baseline, next);
        });
    }

    private void focusCancellationCheck(String baseline, SmokeTest.ThrowingRunnable next) {
        var stopped = new AtomicBoolean();
        AutoTuner.start(new AutoTuner.Listener() {
            @Override public void progress(int percent) {}
            @Override public void finished(Optional<AutoTuner.Result> result) {
                throw new AssertionError("The unfocused benchmark completed instead of cancelling");
            }
            @Override public void interrupted() { stopped.set(true); }
        });
        SmokeTest.maintainWindowFocus = false;
        GLFW.glfwIconifyWindow(mc.getWindow().handle());
        test.waitFor(stopped::get, 15_000, "the unfocused benchmark to cancel", () -> {
            GLFW.glfwRestoreWindow(mc.getWindow().handle());
            GLFW.glfwFocusWindow(mc.getWindow().handle());
            SmokeTest.maintainWindowFocus = true;
            check(!AutoTuner.running() && !AutoTuner.canRestore() && test.optionsFile().equals(baseline),
                    "Losing focus did not roll back the benchmark exactly");
            learningChecks(next);
        });
    }

    private void learningChecks(SmokeTest.ThrowingRunnable next) throws IOException {
        test.writeConfig("{\"goal\":\"beautiful\",\"targetFps\":60,\"keepOptions\":[\"renderClouds\"]}");
        o.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
        o.enableVsync().set(false);
        o.entityDistanceScaling().set(1.0);
        o.save();
        String baseline = test.optionsFile();
        var options = test.openOptions(true);
        button(options, "Tune...").onPress(null);
        button(GameCompat.screen(mc), "Learn while I play").onPress(null);
        check(GameCompat.screen(mc) == null && AutoTuner.learning(), "Learning did not start and resume play");
        test.sleepThen(OBSERVATION_PAUSE_MILLIS, () -> {
            test.openOptions(true);
            // Allow the first paused frame to reset the observation's interval.
            test.sleepThen(OBSERVATION_PAUSE_MILLIS, () -> {
                int progress = AutoTuner.learningPercent();
                test.sleepThen(OBSERVATION_PAUSE_MILLIS, () -> {
                    check(AutoTuner.learningPercent() == progress, "Time in menus counted as active play");
                    check(test.optionsFile().equals(baseline) && !AutoTuner.canRestore(), "Learning wrote options or a backup");
                    button(GameCompat.screen(mc), "Tune...").onPress(null);
                    button(GameCompat.screen(mc), "Stop learning").onPress(null);
                    check(!AutoTuner.learning(), "Learning could not be cancelled");
                    check(test.optionsFile().equals(baseline), "Cancelling learning changed settings");
                    settingChangeCheck(baseline, next);
                });
            });
        });
    }

    private void settingChangeCheck(String baseline, SmokeTest.ThrowingRunnable next) {
        check(AutoTuner.startLearning(), "Could not restart learning");
        GameCompat.setScreen(mc, null);
        o.entityDistanceScaling().set(1.25);
        test.waitFor(() -> !AutoTuner.learning(), 10_000, "a settings change to cancel learning", () -> {
            check(AutoTuner.learningResult().isEmpty() && AutoTuner.applyLearning().isEmpty(), "A stale sample could be applied");
            o.entityDistanceScaling().set(1.0);
            check(test.optionsFile().equals(baseline), "Discarding a stale sample saved settings");
            completeSample(baseline, next);
        });
    }

    private void completeSample(String baseline, SmokeTest.ThrowingRunnable next) {
        GLFW.glfwFocusWindow(mc.getWindow().handle());
        SmokeTest.maintainLearningFocus = true;
        SmokeTest.frameDelayMillis = SLOW_FRAME_MILLIS;
        check(AutoTuner.startLearning(), "Could not start the full gameplay sample");
        GameCompat.setScreen(mc, null);
        test.sleepThen(OBSERVATION_PAUSE_MILLIS, () -> {
            check(AutoTuner.learning() && test.optionsFile().equals(baseline) && !AutoTuner.canRestore(),
                    "A running sample changed saved settings");
            test.waitFor(() -> AutoTuner.learningPercent() > 0, 30_000, "active learning (focused=" + mc.isWindowActive()
                    + ", paused=" + mc.isPaused() + ")", () -> test.waitFor(() -> !AutoTuner.learning(),
                    SAMPLE_TIMEOUT_MILLIS, "two minutes of gameplay observation", () -> reviewSample(baseline, next)));
        });
    }

    private void reviewSample(String baseline, SmokeTest.ThrowingRunnable next) throws IOException {
        SmokeTest.frameDelayMillis = 0;
        SmokeTest.maintainLearningFocus = false;
        var observation = AutoTuner.learningResult().orElseThrow(() -> new AssertionError("No gameplay sample was reported"));
        check(observation.stats().frames() > 100 && observation.stats().averageFps() < 60, "The slow gameplay was not measured");
        check(observation.advice().status() == GameplayAdvice.Status.TRY_REDUCTION, "No reduction was suggested");
        check(observation.advice().adjustment().orElseThrow() == PlanAdjustment.ENTITY_DISTANCE,
                "Beautiful mode did not prefer entity distance");
        check(test.optionsFile().equals(baseline) && !AutoTuner.canRestore(), "The completed sample changed settings");
        var options = test.openOptions(true);
        button(options, "Tune...").onPress(null);
        Screen review = GameCompat.screen(mc);
        check(button(review, "Apply suggested reduction").active, "The review did not offer its recommendation");
        var screenshot = mc.gameDirectory.toPath().resolve("screenshots/tuning-review.png");
        Files.deleteIfExists(screenshot);
        test.sleepThen(500, () -> {
            Screenshot.grab(mc.gameDirectory, "tuning-review.png", GameCompat.mainRenderTarget(mc), 1, message -> {});
            applyAndRestore(baseline, review);
            test.waitFor(() -> Files.isRegularFile(screenshot), 10_000, "the review screenshot", next);
        });
    }

    private void applyAndRestore(String baseline, Screen review) throws IOException {
        button(review, "Apply suggested reduction").onPress(null);
        check(o.entityDistanceScaling().get() == 0.75, "The suggested reduction was not applied");
        check(test.backup().size() == 1 && test.backup().has("entityDistanceScaling"), "Recommendation changed unrelated options");
        check(test.optionsFile().equals(baseline.replaceFirst("entityDistanceScaling:[^\\r\\n]*", "entityDistanceScaling:0.75")),
                "Applying the recommendation changed more than entity distance");
        check(AutoTuner.learningResult().isEmpty(), "An applied recommendation remained reusable");
        check(AutoTuner.restore() && test.optionsFile().equals(baseline), "The recommendation did not restore exactly");
        check(AutoTuner.startLearning(), "Could not start the preference cancellation check");
        check(AutoTuner.savePreferences(new TuningPreferences(TuningPreferences.Goal.QUIET, 60)), "Could not save new preferences");
        check(!AutoTuner.learning(), "Changing preferences did not cancel learning");
        test.writeConfig("{}");
    }
}
