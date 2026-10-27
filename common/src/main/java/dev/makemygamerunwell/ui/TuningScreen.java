package dev.makemygamerunwell.ui;

import dev.makemygamerunwell.AutoTuner;
import dev.makemygamerunwell.GameCompat;
import dev.makemygamerunwell.SettingsReview;
import dev.makemygamerunwell.core.HeapAdvice;
import dev.makemygamerunwell.core.TuningPreferences;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Preferences and explicit actions for read-only gameplay learning. */
public final class TuningScreen extends Screen {
    private static final int[] TARGETS = {TuningPreferences.DISPLAY_REFRESH, TuningPreferences.MIN_TARGET, 60, 90, 120, 144, 165,
            TuningPreferences.MAX_TARGET};
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    private static final int LAST_ACTION_ROW = 4;
    private static final int LINE_HEIGHT = 12;
    private static final int MAX_BUTTON_WIDTH = 300;
    private static final int MARGIN = 10;
    private static final int TOP_MIN = 28;
    private static final int CONTENT_HEIGHT = 210;
    private static final int BOTTOM_ROW_OFFSET = 28;
    private static final int STATUS_OFFSET = 42;
    private static final int RESULT_GAP = 4;
    private static final int COLUMN_GAP = 4;
    private final Screen parent;
    private TuningPreferences preferences;
    private TuningPreferences saved;
    private Optional<AutoTuner.LearningResult> result;
    private final HeapAdvice memory;
    private Button learn;
    private Button apply;
    private Component status = Component.empty();

