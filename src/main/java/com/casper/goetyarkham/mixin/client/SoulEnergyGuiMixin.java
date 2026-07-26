package com.casper.goetyarkham.mixin.client;

import com.Polarice3.Goety.client.gui.overlay.SoulEnergyGui;
import com.casper.goetyarkham.client.ClientSoulPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = SoulEnergyGui.class, remap = false)
public abstract class SoulEnergyGuiMixin {
    @ModifyVariable(method = "drawHUD", at = @At("STORE"), index = 6)
    private static int goetyarkham$useUnifiedCurrentSoul(int original) {
        return ClientSoulPool.snapshot().currentSoul();
    }

    @ModifyVariable(method = "drawHUD", at = @At("STORE"), index = 7)
    private static int goetyarkham$useUnifiedMaximumSoul(int original) {
        /*
         * Goety divides by this local twice while calculating bar widths.
         * Keep the rendering denominator non-zero; the numeric label below
         * still reports the authoritative zero maximum when appropriate.
         */
        return Math.max(1, ClientSoulPool.snapshot().maximumSoul());
    }

    @ModifyVariable(method = "drawHUD", at = @At("STORE"), index = 13)
    private static String goetyarkham$useUnifiedSoulLabel(String original) {
        return ClientSoulPool.snapshot().currentSoul()
                + " / "
                + ClientSoulPool.snapshot().maximumSoul();
    }
}
