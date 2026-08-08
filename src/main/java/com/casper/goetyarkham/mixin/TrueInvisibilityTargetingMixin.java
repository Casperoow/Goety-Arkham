package com.casper.goetyarkham.mixin;

import com.casper.goetyarkham.effect.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a player under On the Lam's True Invisibility (see {@code
 * com.casper.goetyarkham.item.OnTheLamService}) an invalid candidate for
 * every vanilla AI selection routed through {@link TargetingConditions} -
 * new target acquisition ({@code NearestAttackableTargetGoal} and similar)
 * and look-at target selection ({@code LookAtPlayerGoal}) alike - instead of
 * only lowering detection odds the way the vanilla invisibility potion does.
 * Already-acquired targets are cleared separately, once, at the moment True
 * Invisibility activates (see {@code OnTheLamService#activateTrueInvisibility}),
 * since this hook only ever prevents a *new* lock.
 */
@Mixin(TargetingConditions.class)
public abstract class TrueInvisibilityTargetingMixin {
    @Inject(
            method = "test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void goetyarkham$rejectTrueInvisiblePlayers(
            LivingEntity attacker,
            LivingEntity target,
            CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof ServerPlayer player
                && ModEffects.TRUE_INVISIBILITY.isPresent()
                && player.hasEffect(ModEffects.TRUE_INVISIBILITY.get())) {
            cir.setReturnValue(false);
        }
    }
}
