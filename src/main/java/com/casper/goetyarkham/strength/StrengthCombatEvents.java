package com.casper.goetyarkham.strength;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Replaces vanilla critical-hit eligibility and damage for direct player
 * attacks. Forge only fires CriticalHitEvent from Player#attack(Entity), so
 * projectile, gun, and spell damage paths never enter this handler.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class StrengthCombatEvents {
    private StrengthCombatEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCriticalHit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerStatsService.get(player).ifPresent(stats -> {
            double strength = PlayerStatsService.getFinalValue(
                    player, StatType.STRENGTH);
            boolean critical = StrengthEffects.isCriticalHit(
                    strength,
                    player.getRandom().nextDouble()
            );

            if (critical) {
                event.setDamageModifier((float) StrengthEffects.getCriticalDamageMultiplier(strength));
                event.setResult(Event.Result.ALLOW);
            } else {
                event.setResult(Event.Result.DENY);
            }
        });
    }
}
