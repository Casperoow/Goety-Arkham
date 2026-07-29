package com.casper.goetyarkham.revelation;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.stats.IPlayerStats;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies authoritative Player Stats values to Goety: Revelation attributes.
 *
 * <p>Goety: Revelation registers these attributes from its bundled
 * RevelationFix module without exposing those bundled classes on a normal
 * compile classpath, so the bridge uses the verified registry IDs.</p>
 */
public final class RevelationAttributeBridge {
    public static final ResourceLocation SPELL_POWER_ID =
            new ResourceLocation("goety_revelation", "spell_power");
    public static final ResourceLocation SPELL_POWER_MULTIPLIER_ID =
            new ResourceLocation("goety_revelation", "spell_power_multiplier");
    public static final ResourceLocation SOUL_DECREASE_REDUCTION_ID =
            new ResourceLocation("goety_revelation", "soul_decrease_reduction");

    private static final UUID WILLPOWER_SPELL_POWER_MODIFIER_ID =
            UUID.fromString("1b476e56-47bc-4a73-a287-363f7e612e56");
    private static final UUID INTELLECT_SPELL_POWER_MODIFIER_ID =
            UUID.fromString("66905f1f-25ee-4bcf-9d34-f9cfc9e00b5f");
    private static final UUID INTELLECT_SPELL_POWER_MULTIPLIER_MODIFIER_ID =
            UUID.fromString("1d8f0b9c-801d-48c8-b5ca-ce206bb7920b");
    private static final UUID INTELLECT_SOUL_DISCOUNT_MODIFIER_ID =
            UUID.fromString("5574d62d-a2f3-4261-a362-f0ee767cb957");

    private static final String WILLPOWER_SPELL_POWER_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".willpower_spell_power";
    private static final String INTELLECT_SPELL_POWER_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".intellect_spell_power";
    private static final String INTELLECT_SPELL_POWER_MULTIPLIER_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".intellect_spell_power_multiplier";
    private static final String INTELLECT_SOUL_DISCOUNT_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".intellect_soul_discount";

    private RevelationAttributeBridge() {
    }

    public static double calculateSpellPowerContribution(int willpower) {
        return willpower * 2.0D;
    }

    public static double calculateSpellPowerMultiplierContribution(int intellect) {
        return intellect * 0.01D;
    }

    public static double calculateIntellectSoulDiscountContribution(int intellect) {
        return Math.max(0, Math.min(intellect, 50)) * 0.01D;
    }

    public static double calculateIntellectSpellPowerContribution(int intellect) {
        return Math.max(0L, (long) intellect - 50L);
    }

    public static void refresh(ServerPlayer player) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (capability.isEmpty()) {
            return;
        }

        int willpower =
                PlayerStatsService.getFinalValue(player, StatType.WILLPOWER);
        int intellect =
                PlayerStatsService.getFinalValue(player, StatType.INTELLECT);

        AttributeInstance spellPower = getAttributeInstance(player, SPELL_POWER_ID);
        if (spellPower != null) {
            updateSpellPowerModifier(spellPower, willpower);
            updateIntellectSpellPowerModifier(spellPower, intellect);
        }

        AttributeInstance spellPowerMultiplier =
                getAttributeInstance(player, SPELL_POWER_MULTIPLIER_ID);
        if (spellPowerMultiplier != null) {
            updateSpellPowerMultiplierModifier(spellPowerMultiplier, intellect);
        }

        AttributeInstance soulDecreaseReduction =
                getAttributeInstance(player, SOUL_DECREASE_REDUCTION_ID);
        if (soulDecreaseReduction != null) {
            updateIntellectSoulDiscountModifier(soulDecreaseReduction, intellect);
        }
    }

    public static void updateSpellPowerModifier(
            AttributeInstance attribute, int willpower) {
        replaceOwnModifier(
                attribute,
                WILLPOWER_SPELL_POWER_MODIFIER_ID,
                WILLPOWER_SPELL_POWER_MODIFIER_NAME,
                calculateSpellPowerContribution(willpower)
        );
    }

    public static void updateSpellPowerMultiplierModifier(
            AttributeInstance attribute, int intellect) {
        replaceOwnModifier(
                attribute,
                INTELLECT_SPELL_POWER_MULTIPLIER_MODIFIER_ID,
                INTELLECT_SPELL_POWER_MULTIPLIER_MODIFIER_NAME,
                calculateSpellPowerMultiplierContribution(intellect)
        );
    }

    public static void updateIntellectSpellPowerModifier(
            AttributeInstance attribute, int intellect) {
        replaceOwnModifier(
                attribute,
                INTELLECT_SPELL_POWER_MODIFIER_ID,
                INTELLECT_SPELL_POWER_MODIFIER_NAME,
                calculateIntellectSpellPowerContribution(intellect)
        );
    }

    public static void updateIntellectSoulDiscountModifier(
            AttributeInstance attribute, int intellect) {
        replaceOwnModifier(
                attribute,
                INTELLECT_SOUL_DISCOUNT_MODIFIER_ID,
                INTELLECT_SOUL_DISCOUNT_MODIFIER_NAME,
                calculateIntellectSoulDiscountContribution(intellect)
        );
    }

    private static AttributeInstance getAttributeInstance(
            ServerPlayer player, ResourceLocation attributeId) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
        if (attribute == null) {
            GoetyArkham.LOGGER.error(
                    "[RevelationAttributeBridge] Required Goety: Revelation attribute is not registered: attribute={}, player={}, uuid={}",
                    attributeId,
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
            return null;
        }

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            GoetyArkham.LOGGER.error(
                    "[RevelationAttributeBridge] Player is missing required Goety: Revelation AttributeInstance: attribute={}, player={}, uuid={}",
                    attributeId,
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
        }
        return instance;
    }

    private static void replaceOwnModifier(
            AttributeInstance attribute,
            UUID modifierId,
            String modifierName,
            double contribution) {
        attribute.removeModifier(modifierId);
        if (Double.compare(contribution, 0.0D) != 0) {
            attribute.addTransientModifier(new AttributeModifier(
                    modifierId,
                    modifierName,
                    contribution,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }
}
