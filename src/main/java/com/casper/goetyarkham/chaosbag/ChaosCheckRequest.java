package com.casper.goetyarkham.chaosbag;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ChaosCheckRequest(
        UUID playerId,
        ResourceLocation source,
        ChaosBaseValueSource baseValueSource,
        int currentBaseValue,
        int targetValue,
        ChaosSuccessCondition successCondition,
        List<ChaosCheckModifier> otherModifiers,
        ChaosBagSnapshot bagSnapshot,
        ChaosEnvironmentSnapshot environmentSnapshot,
        ChaosRandom random,
        List<ChaosToken> forcedTokens,
        int initialDrawCount) {

    public ChaosCheckRequest {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(baseValueSource, "baseValueSource");
        Objects.requireNonNull(successCondition, "successCondition");
        otherModifiers = List.copyOf(otherModifiers);
        Objects.requireNonNull(bagSnapshot, "bagSnapshot");
        Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
        Objects.requireNonNull(random, "random");
        forcedTokens = List.copyOf(forcedTokens);
        if (initialDrawCount <= 0) {
            throw new IllegalArgumentException("initialDrawCount must be positive");
        }
    }

    public static Builder builder(
            UUID playerId,
            ResourceLocation source,
            ChaosBaseValueSource baseValueSource,
            int currentBaseValue,
            int targetValue,
            ChaosBagSnapshot bagSnapshot,
            ChaosRandom random) {
        return new Builder(
                playerId,
                source,
                baseValueSource,
                currentBaseValue,
                targetValue,
                bagSnapshot,
                random);
    }

    public static final class Builder {
        private final UUID playerId;
        private final ResourceLocation source;
        private final ChaosBaseValueSource baseValueSource;
        private final int currentBaseValue;
        private final int targetValue;
        private final ChaosBagSnapshot bagSnapshot;
        private final ChaosRandom random;
        private ChaosSuccessCondition successCondition =
                ChaosSuccessCondition.AT_LEAST;
        private List<ChaosCheckModifier> modifiers = List.of();
        private ChaosEnvironmentSnapshot environment =
                ChaosEnvironmentSnapshot.EMPTY;
        private List<ChaosToken> forcedTokens = List.of();
        private int initialDrawCount = 1;

        private Builder(
                UUID playerId,
                ResourceLocation source,
                ChaosBaseValueSource baseValueSource,
                int currentBaseValue,
                int targetValue,
                ChaosBagSnapshot bagSnapshot,
                ChaosRandom random) {
            this.playerId = playerId;
            this.source = source;
            this.baseValueSource = baseValueSource;
            this.currentBaseValue = currentBaseValue;
            this.targetValue = targetValue;
            this.bagSnapshot = bagSnapshot;
            this.random = random;
        }

        public Builder successCondition(ChaosSuccessCondition successCondition) {
            this.successCondition = successCondition;
            return this;
        }

        public Builder modifiers(List<ChaosCheckModifier> modifiers) {
            this.modifiers = modifiers;
            return this;
        }

        public Builder environment(ChaosEnvironmentSnapshot environment) {
            this.environment = environment;
            return this;
        }

        public Builder forcedTokens(List<ChaosToken> forcedTokens) {
            this.forcedTokens = forcedTokens;
            return this;
        }

        public Builder initialDrawCount(int initialDrawCount) {
            this.initialDrawCount = initialDrawCount;
            return this;
        }

        public ChaosCheckRequest build() {
            return new ChaosCheckRequest(
                    playerId,
                    source,
                    baseValueSource,
                    currentBaseValue,
                    targetValue,
                    successCondition,
                    modifiers,
                    bagSnapshot,
                    environment,
                    random,
                    forcedTokens,
                    initialDrawCount);
        }
    }
}
