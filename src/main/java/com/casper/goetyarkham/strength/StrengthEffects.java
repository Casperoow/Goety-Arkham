package com.casper.goetyarkham.strength;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.attribute.ModAttributes;
import com.casper.goetyarkham.stats.IPlayerStats;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Formulas and server-side effects derived from the authoritative strength stat.
 */
public final class StrengthEffects {
    public static final UUID ATTACK_DAMAGE_MODIFIER_ID =
            UUID.fromString("7f1f0e8c-8f79-4f4c-9d2d-59f75e413a10");

    private static final String ATTACK_DAMAGE_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".strength_attack_damage";

    private StrengthEffects() {
    }

    public static double getAttackDamageBonus(double strength) {
        return strength * 0.5D;
    }

    public static double getRawCriticalChance(double strength) {
        return 50.0D + strength;
    }

    public static double getCriticalChance(double strength) {
        return Math.max(0.0D, Math.min(100.0D, getRawCriticalChance(strength)));
    }

    public static double getOverflowCriticalChance(double strength) {
        return Math.max(0.0D, getRawCriticalChance(strength) - 100.0D);
    }

    public static double getCriticalDamageMultiplier(double strength) {
        return Math.max(
                1.0D,
                1.0D + strength * 0.02D + getOverflowCriticalChance(strength) * 0.05D
        );
    }

    static boolean isCriticalHit(double strength, double unitRoll) {
        double chance = getCriticalChance(strength);
        return chance >= 100.0D
                || (chance > 0.0D && unitRoll * 100.0D < chance);
    }

    /**
     * Replaces the fixed-UUID transient modifier from the current capability
     * value. The modifier is deliberately not persisted as player stat data.
     */
    public static void refreshStrengthEffects(ServerPlayer player) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (capability.isEmpty()) {
            GoetyArkham.LOGGER.debug(
                    "[StrengthEffects] Skipped refresh because PlayerStats is unavailable: player={}, uuid={}",
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
            return;
        }

        double strength = capability.get().get(StatType.STRENGTH).finalValue();
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            GoetyArkham.LOGGER.warn(
                    "[StrengthEffects] Player is missing minecraft.attack_damage: player={}, uuid={}",
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
        } else {
            updateAttackDamageModifier(attackDamage, getAttackDamageBonus(strength));
        }

        AttributeInstance criticalChance = player.getAttribute(ModAttributes.CRITICAL_CHANCE.get());
        if (criticalChance == null) {
            GoetyArkham.LOGGER.warn(
                    "[StrengthEffects] Player is missing goetyarkham:critical_chance: player={}, uuid={}",
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
        }

        AttributeInstance criticalDamage = player.getAttribute(ModAttributes.CRITICAL_DAMAGE.get());
        if (criticalDamage == null) {
            GoetyArkham.LOGGER.warn(
                    "[StrengthEffects] Player is missing goetyarkham:critical_damage: player={}, uuid={}",
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
        }

        updateCriticalDisplayAttributes(criticalChance, criticalDamage, strength);
    }

    static void updateAttackDamageModifier(AttributeInstance attackDamage, double amount) {
        attackDamage.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
        if (Double.compare(amount, 0.0D) != 0) {
            attackDamage.addTransientModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    amount,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }

    static void updateCriticalDisplayAttributes(
            AttributeInstance criticalChance,
            AttributeInstance criticalDamage,
            double strength) {
        if (criticalChance != null) {
            criticalChance.setBaseValue(getCriticalChance(strength));
        }
        if (criticalDamage != null) {
            double criticalDamageDisplayValue = getCriticalDamageMultiplier(strength) * 100.0D;
            criticalDamage.setBaseValue(criticalDamageDisplayValue);
        }
    }
}
