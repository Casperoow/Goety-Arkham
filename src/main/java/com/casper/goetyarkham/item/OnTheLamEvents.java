package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Breaks On the Lam's True Invisibility (and starts its cooldown) the
 * instant its wearer deals or takes real damage, and denies hostile AI a
 * new lock onto a currently True-Invisible player. See {@link
 * OnTheLamService} for the state machine itself and {@link
 * SneakAttackService#resolveAttackingPlayer} for the shared
 * attacker-resolution helper reused here. Look-at and target-reacquisition
 * blocking for already-invisible players is handled separately by the
 * {@code TargetingConditions} mixin.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class OnTheLamEvents {
    private OnTheLamEvents() {
    }

    /**
     * Fires before the target's health is actually reduced, and is never
     * delivered for an already-canceled event, so an amount of zero (or a
     * fully canceled hit) never breaks stealth.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }
        SneakAttackService.resolveAttackingPlayer(event.getSource())
                .ifPresent(OnTheLamService::breakTrueInvisibility);
        if (event.getEntity() instanceof ServerPlayer victim) {
            OnTheLamService.breakTrueInvisibility(victim);
        }
    }

    /** Cancelling leaves the mob's target unchanged instead of letting it lock onto a True-Invisible player. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player
                && OnTheLamService.hasTrueInvisibility(player)) {
            event.setCanceled(true);
        }
    }
}
