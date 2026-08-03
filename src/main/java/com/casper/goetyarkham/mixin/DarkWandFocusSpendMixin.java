package com.casper.goetyarkham.mixin;

import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.common.items.magic.DarkWand;
import com.casper.goetyarkham.soul.FocusCastSoulSpendTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Narrows actual soul-spend accounting to Goety focus spell settlement. */
@Mixin(value = DarkWand.class, remap = false)
public abstract class DarkWandFocusSpendMixin {
    @Inject(
            method = "MagicResults(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lcom/Polarice3/Goety/api/magic/ISpell;)V",
            at = @At("HEAD"))
    private void goetyarkham$beginFocusSettlement(
            ItemStack wand,
            Level level,
            LivingEntity caster,
            ISpell spell,
            CallbackInfo callback) {
        if (caster instanceof ServerPlayer player) {
            FocusCastSoulSpendTracker.begin(player);
        }
    }

    @Inject(
            method = "MagicResults(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lcom/Polarice3/Goety/api/magic/ISpell;)V",
            at = @At("RETURN"))
    private void goetyarkham$finishFocusSettlement(
            ItemStack wand,
            Level level,
            LivingEntity caster,
            ISpell spell,
            CallbackInfo callback) {
        if (caster instanceof ServerPlayer player) {
            FocusCastSoulSpendTracker.finish(player);
        }
    }

    @Inject(method = "canCastTouch", at = @At("HEAD"))
    private void goetyarkham$beginTouchFocusSettlement(
            ItemStack wand,
            Level level,
            LivingEntity caster,
            LivingEntity target,
            CallbackInfoReturnable<Boolean> callback) {
        if (caster instanceof ServerPlayer player) {
            FocusCastSoulSpendTracker.begin(player);
        }
    }

    @Inject(method = "canCastTouch", at = @At("RETURN"))
    private void goetyarkham$finishTouchFocusSettlement(
            ItemStack wand,
            Level level,
            LivingEntity caster,
            LivingEntity target,
            CallbackInfoReturnable<Boolean> callback) {
        if (caster instanceof ServerPlayer player) {
            FocusCastSoulSpendTracker.finish(player);
        }
    }
}
