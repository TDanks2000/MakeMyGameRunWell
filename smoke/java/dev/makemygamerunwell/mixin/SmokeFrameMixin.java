package dev.makemygamerunwell.mixin;

import dev.makemygamerunwell.smoke.SmokeTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Injects a deterministic slow frame only during the opt-in learning test. Never shipped. */
@Mixin(Minecraft.class)
public class SmokeFrameMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void mmgrw$delayFrame(boolean advanceGameTime, CallbackInfo ci) {
        SmokeTest.delayFrame();
    }
}
