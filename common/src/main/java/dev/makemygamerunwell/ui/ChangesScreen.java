package dev.makemygamerunwell.ui;

import dev.makemygamerunwell.AutoTuner;
import dev.makemygamerunwell.GameCompat;
import dev.makemygamerunwell.SettingsReview;
import dev.makemygamerunwell.core.SettingChange;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Every setting the mod changed, each with the player's original value and a button to put just that one back. */
public final class ChangesScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LIST_TOP = 32;
    private static final int FOOTER_HEIGHT = 56;
    private static final int REVERT_WIDTH = 60;
    private static final int PAGE_BUTTON_WIDTH = 20;
    private static final int MAX_WIDTH = 340;
    private static final int TEXT_PADDING = 6;
    private static final int TEXT_OFFSET_Y = 6;
    private static final int SIDE_MARGIN = 20;
    private static final int TITLE_Y = 10;
    private static final int FOOTER_BUTTON_OFFSET = 28;
    private static final int STATUS_OFFSET = 2;
    private static final int COLUMN_GAP = 4;
    private final Screen parent;
    private List<SettingChange> changes = List.of();
    private int page;
    private int rowsPerPage = 1;
    private Component status = Component.empty();

    public ChangesScreen(Screen parent) {
        super(Component.translatable("mmgrw.changes.title"));
        this.parent = parent;
    }

    private int listWidth() {
        return Math.min(MAX_WIDTH, width - SIDE_MARGIN);
    }

    private int left() {
        return (width - listWidth()) / 2;
    }

    private int pageCount() {
        return Math.max(1, (changes.size() + rowsPerPage - 1) / rowsPerPage);
    }

    @Override
    protected void init() {
        changes = SettingsReview.changedSettings();
        rowsPerPage = Math.max(1, (height - LIST_TOP - FOOTER_HEIGHT) / ROW_HEIGHT);
        page = Math.min(page, pageCount() - 1);
        int right = left() + listWidth();
        int first = page * rowsPerPage;
        for (int row = 0; row < Math.min(rowsPerPage, changes.size() - first); row++) {
            SettingChange change = changes.get(first + row);
            addRenderableWidget(Button.builder(Component.translatable("mmgrw.changes.revert"), button -> revert(change))
                    .bounds(right - REVERT_WIDTH, LIST_TOP + row * ROW_HEIGHT, REVERT_WIDTH, BUTTON_HEIGHT).build());
        }
        int footer = height - FOOTER_BUTTON_OFFSET;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                .bounds(left(), footer - ROW_HEIGHT, PAGE_BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                .bounds(right - PAGE_BUTTON_WIDTH, footer - ROW_HEIGHT, PAGE_BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = page < pageCount() - 1;
        int half = listWidth() / 2 - COLUMN_GAP / 2;
        addRenderableWidget(Button.builder(Component.translatable("mmgrw.changes.copy"), button -> copyReport())
                .bounds(left(), footer, half, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(right - half, footer, half, BUTTON_HEIGHT).build());
    }

    private void turn(int direction) {
        page += direction;
        rebuildWidgets();
    }

    private void revert(SettingChange change) {
        status = Component.translatable(AutoTuner.revert(change.option()) ? "mmgrw.changes.reverted" : "mmgrw.changes.revert_failed");
        rebuildWidgets();
    }

    private void copyReport() {
        try {
            minecraft.keyboardHandler.setClipboard(SettingsReview.report());
            status = Component.translatable("mmgrw.changes.copied");
        } catch (RuntimeException e) {
            status = Component.translatable("mmgrw.changes.copy_failed");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, TITLE_Y, -1);
        if (changes.isEmpty()) {
            graphics.centeredText(font, Component.translatable("mmgrw.changes.none"), width / 2, LIST_TOP + TEXT_OFFSET_Y, -1);
        }
        int first = page * rowsPerPage;
        for (int row = 0; row < Math.min(rowsPerPage, changes.size() - first); row++) {
            SettingChange change = changes.get(first + row);
            Component line = Component.translatable("mmgrw.changes.row", Component.translatableWithFallback(
                    "mmgrw.option." + change.option(), change.option()), change.original(), change.current());
            graphics.text(font, line, left() + TEXT_PADDING, LIST_TOP + row * ROW_HEIGHT + TEXT_OFFSET_Y, -1);
        }
        graphics.centeredText(font, status, width / 2, height - FOOTER_HEIGHT + STATUS_OFFSET, -1);
    }

    @Override
    public void onClose() {
        GameCompat.setScreen(minecraft, parent);
    }
}
