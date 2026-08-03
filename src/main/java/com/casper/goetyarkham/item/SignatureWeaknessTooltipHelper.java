package com.casper.goetyarkham.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shared "Signature Weakness:" tooltip block for items that own a bound
 * weakness. Reveals only the weakness's own display name while Shift is held;
 * the weakness item's own tooltip is responsible for its full effect text.
 */
public final class SignatureWeaknessTooltipHelper {
    public static final String HEADING_TRANSLATION_KEY =
            "tooltip.goetyarkham.signature_weakness";
    public static final String HOLD_SHIFT_TRANSLATION_KEY =
            "tooltip.goetyarkham.hold_shift";

    private SignatureWeaknessTooltipHelper() {
    }

    public static void append(
            List<Component> tooltip, String weaknessNameTranslationKey) {
        append(tooltip, weaknessNameTranslationKey, ShiftTooltipHelper.isShiftDown());
    }

    static void append(
            List<Component> tooltip,
            String weaknessNameTranslationKey,
            boolean shiftDown) {
        tooltip.add(Component.translatable(HEADING_TRANSLATION_KEY)
                .withStyle(ChatFormatting.YELLOW));
        if (shiftDown) {
            tooltip.add(Component.translatable(weaknessNameTranslationKey)
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else {
            tooltip.add(Component.translatable(HOLD_SHIFT_TRANSLATION_KEY)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
