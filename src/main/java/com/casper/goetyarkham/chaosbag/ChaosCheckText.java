package com.casper.goetyarkham.chaosbag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class ChaosCheckText {
    private ChaosCheckText() {
    }

    public static Component summary(ChaosCheckResult result) {
        Component draws = joinDraws(result.draws());
        Component outcome = Component.translatable(
                result.success()
                        ? "chaos_check.result.goetyarkham.success"
                        : "chaos_check.result.goetyarkham.failure");
        return Component.translatable(
                "message.goetyarkham.chaos_check",
                Component.translatable(result.baseValueSource().translationKey()),
                result.targetValue(),
                result.currentBaseValue(),
                draws,
                signed(result.otherModifier()),
                signed(result.totalModifier()),
                result.finalValue(),
                outcome);
    }

    public static List<Component> notices(ChaosCheckResult result) {
        java.util.ArrayList<Component> notices = new java.util.ArrayList<>();
        if (result.overrides().contains(ChaosOverride.AUTO_FAIL_OVER_ELDER_SIGN)) {
            notices.add(Component.translatable(
                    "chaos_check.override.goetyarkham.auto_fail_over_elder_sign"));
        } else if (result.overrides().contains(ChaosOverride.AUTO_FAIL)) {
            notices.add(Component.translatable(
                    "chaos_check.override.goetyarkham.auto_fail"));
        } else if (result.overrides().contains(ChaosOverride.ELDER_SIGN_TARGET)) {
            notices.add(Component.translatable(
                    "chaos_check.override.goetyarkham.elder_sign"));
        }
        if (result.temporaryBagExhausted()) {
            notices.add(Component.translatable(
                    "chaos_check.notice.goetyarkham.bag_exhausted"));
        }
        return List.copyOf(notices);
    }

    public static Component token(ChaosToken token) {
        if (token.kind() == ChaosToken.Kind.NUMBER) {
            return Component.translatable(
                    token.translationKey(), signed(token.value()));
        }
        return Component.translatable(token.translationKey());
    }

    public static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static Component joinDraws(List<ChaosCheckResult.Draw> draws) {
        if (draws.isEmpty()) {
            return Component.translatable("chaos_check.draw.goetyarkham.none");
        }
        MutableComponent result = Component.empty();
        for (int index = 0; index < draws.size(); index++) {
            if (index > 0) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            ChaosCheckResult.Draw draw = draws.get(index);
            if (draw.token().kind() == ChaosToken.Kind.NUMBER) {
                result.append(token(draw.token()));
            } else {
                result.append(Component.translatable(
                        "chaos_check.draw.goetyarkham.named",
                        token(draw.token()),
                        signed(draw.modifier())));
            }
        }
        return result;
    }
}
