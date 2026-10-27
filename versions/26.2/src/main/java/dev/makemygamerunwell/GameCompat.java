package dev.makemygamerunwell;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.DeviceInfo;
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
        return mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    /** Whether a loading overlay (resource reload, for example) is covering the game. */
    public static boolean hasOverlay(Minecraft mc) {
        return mc.gui.overlay() != null;
    }

    /** Shows a message above the hotbar. */
    public static void showOverlayMessage(Minecraft mc, Component message) {
        mc.gui.hud.setOverlayMessage(message, false);
    }

    /** Rebuilds the world renderer so changed render settings apply. */
    public static void reloadLevelRenderer(Minecraft mc) {
        mc.levelExtractor.allChanged();
    }

    /** The framebuffer the game draws into, for screenshots. */
    public static RenderTarget mainRenderTarget(Minecraft mc) {
        return mc.gameRenderer.mainRenderTarget();
    }

    public static GpuInfo gpu() {
        DeviceInfo device = RenderSystem.getDevice().getDeviceInfo();
        Hardware.Gpu type = switch (device.type()) {
            case CPU, VIRTUAL -> Hardware.Gpu.SOFTWARE;
            case INTEGRATED -> Hardware.Gpu.INTEGRATED;
            case DISCRETE -> Hardware.Gpu.DISCRETE;
            case OTHER -> Hardware.Gpu.UNKNOWN;
        };
        return new GpuInfo(type, String.valueOf(device.name()));
    }
}
