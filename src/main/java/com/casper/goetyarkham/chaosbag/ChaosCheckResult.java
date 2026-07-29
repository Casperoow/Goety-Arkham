package com.casper.goetyarkham.chaosbag;

import java.util.List;

public record ChaosCheckResult(
        ChaosBaseValueSource baseValueSource,
        int currentBaseValue,
        int targetValue,
        List<Draw> draws,
        int tokenModifier,
        List<ChaosCheckModifier> otherModifiers,
        int otherModifier,
        int totalModifier,
        int formulaFinalValue,
        List<ChaosOverride> overrides,
        int finalValue,
        boolean success,
        List<ChaosConsequence> consequences,
        boolean temporaryBagExhausted) {

    public ChaosCheckResult {
        draws = List.copyOf(draws);
        otherModifiers = List.copyOf(otherModifiers);
        overrides = List.copyOf(overrides);
        consequences = List.copyOf(consequences);
    }

    public record Draw(long instanceId, ChaosToken token, int modifier) {
    }
}
