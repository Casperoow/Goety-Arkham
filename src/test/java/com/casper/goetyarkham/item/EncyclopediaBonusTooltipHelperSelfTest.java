package com.casper.goetyarkham.item;

import com.casper.goetyarkham.stats.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic pure-logic tests for {@link EncyclopediaBonusTooltipHelper}
 * and {@link EncyclopediaBonusProvider#computeBonus}: the Shift-revealed
 * "current bonus" / "when equipped" block of the Encyclopedia's tooltip.
 */
public final class EncyclopediaBonusTooltipHelperSelfTest {
    private EncyclopediaBonusTooltipHelperSelfTest() {
    }

    public static void main(String[] args) {
        equippedNonEmptyShowsHeadingThenOrderedLines();
        equippedAllZeroShowsCurrentBonusNone();
        zeroAttributesAreSuppressed();
        notEquippedWithPredictionShowsWhenEquippedLines();
        notEquippedWithNoPredictionShowsWhenEquippedNone();
        unavailableShowsCurrentBonusNone();
        displayOrderIsStrengthAgilityWillpowerIntellect();
        System.out.println("EncyclopediaBonusTooltipHelperSelfTest: all checks passed");
    }

    private static void equippedNonEmptyShowsHeadingThenOrderedLines() {
        Map<StatType, Integer> bonuses = totals(0, 2, 2, 2);
        List<Component> tooltip = new ArrayList<>();
        EncyclopediaBonusTooltipHelper.append(
                tooltip, new EncyclopediaTooltipBonus(true, true, bonuses));

        assertEquals(4, tooltip.size(), "heading + 3 non-zero lines");
        assertHeadingAlone(tooltip.get(0), EncyclopediaBonusTooltipHelper.CURRENT_BONUS_HEADING_KEY);
        assertAttributeLine(tooltip.get(1), "attribute.name.goetyarkham.agility");
        assertAttributeLine(tooltip.get(2), "attribute.name.goetyarkham.willpower");
        assertAttributeLine(tooltip.get(3), "attribute.name.goetyarkham.intellect");
    }

    private static void equippedAllZeroShowsCurrentBonusNone() {
        Map<StatType, Integer> bonuses = totals(0, 0, 0, 0);
        List<Component> tooltip = new ArrayList<>();
        EncyclopediaBonusTooltipHelper.append(
                tooltip, new EncyclopediaTooltipBonus(true, true, bonuses));

        assertEquals(1, tooltip.size(), "single combined line when all bonuses are zero");
        assertHeadingWithInlineKey(
                tooltip.get(0),
                EncyclopediaBonusTooltipHelper.CURRENT_BONUS_HEADING_KEY,
                EncyclopediaBonusTooltipHelper.NONE_KEY);
    }

    private static void zeroAttributesAreSuppressed() {
        Map<StatType, Integer> bonuses = totals(0, 0, 0, 6);
        List<Component> tooltip = new ArrayList<>();
        EncyclopediaBonusTooltipHelper.append(
                tooltip, new EncyclopediaTooltipBonus(true, true, bonuses));

        assertEquals(2, tooltip.size(), "heading + exactly one non-zero line");
        assertAttributeLine(tooltip.get(1), "attribute.name.goetyarkham.intellect");
    }

    private static void notEquippedWithPredictionShowsWhenEquippedLines() {
        Map<StatType, Integer> bonuses = totals(2, 0, 0, 4);
        List<Component> tooltip = new ArrayList<>();
        EncyclopediaBonusTooltipHelper.append(
                tooltip, new EncyclopediaTooltipBonus(true, false, bonuses));

        assertEquals(3, tooltip.size(), "when-equipped heading + 2 predicted lines");
        assertHeadingAlone(
                tooltip.get(0), EncyclopediaBonusTooltipHelper.WHEN_EQUIPPED_HEADING_KEY);
        assertAttributeLine(tooltip.get(1), "attribute.name.goetyarkham.strength");
        assertAttributeLine(tooltip.get(2), "attribute.name.goetyarkham.intellect");
    }

    private static void notEquippedWithNoPredictionShowsWhenEquippedNone() {
        Map<StatType, Integer> bonuses = totals(0, 0, 0, 0);
        List<Component> tooltip = new ArrayList<>();
        EncyclopediaBonusTooltipHelper.append(
                tooltip, new EncyclopediaTooltipBonus(true, false, bonuses));

        assertEquals(1, tooltip.size(), "single when-equipped-none line");
        assertHeadingWithInlineKey(
                tooltip.get(0),
                EncyclopediaBonusTooltipHelper.WHEN_EQUIPPED_HEADING_KEY,
                EncyclopediaBonusTooltipHelper.NONE_KEY);
    }

    private static void unavailableShowsCurrentBonusNone() {
        List<Component> tooltip = new ArrayList<>();
        EncyclopediaBonusTooltipHelper.append(tooltip, EncyclopediaTooltipBonus.UNAVAILABLE);

        assertEquals(1, tooltip.size(), "no local player still yields a single safe line");
        assertHeadingWithInlineKey(
                tooltip.get(0),
                EncyclopediaBonusTooltipHelper.CURRENT_BONUS_HEADING_KEY,
                EncyclopediaBonusTooltipHelper.NONE_KEY);
    }

    private static void displayOrderIsStrengthAgilityWillpowerIntellect() {
        Map<StatType, Integer> bonuses = totals(2, 2, 2, 2);
        List<Component> tooltip = new ArrayList<>();
        EncyclopediaBonusTooltipHelper.append(
                tooltip, new EncyclopediaTooltipBonus(true, true, bonuses));

        assertEquals(5, tooltip.size(), "heading + 4 non-zero lines");
        assertAttributeLine(tooltip.get(1), "attribute.name.goetyarkham.strength");
        assertAttributeLine(tooltip.get(2), "attribute.name.goetyarkham.agility");
        assertAttributeLine(tooltip.get(3), "attribute.name.goetyarkham.willpower");
        assertAttributeLine(tooltip.get(4), "attribute.name.goetyarkham.intellect");
    }

    private static Map<StatType, Integer> totals(
            int strength, int agility, int willpower, int intellect) {
        Map<StatType, Integer> totals = new EnumMap<>(StatType.class);
        totals.put(StatType.STRENGTH, strength);
        totals.put(StatType.AGILITY, agility);
        totals.put(StatType.WILLPOWER, willpower);
        totals.put(StatType.INTELLECT, intellect);
        return totals;
    }

    private static void assertAttributeLine(Component line, String attributeKey) {
        assertTrue(
                TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(line.getStyle().getColor()),
                "attribute bonus line is green: " + attributeKey);
        assertTrue(
                translatableKeyUsedAnywhere(line, attributeKey),
                "attribute bonus line references " + attributeKey);
    }

    private static void assertHeadingAlone(Component line, String headingKey) {
        assertTrue(translatableKey(line).map(headingKey::equals).orElse(false),
                "line is the heading alone: " + headingKey);
        assertTrue(
                TextColor.fromLegacyFormat(ChatFormatting.YELLOW).equals(line.getStyle().getColor()),
                "heading is yellow");
        assertTrue(line.getSiblings().isEmpty(), "heading-alone line has no inline value");
    }

    private static void assertHeadingWithInlineKey(
            Component line, String headingKey, String inlineKey) {
        assertTrue(translatableKey(line).map(headingKey::equals).orElse(false),
                "line begins with heading: " + headingKey);
        assertTrue(
                TextColor.fromLegacyFormat(ChatFormatting.YELLOW).equals(line.getStyle().getColor()),
                "heading segment is yellow");
        assertTrue(translatableKeyUsedAnywhere(line, inlineKey),
                "line's inline value is " + inlineKey);
    }

    private static java.util.Optional<String> translatableKey(Component component) {
        if (component.getContents() instanceof TranslatableContents contents) {
            return java.util.Optional.of(contents.getKey());
        }
        return java.util.Optional.empty();
    }

    private static boolean translatableKeyUsedAnywhere(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents) {
            if (key.equals(contents.getKey())) {
                return true;
            }
            for (Object arg : contents.getArgs()) {
                if (arg instanceof Component argComponent
                        && translatableKeyUsedAnywhere(argComponent, key)) {
                    return true;
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (translatableKeyUsedAnywhere(sibling, key)) {
                return true;
            }
        }
        return false;
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
