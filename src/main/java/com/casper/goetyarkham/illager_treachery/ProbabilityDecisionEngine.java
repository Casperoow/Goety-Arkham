package com.casper.goetyarkham.illager_treachery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class ProbabilityDecisionEngine {
    private ProbabilityDecisionEngine() {
    }

    public static <T> Outcome<T> rollDaily(
            List<T> candidates,
            ToIntFunction<T> maximumSoul,
            TreacherySettings settings,
            TreacheryRandom random) {
        LinkedHashSet<T> successes = new LinkedHashSet<>();
        int count = candidates.size();
        for (T candidate : candidates) {
            double base = IllagerTreacheryMath.baseProbability(
                    maximumSoul.applyAsInt(candidate), settings);
            double actual =
                    IllagerTreacheryMath.normalizedProbability(base, count);
            if (random.nextDouble() < actual) {
                successes.add(candidate);
            }
        }
        return new Outcome<>(!candidates.isEmpty(), successes);
    }

    public static <T> Outcome<T> rollDirect(
            List<T> candidates,
            ToIntFunction<T> maximumSoul,
            TreacherySettings settings,
            TreacheryRandom random) {
        LinkedHashSet<T> successes = new LinkedHashSet<>();
        for (T candidate : candidates) {
            double probability = IllagerTreacheryMath.baseProbability(
                    maximumSoul.applyAsInt(candidate), settings);
            if (random.nextDouble() < probability) {
                successes.add(candidate);
            }
        }
        return new Outcome<>(!candidates.isEmpty(), successes);
    }

    public record Outcome<T>(boolean randomDrawn, Set<T> successes) {
        public Outcome {
            successes = Set.copyOf(successes);
        }
    }
}
