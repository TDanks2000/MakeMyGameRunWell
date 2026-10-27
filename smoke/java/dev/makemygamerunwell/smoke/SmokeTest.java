package dev.makemygamerunwell.smoke;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.makemygamerunwell.AutoTuner;
import dev.makemygamerunwell.GameCompat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Real-client integration test, started from the title screen by {@code SmokeMixin}. It drives the actual Options
 * screen, then joins a world and repeats the apply and restore there, benchmarking. Any failed check fails the
 * Gradle task.
 */
public final class SmokeTest {
    private static final String AUTO = "Make My Game Run Well";
    private static final String RESTORE = "Restore";
    private static final String APPLIED = "Settings applied!";
    private static final String RESTORED = "Restored!";
    private static final String BENCHMARKING = "Benchmarking";
    private static final int OWNED_OPTIONS = 21;
    private static final long POLL_MILLIS = 250;
    private static final long WORLD_TIMEOUT_MILLIS = 240_000;
    private static final long BENCHMARK_TIMEOUT_MILLIS = 300_000;
    private static final long SETTLE_MILLIS = 4_000;
    private static final long BEFORE_INTERRUPT_MILLIS = 1_000;
    private static final String PASSED = "PASS: options button, apply, save, repeated apply, restore (byte-for-byte), "
            + "preferences, corrupt and partial backups, resize, configuration file, in-world benchmark with progress, "
            + "nothing saved mid-run, apply and restore, interruption, resize mid-run, tuning preferences, selective trials, gameplay learning pause/cancel/review/apply/restore\n";

    static long frameDelayMillis;
    static boolean maintainLearningFocus;
    static boolean maintainWindowFocus;
    static boolean slowCloudFrames;
    static boolean skipNextSave;

    public static boolean skipSave() {
        if (!skipNextSave) return false;
        skipNextSave = false;
        return true;
    }

