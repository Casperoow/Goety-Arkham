package com.casper.goetyarkham.willpower;

import com.Polarice3.Goety.init.ModAttributes;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.stats.IPlayerStats;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side effects derived from the authoritative willpower stat.
 */
public final class WillpowerEffects {
    public static final UUID SPELL_POTENCY_MODIFIER_ID =
            UUID.fromString("9e108d55-3402-4264-bfc0-8e7348423259");

    private static final String SPELL_POTENCY_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".willpower_spell_potency";

    private WillpowerEffects() {
    }

    public static double calculateSpellPotencyContribution(int willpower) {
        return willpower * 2.0D;
    }

    public static int calculateSoulCapacityContribution(int willpower) {
        long contribution = (long) willpower * 10L;
        return (int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, contribution));
    }

    public static void refreshWillpowerEffects(ServerPlayer player) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (capability.isEmpty()) {
            return;
        }

        int willpower = capability.get().get(StatType.WILLPOWER).finalValue();
        AttributeInstance potency = player.getAttribute(ModAttributes.SPELL_POTENCY.get());
        if (potency != null) {
            updateSpellPotencyModifier(potency, willpower);
        } else {
            GoetyArkham.LOGGER.warn(
                    "[WillpowerEffects] Player is missing goety:spell_potency: player={}, uuid={}",
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
        }

        refreshSpellPowerMirror(player);

        SoulEnergyPoolService.refresh(player);
    }

    static void updateSpellPotencyModifier(
            AttributeInstance potency, int willpower) {
        potency.removeModifier(SPELL_POTENCY_MODIFIER_ID);
        double contribution = calculateSpellPotencyContribution(willpower);
        if (Double.compare(contribution, 0.0D) != 0) {
            potency.addTransientModifier(new AttributeModifier(
                    SPELL_POTENCY_MODIFIER_ID,
                    SPELL_POTENCY_MODIFIER_NAME,
                    contribution,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }

    public static void refreshSpellPowerMirror(ServerPlayer player) {
        AttributeInstance display =
                player.getAttribute(com.casper.goetyarkham.attribute.ModAttributes.SPELL_POWER.get());
        if (display != null) {
            double actualPotency = Math.max(0.0D,
                    player.getAttributeValue(ModAttributes.SPELL_POTENCY.get()));
            if (Double.compare(display.getBaseValue(), actualPotency) != 0) {
                display.setBaseValue(actualPotency);
            }
        }
    }
}
