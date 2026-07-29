package com.casper.goetyarkham.chaosbag;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, server-independent chaos check engine.
 */
public final class ChaosCheckEngine {
    private ChaosCheckEngine() {
    }

    public static ChaosCheckResult resolve(ChaosCheckRequest request) {
        List<TokenInstance> temporaryBag = new ArrayList<>();
        List<ChaosToken> configured = request.bagSnapshot().tokens();
        for (int index = 0; index < configured.size(); index++) {
            temporaryBag.add(new TokenInstance(index, configured.get(index)));
        }

        List<ChaosCheckResult.Draw> draws = new ArrayList<>();
        int forcedIndex = 0;
        int queuedDraws = request.initialDrawCount();
        boolean exhausted = false;
        long tokenModifier = 0L;

        while (queuedDraws > 0
                || forcedIndex < request.forcedTokens().size()) {
            if (queuedDraws > 0) {
                queuedDraws--;
            }
            if (temporaryBag.isEmpty()) {
                exhausted = true;
                break;
            }

            int selectedIndex;
            if (forcedIndex < request.forcedTokens().size()) {
                ChaosToken forced = request.forcedTokens().get(forcedIndex++);
                selectedIndex = find(temporaryBag, forced);
                if (selectedIndex < 0) {
                    throw new IllegalArgumentException(
                            "Forced token " + forced.serializedName()
                                    + " is not available in the temporary bag");
                }
            } else {
                selectedIndex = request.random().nextInt(temporaryBag.size());
                if (selectedIndex < 0 || selectedIndex >= temporaryBag.size()) {
                    throw new IllegalArgumentException(
                            "Random source returned out-of-range index "
                                    + selectedIndex + " for bound "
                                    + temporaryBag.size());
                }
            }

            TokenInstance selected = temporaryBag.remove(selectedIndex);
            int modifier = modifier(
                    selected.token(),
                    request.bagSnapshot().level(),
                    request.environmentSnapshot());
            draws.add(new ChaosCheckResult.Draw(
                    selected.instanceId(), selected.token(), modifier));
            tokenModifier += modifier;

            if (selected.token().kind() == ChaosToken.Kind.CULTIST
                    && request.bagSnapshot().level().isHardOrExpert()) {
                queuedDraws++;
            }
        }

        int safeTokenModifier = saturatingInt(tokenModifier);
        int otherModifier = saturatingInt(request.otherModifiers().stream()
                .mapToLong(ChaosCheckModifier::amount)
                .sum());
        int totalModifier = saturatingInt(
                (long) safeTokenModifier + otherModifier);
        int formulaFinal = saturatingInt(
                (long) request.currentBaseValue() + totalModifier);

        boolean hasAutoFail = draws.stream().anyMatch(draw ->
                draw.token().kind() == ChaosToken.Kind.AUTO_FAIL);
        boolean hasElderSign = draws.stream().anyMatch(draw ->
                draw.token().kind() == ChaosToken.Kind.ELDER_SIGN);
        List<ChaosOverride> overrides = new ArrayList<>();
        int finalValue = formulaFinal;
        boolean success;
        if (hasAutoFail) {
            overrides.add(ChaosOverride.AUTO_FAIL);
            if (hasElderSign) {
                overrides.add(ChaosOverride.AUTO_FAIL_OVER_ELDER_SIGN);
            }
            success = false;
        } else if (hasElderSign) {
            overrides.add(ChaosOverride.ELDER_SIGN_TARGET);
            finalValue = request.targetValue();
            success = request.successCondition().succeeds(
                    finalValue, request.targetValue());
        } else {
            success = request.successCondition().succeeds(
                    finalValue, request.targetValue());
        }

        List<ChaosConsequence> consequences = consequences(
                draws,
                request.bagSnapshot().level(),
                request.environmentSnapshot(),
                success);
        return new ChaosCheckResult(
                request.baseValueSource(),
                request.currentBaseValue(),
                request.targetValue(),
                draws,
                safeTokenModifier,
                request.otherModifiers(),
                otherModifier,
                totalModifier,
                formulaFinal,
                overrides,
                finalValue,
                success,
                consequences,
                exhausted);
    }

    private static int modifier(
            ChaosToken token,
            ChaosBagLevel level,
            ChaosEnvironmentSnapshot environment) {
        return switch (token.kind()) {
            case NUMBER -> token.value();
            case GHOUL -> level.isHardOrExpert()
                    ? -2 : -environment.nearbyGhoulCount();
            case CULTIST -> level.isHardOrExpert() ? 0 : -1;
            case TABLET -> level.isHardOrExpert() ? -4 : -2;
            case AUTO_FAIL, ELDER_SIGN -> 0;
        };
    }

    private static List<ChaosConsequence> consequences(
            List<ChaosCheckResult.Draw> draws,
            ChaosBagLevel level,
            ChaosEnvironmentSnapshot environment,
            boolean success) {
        List<ChaosConsequence> result = new ArrayList<>();
        for (ChaosCheckResult.Draw draw : draws) {
            switch (draw.token().kind()) {
                case GHOUL -> {
                    if (level.isHardOrExpert() && !success) {
                        result.add(new ChaosConsequence(
                                ChaosConsequence.Kind.SPAWN_GHOUL,
                                1,
                                draw.token()));
                    }
                }
                case CULTIST -> {
                    if (!success) {
                        result.add(new ChaosConsequence(
                                ChaosConsequence.Kind.REMOVE_SOUL,
                                level.isHardOrExpert() ? 200 : 100,
                                draw.token()));
                    }
                }
                case TABLET -> {
                    if (environment.nearbyGhoulCount() > 0) {
                        result.add(new ChaosConsequence(
                                ChaosConsequence.Kind.DAMAGE,
                                level.isHardOrExpert() ? 2 : 1,
                                draw.token()));
                        if (level.isHardOrExpert()) {
                            result.add(new ChaosConsequence(
                                    ChaosConsequence.Kind.REMOVE_SOUL,
                                    100,
                                    draw.token()));
                        }
                    }
                }
                case NUMBER, AUTO_FAIL, ELDER_SIGN -> {
                }
            }
        }
        return List.copyOf(result);
    }

    private static int find(
            List<TokenInstance> temporaryBag, ChaosToken token) {
        for (int index = 0; index < temporaryBag.size(); index++) {
            if (temporaryBag.get(index).token().equals(token)) {
                return index;
            }
        }
        return -1;
    }

    private static int saturatingInt(long value) {
        return (int) Math.max(
                Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, value));
    }

    private record TokenInstance(long instanceId, ChaosToken token) {
    }
}