    public static void delayFrame() {
        var mc = Minecraft.getInstance();
        if (maintainWindowFocus && !mc.isWindowActive()) GLFW.glfwFocusWindow(mc.getWindow().handle());
        if (maintainWindowFocus || maintainLearningFocus) mc.getFramerateLimitTracker().onInputReceived();
        if (maintainLearningFocus) {
            if (GameCompat.screen(mc) instanceof PauseScreen) GameCompat.setScreen(mc, null);
            if (mc.player != null && GameCompat.screen(mc) == null) mc.player.turn(0.25, 0);
        }
        long delay = slowCloudFrames && mc.options.cloudStatus().get() != CloudStatus.OFF ? 40 : frameDelayMillis;
        if (delay <= 0) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final Minecraft mc;
    private final Options o;
    private final Path backupFile;
    private final Path configFile;
    private boolean finished;

    private SmokeTest(Minecraft mc) {
        this.mc = mc;
        this.o = mc.options;
        this.backupFile = mc.gameDirectory.toPath().resolve("config/makemygamerunwell/previous-settings.json");
        this.configFile = mc.gameDirectory.toPath().resolve("config/makemygamerunwell.json");
    }

    public static void start(Minecraft mc) {
        SmokeTest test = new SmokeTest(mc);
        mc.execute(() -> test.guarded(() -> {
            maintainWindowFocus = true;
            mc.options.pauseOnLostFocus = false;
            GLFW.glfwFocusWindow(mc.getWindow().handle());
            test.menuChecks();
            test.configurationChecks();
            new TuningFeatureChecks(test, mc).menuChecks();
            test.startWorld();
        }));
    }

    private void menuChecks() throws IOException {
        // A recognizable baseline makes persistence and restore assertions meaningful.
        o.renderDistance().set(23);
        o.simulationDistance().set(11);
        o.mipmapLevels().set(0);
        double sensitivity = o.sensitivity().get();
        int fov = o.fov().get();
        o.save();
        String baseline = optionsFile();

        OptionsScreen screen = openOptions(false);
        check(Files.isRegularFile(configFile), "The default configuration file was not created");
        Button auto = button(screen, AUTO);
        Button restore = button(screen, RESTORE);
        auto.onPress(null);
        check(auto.getMessage().getString().equals(APPLIED), "Apply failed");
        check(!AutoTuner.running(), "Outside a world there is nothing to benchmark, yet a benchmark is running");
        check(o.renderDistance().get() <= 16, "Render distance unchanged");
        check(restore.active && AutoTuner.canRestore(), "No restore backup");
        check(backup().size() == OWNED_OPTIONS, "The first backup should hold every option the mod changes");
        check(optionsFile().contains("renderDistance:" + o.renderDistance().get() + System.lineSeparator()), "Settings not persisted");
        // A second click must keep the original baseline.
        auto.onPress(null);
        restore.onPress(null);
        check(o.renderDistance().get() == 23 && o.simulationDistance().get() == 11, "Restore lost original values");
        check(o.mipmapLevels().get() == 0, "Restore lost mipmap setting");
        check(optionsFile().equals(baseline), "options.txt differs from the baseline after restore");
        check(!AutoTuner.canRestore(), "Backup not consumed");
        check(o.sensitivity().get() == sensitivity && o.fov().get() == fov, "Personal settings changed");

        // An unreadable backup must be replaced, not left to block every future backup.
        Files.createDirectories(backupFile.getParent());
        Files.writeString(backupFile, "this is not json");
        auto.onPress(null);
        check(auto.getMessage().getString().equals(APPLIED), "Apply failed with a corrupt backup present");
        check(Files.exists(backupFile.resolveSibling("previous-settings.json.corrupt")), "Corrupt backup was not set aside");
        check(Files.readString(backupFile).contains("renderDistance"), "Corrupt backup was not replaced");
        // A backup from an older version may lack newer options; the rest must still restore.
        JsonObject partial = backup();
        partial.remove("vignette");
        Files.writeString(backupFile, partial.toString());
        restore.onPress(null);
        check(restore.getMessage().getString().equals(RESTORED), "Restore failed for a backup missing an option");
        check(o.renderDistance().get() == 23, "Partial restore lost original values");
        check(optionsFile().equals(baseline.replaceFirst("vignette:[^\r\n]*", "vignette:" + o.vignette().get())),
                "Partial restore changed an option it had no value for");

        // Resize and recreate the screen: injection must add exactly one button.
        screen.resize(640, 360);
        long buttons = screen.children().stream().filter(w -> w instanceof Button b &&
                (b.getMessage().getString().equals(AUTO) || b.getMessage().getString().equals(APPLIED))).count();
        check(buttons == 1, "Duplicate or missing button after resize");
    }

    private void configurationChecks() throws IOException {
        o.enableVsync().set(true);
        o.cloudStatus().set(CloudStatus.FANCY);
        o.save();
        String baseline = optionsFile();

        // Kept options are untouched, caps and overrides apply, and the backup only holds what changed.
        writeConfig("{\"profile\": \"high\", \"maxRenderDistance\": 6, \"fpsLimit\": 90, "
                + "\"keepOptions\": [\"enableVsync\", \"renderClouds\"]}");
        OptionsScreen screen = openOptions(false);
        Button auto = button(screen, AUTO);
        Button restore = button(screen, RESTORE);
        auto.onPress(null);
        check(auto.getMessage().getString().equals(APPLIED), "Apply failed with a configuration file");
        check(o.enableVsync().get() && o.cloudStatus().get() == CloudStatus.FANCY, "A kept option was changed");
        check(o.renderDistance().get() == 6, "maxRenderDistance was not applied");
        check(o.framerateLimit().get() == 90, "fpsLimit was not applied");
        JsonObject backup = backup();
        check(!backup.has("enableVsync") && !backup.has("renderClouds") && backup.size() == OWNED_OPTIONS - 2,
                "The backup should hold only the options that changed");

        // Releasing a kept option must teach the backup its original value, so Restore can still undo everything.
        writeConfig("{}");
        auto.onPress(null);
        check(!o.enableVsync().get(), "Vsync stayed on after it stopped being kept");
        backup = backup();
        check(backup.size() == OWNED_OPTIONS, "The backup did not pick up the newly changed options");
        check(backup.get("enableVsync").getAsBoolean(), "The backup lost the original vsync value");
        check(backup.get("renderDistance").getAsInt() == 23, "The backup replaced an original value");
        restore.onPress(null);
        check(restore.getMessage().getString().equals(RESTORED), "Restore failed after the backup grew");
        check(optionsFile().equals(baseline), "options.txt differs from the baseline after restoring a grown backup");

        // The configuration can hide the buttons entirely.
        writeConfig("{\"showButton\": false}");
        screen = openOptions(false);
        check(screen.children().stream().noneMatch(w -> w instanceof Button b && b.getMessage().getString().equals(AUTO)),
                "The button was shown although showButton is false");

        // A broken file must not break anything: defaults apply.
        writeConfig("this is { not json");
        screen = openOptions(false);
        auto = button(screen, AUTO);
        auto.onPress(null);
        check(auto.getMessage().getString().equals(APPLIED), "Apply failed with an invalid configuration file");
        button(screen, RESTORE).onPress(null);
        check(optionsFile().equals(baseline), "options.txt differs from the baseline after the invalid configuration run");
        writeConfig("{}");
    }

    private void startWorld() {
        var settings = new LevelSettings("mmgrw-smoke", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT,
                true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("mmgrw-smoke", settings, WorldOptions.defaultWithRandomSeed(),
                WorldPresets::createNormalWorldDimensions, GameCompat.screen(mc));
        waitFor(() -> mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && GameCompat.screen(mc) == null,
                WORLD_TIMEOUT_MILLIS, "the world to load", this::inWorldChecks);
    }

    /** The button inside a world: progress is shown, nothing is saved until the benchmark ends, then it applies. */
    private void inWorldChecks() throws IOException {
        o.renderDistance().set(20);
        o.simulationDistance().set(12);
        o.save();
        String baseline = optionsFile();

        OptionsScreen screen = openOptions(true);
        Button auto = button(screen, AUTO);
        Button restore = button(screen, RESTORE);
        auto.onPress(null);
        check(AutoTuner.running(), "The benchmark did not start in a world");
        check(!auto.active && !restore.active, "The buttons stayed active during the benchmark");
        waitFor(() -> auto.getMessage().getString().startsWith(BENCHMARKING), BENCHMARK_TIMEOUT_MILLIS,
                "progress on the button", () -> {
                    check(optionsFile().equals(baseline), "The benchmark saved settings before it finished");
                    check(!AutoTuner.canRestore(), "A backup was written before the benchmark finished");
                    waitFor(() -> auto.getMessage().getString().equals(APPLIED), BENCHMARK_TIMEOUT_MILLIS,
                            "the benchmark to finish", () -> afterBenchmark(baseline, auto, restore));
                });
    }

    private void afterBenchmark(String baseline, Button auto, Button restore) throws IOException {
        check(auto.active && !AutoTuner.running(), "The button stayed locked after the benchmark");
        check(o.renderDistance().get() <= 16, "Render distance unchanged in a world");
        check(o.framerateLimit().get() <= 140, "The benchmark left the frame rate uncapped");
        check(optionsFile().contains("renderDistance:" + o.renderDistance().get() + System.lineSeparator()), "Settings not persisted");
        check(backup().size() == OWNED_OPTIONS, "The backup should hold every option the mod changed");
        check(backup().get("renderDistance").getAsInt() == 20, "The backup holds a value the benchmark set");

        sleepThen(SETTLE_MILLIS, () -> {
            checkStillInWorld();
            var server = mc.getSingleplayerServer();
            check(server.getPlayerList().getPlayers().get(0).requestedViewDistance() == o.renderDistance().get(),
                    "The server did not receive the new render distance");
            check(server.getPlayerList().getSimulationDistance() == o.simulationDistance().get(),
                    "The server did not receive the new simulation distance");
            restore.onPress(null);
            check(restore.getMessage().getString().equals(RESTORED), "Restore failed in a world");
            check(o.renderDistance().get() == 20 && optionsFile().equals(baseline), "Restore in a world lost original values");
            resultChecks(baseline);
        });
    }

    /** Runs the benchmark through the API to see what it decided. */
    private void resultChecks(String baseline) {
        Captor captor = new Captor();
        AutoTuner.start(captor);
        check(AutoTuner.running(), "The second benchmark did not start");
        waitFor(captor::ended, BENCHMARK_TIMEOUT_MILLIS, "the benchmark to report", () -> {
            check(captor.result.isPresent(), "The benchmark failed");
            var result = captor.result.get();
            check(result.basis() == AutoTuner.Basis.BENCHMARKED, "The result was not benchmarked: " + result.basis());
            check(result.stats().frames() > 0 && result.stats().averageFps() > 0, "No frames were measured: " + result.stats());
            check(captor.progressCalls > 0 && captor.lastPercent <= 100, "The benchmark reported no progress");
            check(!AutoTuner.running(), "The benchmark did not end");
            interruptionChecks(baseline);
        });
    }

    /** Closing the screen mid-run must stop the benchmark and leave the player's settings exactly as they were. */
    private void interruptionChecks(String baseline) throws IOException {
        // The previous benchmark saved settings; put the baseline back so the comparison below is meaningful.
        OptionsScreen screen = openOptions(true);
        button(screen, RESTORE).onPress(null);
        check(optionsFile().equals(baseline), "Restore did not return the baseline before the interruption test");
        Captor captor = new Captor();
        AutoTuner.start(captor);
        check(AutoTuner.running(), "The interruption benchmark did not start");
        sleepThen(BEFORE_INTERRUPT_MILLIS, () -> {
            check(AutoTuner.running(), "The benchmark ended too early to be interrupted");
            GameCompat.setScreen(mc, null);
            waitFor(captor::ended, BENCHMARK_TIMEOUT_MILLIS, "the interrupted benchmark to stop", () -> {
                check(captor.interrupted && captor.result == null, "Closing the screen did not interrupt the benchmark");
                check(!AutoTuner.running(), "An interrupted benchmark kept running");
                check(o.renderDistance().get() == 20 && o.simulationDistance().get() == 12,
                        "An interrupted benchmark left its settings applied");
                check(optionsFile().equals(baseline), "An interrupted benchmark changed options.txt");
                check(!AutoTuner.canRestore(), "An interrupted benchmark left a backup");
                resizeChecks(baseline);
            });
        });
    }

    /** Resizing rebuilds the buttons; the new ones must show the running benchmark and then its result. */
    private void resizeChecks(String baseline) throws IOException {
        OptionsScreen screen = openOptions(true);
        button(screen, AUTO).onPress(null);
        check(AutoTuner.running(), "The resize benchmark did not start");
        screen.resize(640, 360);
        check(!button(screen, AUTO).active && !button(screen, RESTORE).active,
                "A rebuilt screen offered its buttons during a benchmark");
        waitFor(() -> hasButton(screen, APPLIED), BENCHMARK_TIMEOUT_MILLIS, "the rebuilt screen to show the result", () -> {
            check(button(screen, APPLIED).active && button(screen, RESTORE).active, "The rebuilt buttons stayed locked");
            button(screen, RESTORE).onPress(null);
            check(optionsFile().equals(baseline), "Restore after the resize benchmark lost original values");
            sleepThen(SETTLE_MILLIS, () -> {
                checkStillInWorld();
                new TuningFeatureChecks(this, mc).inWorldChecks(() -> finish(PASSED));
            });
        });
    }

    private void checkStillInWorld() {
        check(mc.level != null && mc.player != null, "The client left the world");
        check(mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isRunning(), "The integrated server stopped");
    }

    OptionsScreen openOptions(boolean inGame) {
        OptionsScreen screen = new OptionsScreen(GameCompat.screen(mc), o, inGame);
        GameCompat.setScreen(mc, screen);
        return screen;
    }

    void writeConfig(String json) throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, json);
    }

