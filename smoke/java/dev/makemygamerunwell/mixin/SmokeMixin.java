package dev.makemygamerunwell.mixin;

import dev.makemygamerunwell.smoke.SmokeTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Starts {@link SmokeTest} the first time the title screen opens. */
@Mixin(TitleScreen.class)
public class SmokeMixin {
    @Unique private static boolean mmgrw$started;

    @Inject(method = "init", at = @At("TAIL"))
    private void mmgrw$start(CallbackInfo ci) {
        if (mmgrw$started) return;
        mmgrw$started = true;
        SmokeTest.start(Minecraft.getInstance());
    }
}
