package com.casper.goetyarkham.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared formatting for the player-visible effects of worn Curios. */
public final class CurioTooltipHelper {
    public static final String WHEN_WORN_TRANSLATION_KEY =
            "tooltip.goetyarkham.when_worn";
    public static final String ATTRIBUTE_BONUS_TRANSLATION_KEY =
            "tooltip.goetyarkham.attribute_bonus";
    public static final String ATTRIBUTE_BONUS_PERCENT_TRANSLATION_KEY =
            "tooltip.goetyarkham.attribute_bonus_percent";

    private CurioTooltipHelper() {
    }

    public static void appendSlot(
            List<Component> tooltip, String slotTranslationKey) {
        tooltip.add(Component.translatable(slotTranslationKey)
                .withStyle(ChatFormatting.GRAY));
    }

    public static void appendWhenWorn(
            List<Component> tooltip,
            String effectTranslationKey) {
        appendWhenWorn(tooltip, Component.translatable(effectTranslationKey));
    }

    public static void appendWhenWorn(
            List<Component> tooltip,
            Component... effects) {
        tooltip.add(Component.translatable(WHEN_WORN_TRANSLATION_KEY)
                .withStyle(ChatFormatting.YELLOW));
        for (Component effect : effects) {
            tooltip.add(effect.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    public static Component attributeBonus(int amount, String attributeTranslationKey) {
        return Component.translatable(
                ATTRIBUTE_BONUS_TRANSLATION_KEY,
                amount,
                Component.translatable(attributeTranslationKey));
    }

    public static Component attributeBonusPercent(int percent, String attributeTranslationKey) {
        return Component.translatable(
                ATTRIBUTE_BONUS_PERCENT_TRANSLATION_KEY,
                percent,
                Component.translatable(attributeTranslationKey));
    }
}
