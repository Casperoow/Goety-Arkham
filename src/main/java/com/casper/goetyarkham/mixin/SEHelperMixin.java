package com.casper.goetyarkham.mixin;

import com.Polarice3.Goety.utils.SEHelper;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SEHelper.class, remap = false)
public abstract class SEHelperMixin {
    @Inject(method = "getSoulsContainer", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$getSoulsContainer(
            Player player, CallbackInfoReturnable<Boolean> callback) {
        if (player instanceof ServerPlayer) {
            callback.setReturnValue(SoulEnergyPoolService.hasContainer(player));
        }
    }

    @Inject(method = "getSoulsAmount", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$getSoulsAmount(
            Player player, int amount, CallbackInfoReturnable<Boolean> callback) {
        if (player instanceof ServerPlayer) {
            callback.setReturnValue(SoulEnergyPoolService.hasSoul(player, amount));
        }
    }

    @Inject(method = "getSoulAmountInt", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$getSoulAmountInt(
            Player player, CallbackInfoReturnable<Integer> callback) {
        if (player instanceof ServerPlayer) {
            callback.setReturnValue(SoulEnergyPoolService.getCurrentSoul(player));
        }
    }

    @Inject(method = "setSoulsAmount", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$setSoulsAmount(
            Player player, int amount, CallbackInfo callback) {
        if (player instanceof ServerPlayer) {
            SoulEnergyPoolService.setSoul(player, amount);
            callback.cancel();
        }
    }

    @Inject(method = "increaseSouls", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$increaseSouls(
            Player player, int amount, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) {
            SoulEnergyPoolService.addSoulFromGoety(serverPlayer, amount);
            callback.cancel();
        }
    }

    @Inject(method = "decreaseSouls", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$decreaseSouls(
            Player player, int amount, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) {
            SoulEnergyPoolService.removeSoulFromGoety(serverPlayer, amount);
            callback.cancel();
        }
    }
}
