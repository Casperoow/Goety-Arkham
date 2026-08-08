package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies Sneak Attack's x2 multiplier directly onto the single {@link
 * LivingHurtEvent} of the qualifying hit - fired before armor/resistance
 * reduction and before the target's health is actually subtracted, so
 * {@code event.getEntity().getHealth()} still reflects pre-hit health at
 * the time {@link SneakAttackService#qualifies} checks it. Multiplying the
 * event's own amount here keeps the bonus part of the ordinary
 * damage-resolution pipeline (still reduced by armor, still one hurt()
 * call, one hit animation, one death event, one set of enchantment
 * triggers) instead of cancelling anything or triggering a second,
 * independent hurt() call. See {@link SneakAttackService} for the
 * attacker-resolution and full-health eligibility logic.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class SneakAttackEvents {
    private SneakAttackEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }
        if (SneakAttackService.qualifies(event.getSource(), event.getEntity())) {
            event.setAmount(event.getAmount() * SneakAttackService.DAMAGE_MULTIPLIER);
        }
    }
}
