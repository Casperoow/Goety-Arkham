package com.casper.goetyarkham.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;

public final class SignatureWeaknessTooltipHelperSelfTest {
    private static final String WEAKNESS_KEY = "item.goetyarkham.dark_memory";

    private SignatureWeaknessTooltipHelperSelfTest() {
    }

    public static void main(String[] args) {
        collapsedHidesWeaknessName();
        expandedRevealsWeaknessNameInBoldDarkRed();
        System.out.println("SignatureWeaknessTooltipHelperSelfTest: all checks passed");
    }

    private static void collapsedHidesWeaknessName() {
        List<Component> tooltip = new ArrayList<>();
        SignatureWeaknessTooltipHelper.append(tooltip, WEAKNESS_KEY, false);
        assertEquals(2, tooltip.size(), "collapsed tooltip line count");
        assertHeading(tooltip.get(0));
        assertTrue(tooltip.get(1).getContents() instanceof TranslatableContents contents
                        && SignatureWeaknessTooltipHelper.HOLD_SHIFT_TRANSLATION_KEY
                        .equals(contents.getKey()),
                "collapsed state shows the shared hold-shift hint");
        assertTrue(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "hold-shift hint is dark gray");
        assertTrue(!containsWeaknessName(tooltip),
                "collapsed state never reveals the weakness name");
    }

    private static void expandedRevealsWeaknessNameInBoldDarkRed() {
        List<Component> tooltip = new ArrayList<>();
        SignatureWeaknessTooltipHelper.append(tooltip, WEAKNESS_KEY, true);
        assertEquals(2, tooltip.size(), "expanded tooltip line count");
        assertHeading(tooltip.get(0));
        assertTrue(tooltip.get(1).getContents() instanceof TranslatableContents contents
                        && WEAKNESS_KEY.equals(contents.getKey()),
                "expanded state reveals the weakness's own name key");
        assertTrue(TextColor.fromLegacyFormat(ChatFormatting.DARK_RED)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "revealed weakness name is dark red");
        assertTrue(tooltip.get(1).getStyle().isBold(),
                "revealed weakness name is bold");
    }

    private static boolean containsWeaknessName(List<Component> tooltip) {
        return tooltip.stream().anyMatch(component ->
                component.getContents() instanceof TranslatableContents contents
                        && WEAKNESS_KEY.equals(contents.getKey()));
    }

    private static void assertHeading(Component heading) {
        assertTrue(heading.getContents() instanceof TranslatableContents contents
                        && SignatureWeaknessTooltipHelper.HEADING_TRANSLATION_KEY
                        .equals(contents.getKey()),
                "heading uses the shared signature-weakness key");
        assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(heading.getStyle().getColor()),
                "heading is yellow");
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