    public TuningScreen(Screen parent) {
        super(Component.translatable("mmgrw.tuning.title"));
        this.parent = parent;
        preferences = AutoTuner.configuration().preferences();
        saved = preferences;
        result = AutoTuner.learningResult();
        memory = SettingsReview.memoryAdvice();
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(MAX_BUTTON_WIDTH, width - 2 * MARGIN);
        int left = (width - buttonWidth) / 2;
        int halfWidth = buttonWidth / 2 - COLUMN_GAP / 2;
        int top = top();
        Button goal = Button.builder(goalLabel(), button -> {
            var goals = TuningPreferences.Goal.values();
            preferences = new TuningPreferences(goals[(preferences.goal().ordinal() + 1) % goals.length], preferences.targetFps());
            button.setMessage(goalLabel());
            button.setTooltip(goalTooltip());
            refreshActions();
        }).bounds(left, top, buttonWidth, BUTTON_HEIGHT).tooltip(goalTooltip()).build();
        addRenderableWidget(goal);
        addRenderableWidget(Button.builder(targetLabel(), button -> {
            preferences = new TuningPreferences(preferences.goal(), nextTarget(preferences.targetFps()));
            button.setMessage(targetLabel());
            refreshActions();
        }).bounds(left, top + ROW_SPACING, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mmgrw.target.tooltip"))).build());
        learn = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (AutoTuner.learning()) {
                AutoTuner.cancelLearning();
                status = Component.translatable("mmgrw.learning.stopped");
                refreshActions();
            } else if (save() && AutoTuner.startLearning()) {
                result = Optional.empty();
                GameCompat.setScreen(minecraft, null);
            } else {
                status = Component.translatable("mmgrw.learning.unavailable");
            }
        }).bounds(left, top + ROW_SPACING * 2, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mmgrw.learning.tooltip"))).build());
        apply = addRenderableWidget(Button.builder(Component.translatable("mmgrw.learning.apply"), button -> {
            boolean applied = AutoTuner.applyLearning().isPresent();
            status = Component.translatable(applied ? "mmgrw.learning.applied" : "mmgrw.learning.stale");
            result = Optional.empty();
            refreshActions();
        }).bounds(left, top + ROW_SPACING * 3, buttonWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("mmgrw.changes.open"), button ->
                GameCompat.setScreen(minecraft, new ChangesScreen(this))).bounds(left, top + ROW_SPACING * LAST_ACTION_ROW, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("mmgrw.changes.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("mmgrw.preferences.save"), button -> {
            if (save()) onClose();
        }).bounds(left, height - BOTTOM_ROW_OFFSET, halfWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + buttonWidth - halfWidth, height - BOTTOM_ROW_OFFSET, halfWidth, BUTTON_HEIGHT).build());
        refreshActions();
    }

    /** The next entry of {@link #TARGETS} above {@code current}, wrapping to the first. */
    private static int nextTarget(int current) {
        return Arrays.stream(TARGETS).filter(target -> target > current).findFirst().orElse(TARGETS[0]);
    }

    private int top() {
        return Math.max(TOP_MIN, (height - CONTENT_HEIGHT) / 2);
    }

    private boolean save() {
        if (!AutoTuner.savePreferences(preferences)) {
            status = Component.translatable("mmgrw.preferences.failed");
            return false;
        }
        saved = preferences;
        return true;
    }

    private void refreshActions() {
        learn.setMessage(Component.translatable(AutoTuner.learning() ? "mmgrw.learning.cancel" : "mmgrw.learning.start"));
        learn.active = !AutoTuner.running() && (AutoTuner.learning() || minecraft.level != null);
        apply.active = !AutoTuner.running() && preferences.equals(saved)
                && result.flatMap(value -> value.advice().adjustment()).isPresent();
    }

    @Override
    public void tick() {
        if (AutoTuner.learning()) status = Component.translatable("mmgrw.learning.progress", AutoTuner.learningPercent());
        else if (result.isEmpty()) {
            result = AutoTuner.learningResult();
            refreshActions();
        }
    }

    private Component goalLabel() {
        return Component.translatable("mmgrw.goal", Component.translatable(goalKey()));
    }

    private Tooltip goalTooltip() {
        return Tooltip.create(Component.translatable(goalKey() + ".tooltip"));
    }

    private String goalKey() {
        return "mmgrw.goal." + preferences.goal().name().toLowerCase(Locale.ROOT);
    }

    private Component targetLabel() {
        return preferences.targetFps() == 0 ? Component.translatable("mmgrw.target.display")
                : Component.translatable("mmgrw.target", preferences.targetFps());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, MARGIN, -1);
        int y = top() + ROW_SPACING * (LAST_ACTION_ROW + 1) + RESULT_GAP;
        if (result.isPresent()) {
            var observation = result.get();
            graphics.centeredText(font, Component.translatable("mmgrw.learning.stats", observation.targetFps(), Math.round(observation.stats().averageFps()),
                    Math.round(observation.stats().lowFps())), width / 2, y, -1);
            String key = "mmgrw.learning." + observation.advice().status().name().toLowerCase(Locale.ROOT);
            graphics.centeredText(font, Component.translatable(key), width / 2, y + LINE_HEIGHT, -1);
            observation.advice().adjustment().ifPresent(step -> graphics.centeredText(font,
                    Component.translatable("mmgrw.learning.suggestion", Component.translatable("mmgrw.adjust." + step.name().toLowerCase(Locale.ROOT))),
                    width / 2, y + LINE_HEIGHT * 2, -1));
        }
        memoryLine().ifPresent(line -> graphics.centeredText(font, line, width / 2, y + LINE_HEIGHT * 3, -1));
        graphics.centeredText(font, status, width / 2, height - STATUS_OFFSET, -1);
    }

    private Optional<Component> memoryLine() {
        return switch (memory.status()) {
            case OK -> Optional.empty();
            case TOO_SMALL -> Optional.of(Component.translatable("mmgrw.memory.too_small", memory.suggestedMiB()));
            case TOO_LARGE -> Optional.of(Component.translatable("mmgrw.memory.too_large", memory.suggestedMiB()));
        };
    }

    @Override
    public void onClose() {
        GameCompat.setScreen(minecraft, parent);
    }
}
