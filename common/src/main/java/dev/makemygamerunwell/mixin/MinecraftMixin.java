package dev.makemygamerunwell.mixin;

import dev.makemygamerunwell.AutoTuner;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** Tells a running benchmark that a frame is starting. Does nothing otherwise. */
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void mmgrw$frame(boolean advanceGameTime, CallbackInfo ci) {
        AutoTuner.onFrame();
    }
}
