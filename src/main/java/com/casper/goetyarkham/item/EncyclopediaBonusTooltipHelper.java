package com.casper.goetyarkham.item;

import com.casper.goetyarkham.stats.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Formats {@link EncyclopediaTooltipBonus} into the Shift-revealed
 * "current bonus" / "when equipped" block of {@link EncyclopediaItem}'s
 * tooltip. Pure formatting only - every number it prints comes from {@link
 * EncyclopediaBonusProvider#computeBonus}, the same rule used for the
 * player's actual stats, so this can never drift from what the Encyclopedia
 * really grants.
 */
public final class EncyclopediaBonusTooltipHelper {
    static final String CURRENT_BONUS_HEADING_KEY =
            "tooltip.goetyarkham.encyclopedia.current_bonus_heading";
    static final String WHEN_EQUIPPED_HEADING_KEY =
            "tooltip.goetyarkham.encyclopedia.when_equipped_heading";
    static final String NONE_KEY = "tooltip.goetyarkham.encyclopedia.none";

    /** Fixed display order: Strength, Agility, Willpower, Intellect. */
    private static final List<StatType> DISPLAY_ORDER = List.of(
            StatType.STRENGTH, StatType.AGILITY, StatType.WILLPOWER, StatType.INTELLECT);
    private static final Map<StatType, String> ATTRIBUTE_KEYS = Map.of(
            StatType.STRENGTH, "attribute.name.goetyarkham.strength",
            StatType.AGILITY, "attribute.name.goetyarkham.agility",
            StatType.WILLPOWER, "attribute.name.goetyarkham.willpower",
            StatType.INTELLECT, "attribute.name.goetyarkham.intellect");

    private EncyclopediaBonusTooltipHelper() {
    }

    /**
     * Appends either the "current bonus" block (when the viewing player has
     * an Encyclopedia equipped) or the "when equipped" prediction block
     * (when they do not), matching the same resource-slot contents either
     * way. {@code bonus.equipped()} is what selects between them - not
     * whether the specific hovered {@code ItemStack} is the equipped one.
     */
    public static void append(List<Component> tooltip, EncyclopediaTooltipBonus bonus) {
        if (!bonus.available()) {
            tooltip.add(headingLine(CURRENT_BONUS_HEADING_KEY, none()));
            return;
        }
        List<Component> lines = bonusLines(bonus.bonuses());
        String headingKey = bonus.equipped() ? CURRENT_BONUS_HEADING_KEY : WHEN_EQUIPPED_HEADING_KEY;
        appendSection(tooltip, headingKey, lines);
    }

    private static void appendSection(
            List<Component> tooltip,
            String headingKey,
            List<Component> lines) {
        if (lines.isEmpty()) {
            tooltip.add(headingLine(headingKey, none()));
        } else {
            tooltip.add(headingLine(headingKey, null));
            tooltip.addAll(lines);
        }
    }

    private static List<Component> bonusLines(Map<StatType, Integer> bonuses) {
        List<Component> lines = new ArrayList<>();
        for (StatType stat : DISPLAY_ORDER) {
            int amount = bonuses.getOrDefault(stat, 0);
            if (amount == 0) {
                continue;
            }
            lines.add(CurioTooltipHelper.attributeBonus(amount, ATTRIBUTE_KEYS.get(stat))
                    .copy().withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }

    private static Component headingLine(String headingKey, Component inlineValue) {
        MutableComponent heading = Component.translatable(headingKey).withStyle(ChatFormatting.YELLOW);
        if (inlineValue == null) {
            return heading;
        }
        return heading.append(Component.literal(" ")).append(inlineValue);
    }

    private static Component none() {
        return Component.translatable(NONE_KEY).withStyle(ChatFormatting.GRAY);
    }
}
