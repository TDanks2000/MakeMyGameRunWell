package dev.makemygamerunwell.ui;

import dev.makemygamerunwell.AutoTuner;
import dev.makemygamerunwell.GameCompat;
import dev.makemygamerunwell.SettingsReview;
import dev.makemygamerunwell.core.HardwareChange;
import dev.makemygamerunwell.core.TuningPlan;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;

/** The Options screen footer row: [Make My Game Run Well] [Restore] [Tune...] [Done], unless the configuration hides it. */
public final class OptionsButtons implements AutoTuner.Listener {
    private static final int SPACING = 4;
    private static final int AUTO_WIDTH = 150;
    private static final int RESTORE_WIDTH = 54;
    private static final int TUNE_WIDTH = 54;
    private static final int DONE_WIDTH = 40;

    private final Button auto = Button.builder(Component.translatable("mmgrw.auto"), button -> onAuto())
            .width(AUTO_WIDTH).tooltip(defaultTooltip()).build();
    private final Button restore = Button.builder(Component.translatable("mmgrw.restore"), button -> onRestore())
            .width(RESTORE_WIDTH).build();
    private final Button tune = Button.builder(Component.translatable("mmgrw.tune_options"), button -> {
        var mc = Minecraft.getInstance();
        GameCompat.setScreen(mc, new TuningScreen(GameCompat.screen(mc)));
    }).width(TUNE_WIDTH).build();
    private int shownPercent = -1;

    private OptionsButtons() {
        restore.active = AutoTuner.canRestore();
        if (AutoTuner.running()) {
            // The screen was rebuilt (for example resized) while a benchmark was running.
            auto.active = false;
            restore.active = false;
            tune.active = false;
            AutoTuner.attach(this);
        }
    }

    /** @param done the footer element the vanilla screen was about to add (its Done button) */
    public static LayoutElement footerRow(LayoutElement done) {
        if (!AutoTuner.buttonsEnabled()) return done;
        AutoTuner.prepare();
        OptionsButtons buttons = new OptionsButtons();
        LinearLayout row = LinearLayout.horizontal().spacing(SPACING);
        row.addChild(buttons.auto);
        row.addChild(buttons.restore);
        row.addChild(buttons.tune);
        if (done instanceof Button doneButton) doneButton.setWidth(DONE_WIDTH);
        row.addChild(done);
        return row;
    }

    private void onAuto() {
        auto.active = false;
        restore.active = false;
        tune.active = false;
        AutoTuner.start(this);
    }

    @Override
    public void progress(int percent) {
        if (percent == shownPercent) return;
        shownPercent = percent;
        auto.setMessage(Component.translatable("mmgrw.benchmarking", percent));
    }

    @Override
    public void finished(Optional<AutoTuner.Result> result) {
        auto.active = true;
        tune.active = true;
        auto.setMessage(Component.translatable(result.isPresent() ? "mmgrw.applied" : "mmgrw.failed"));
        auto.setTooltip(result.map(OptionsButtons::summary).orElseGet(OptionsButtons::defaultTooltip));
        restore.setMessage(Component.translatable("mmgrw.restore"));
        restore.active = AutoTuner.canRestore();
    }

    @Override
    public void interrupted() {
        auto.active = true;
        tune.active = true;
        auto.setMessage(Component.translatable("mmgrw.interrupted"));
        auto.setTooltip(defaultTooltip());
        restore.active = AutoTuner.canRestore();
    }

    private void onRestore() {
        boolean restored = AutoTuner.restore();
        restore.setMessage(Component.translatable(restored ? "mmgrw.restored" : "mmgrw.restore_failed"));
        restore.active = AutoTuner.canRestore();
        if (restored) {
            auto.setMessage(Component.translatable("mmgrw.auto"));
            auto.setTooltip(defaultTooltip());
        }
    }

    /** The standing explanation, plus a nudge when the hardware differs from the last tune's. */
    private static Tooltip defaultTooltip() {
        MutableComponent text = Component.translatable("mmgrw.tooltip");
        Set<HardwareChange> changed = SettingsReview.hardwareChangedSinceLastTune();
        if (!changed.isEmpty()) {
            line(text, Component.translatable("mmgrw.hardware_changed", ComponentUtils.formatList(changed,
                    Component.literal(", "), aspect -> Component.translatable(key("change", aspect)))));
        }
        return Tooltip.create(text);
    }

    /** Tells the player which profile was chosen, on what evidence, and what held it back if it fell short of the top. */
    private static Tooltip summary(AutoTuner.Result result) {
        TuningPlan plan = result.plan();
        MutableComponent text = Component.translatable("mmgrw.summary", Component.translatable(key("tier", plan.tier())));
        if (plan.limit() != TuningPlan.Limit.NONE) line(text, Component.translatable(key("limit", plan.limit())));
        switch (result.basis()) {
            case NO_WORLD, UNAVAILABLE -> line(text, Component.translatable(key("basis", result.basis())));
            case BENCHMARKED -> {
                if (result.stats().frames() > 0) {
                    line(text, Component.translatable(key("basis", result.basis()), Math.round(result.stats().averageFps())));
                }
            }
            case HARDWARE -> { }
            case GAMEPLAY -> line(text, Component.translatable("mmgrw.basis.gameplay"));
        }
        line(text, plan.fpsLimit() >= Options.UNLIMITED_FRAMERATE_CUTOFF
                ? Component.translatable("mmgrw.summary.unlimited")
                : Component.translatable("mmgrw.summary.cap", plan.fpsLimit()));
        if (result.basis() == AutoTuner.Basis.BENCHMARKED) {
            line(text, Component.translatable(result.targetMet() ? "mmgrw.target.met" : "mmgrw.target.missed"));
            if (result.shaderPack()) line(text, Component.translatable("mmgrw.shader_pack"));
        }
        return Tooltip.create(text);
    }

    private static void line(MutableComponent text, Component next) {
        text.append(CommonComponents.NEW_LINE).append(next);
    }

    private static String key(String group, Enum<?> value) {
        return "mmgrw." + group + "." + value.name().toLowerCase(Locale.ROOT);
    }
}
