package com.casper.goetyarkham.mixin.client;

import com.Polarice3.Goety.utils.SEHelper;
import com.casper.goetyarkham.client.ClientSoulPool;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SEHelper.class, remap = false)
public abstract class SEHelperClientMixin {
    @Inject(method = "getSoulsContainer", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$getSoulsContainer(
            Player player,
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(ClientSoulPool.snapshot().hasContainer());
    }

    @Inject(method = "getSoulsAmount", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$getSoulsAmount(
            Player player,
            int amount,
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(
                ClientSoulPool.snapshot().hasContainer()
                        && ClientSoulPool.snapshot().currentSoul() >= amount
        );
    }

    @Inject(method = "getSoulAmountInt", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$getSoulAmountInt(
            Player player,
            CallbackInfoReturnable<Integer> callback
    ) {
        callback.setReturnValue(ClientSoulPool.snapshot().currentSoul());
    }
}