    JsonObject backup() throws IOException {
        return JsonParser.parseString(Files.readString(backupFile)).getAsJsonObject();
    }

    String optionsFile() throws IOException {
        return Files.readString(o.getFile().toPath());
    }

    void waitFor(BooleanSupplier ready, long timeoutMillis, String what, ThrowingRunnable next) {
        waitUntil(ready, System.currentTimeMillis() + timeoutMillis, what, next);
    }

    private void waitUntil(BooleanSupplier ready, long deadline, String what, ThrowingRunnable next) {
        sleepThen(POLL_MILLIS, () -> {
            if (ready.getAsBoolean()) next.run();
            else if (System.currentTimeMillis() > deadline) throw new AssertionError("Timed out waiting for " + what);
            else waitUntil(ready, deadline, what, next);
        });
    }

    /** Lets the game run for a while, then continues on the render thread. */
    void sleepThen(long millis, ThrowingRunnable task) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mc.execute(() -> guarded(task));
        }, "MMGRW smoke wait");
        thread.setDaemon(true);
        thread.start();
    }

    private void guarded(ThrowingRunnable task) {
        try {
            task.run();
        } catch (Throwable error) {
            error.printStackTrace();
            finish("FAIL: " + error);
        }
    }

    private void finish(String result) {
        if (finished) return;
        finished = true;
        frameDelayMillis = 0;
        maintainLearningFocus = false;
        maintainWindowFocus = false;
        slowCloudFrames = false;
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("smoke-result.txt"), result);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(result.startsWith("PASS") ? "MMGRW_SMOKE_PASS" : "MMGRW_SMOKE_FAIL");
        mc.stop();
    }

    private static List<Button> buttons(Screen screen) {
        return screen.children().stream().filter(w -> w instanceof Button).map(w -> (Button) w).toList();
    }

    private static boolean hasButton(Screen screen, String label) {
        return buttons(screen).stream().anyMatch(b -> b.getMessage().getString().equals(label));
    }

    static Button button(Screen screen, String label) {
        var buttons = buttons(screen);
        return buttons.stream().filter(b -> b.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("No \"" + label + "\" button; found "
                        + buttons.stream().map(b -> b.getMessage().getString()).toList()));
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Records what a benchmark reports. */
    private static final class Captor implements AutoTuner.Listener {
        int progressCalls;
        int lastPercent = -1;
        Optional<AutoTuner.Result> result;
        boolean interrupted;

        @Override public void progress(int percent) {
            progressCalls++;
            lastPercent = percent;
        }

        @Override public void finished(Optional<AutoTuner.Result> result) {
            this.result = result;
        }

        @Override public void interrupted() {
            interrupted = true;
        }

        boolean ended() {
            return result != null || interrupted;
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
