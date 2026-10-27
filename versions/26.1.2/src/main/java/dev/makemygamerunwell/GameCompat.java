package dev.makemygamerunwell;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.makemygamerunwell.core.Hardware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Minecraft calls that differ between game versions. Each directory under {@code versions/} supplies its own
 * class of this name with the same methods, so the shared code never mentions a version.
 */
public final class GameCompat {
    private GameCompat() {}

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    /** Whether a loading overlay (resource reload, for example) is covering the game. */
    public static boolean hasOverlay(Minecraft mc) {
        return mc.getOverlay() != null;
    }

    /** Shows a message above the hotbar. */
    public static void showOverlayMessage(Minecraft mc, Component message) {
        mc.gui.setOverlayMessage(message, false);
    }

    /** Rebuilds the world renderer so changed render settings apply. */
    public static void reloadLevelRenderer(Minecraft mc) {
        mc.levelRenderer.allChanged();
    }

    /** The framebuffer the game draws into, for screenshots. */
    public static RenderTarget mainRenderTarget(Minecraft mc) {
        return mc.getMainRenderTarget();
    }

    /** This game version reports no device type, so classification rests on the device name alone. */
    public static GpuInfo gpu() {
        return new GpuInfo(Hardware.Gpu.UNKNOWN, String.valueOf(RenderSystem.getDevice().getRenderer()));
    }
}
