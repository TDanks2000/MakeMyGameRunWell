package dev.makemygamerunwell.mixin;

import dev.makemygamerunwell.ui.OptionsButtons;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin {
    /** Swaps the footer's lone Done button for a row that also holds our buttons. */
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToFooter(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"), index = 0)
    private LayoutElement mmgrw$addButtons(LayoutElement done) {
        return OptionsButtons.footerRow(done);
    }
}
