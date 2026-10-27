package dev.makemygamerunwell.mixin;

import dev.makemygamerunwell.smoke.SmokeTest;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Simulates a silently failed options save in the isolated client test. Never shipped. */
@Mixin(Options.class)
public class SmokeSaveMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void mmgrw$skipSave(CallbackInfo ci) {
        if (SmokeTest.skipSave()) ci.cancel();
    }
}
