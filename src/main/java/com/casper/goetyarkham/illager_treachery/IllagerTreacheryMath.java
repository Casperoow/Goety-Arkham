package com.casper.goetyarkham.illager_treachery;

public final class IllagerTreacheryMath {
    private IllagerTreacheryMath() {
    }

    public static boolean isSoulEligible(int maximumSoul, TreacherySettings settings) {
        return maximumSoul >= settings.minimumSoul();
    }

    public static double baseProbability(int maximumSoul, TreacherySettings settings) {
        if (!isSoulEligible(maximumSoul, settings)) {
            return 0.0D;
        }

        double minimumSoul = settings.minimumSoul();
        double curveMaximum = settings.curveMaximumSoul();
        double ratio;
        if (minimumSoul <= 0.0D) {
            ratio = maximumSoul <= 0
                    ? 0.0D
                    : 1.0D;
        } else {
            double denominator = Math.log(curveMaximum / minimumSoul);
            double numerator = Math.log(Math.max(minimumSoul, maximumSoul) / minimumSoul);
            ratio = denominator > 0.0D && Double.isFinite(denominator)
                    ? numerator / denominator
                    : 0.0D;
        }
        if (!Double.isFinite(ratio)) {
            ratio = 0.0D;
        }
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));

        double probability = settings.minimumProbability()
                + (settings.maximumProbability() - settings.minimumProbability()) * ratio;
        if (!Double.isFinite(probability)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, probability));
    }

    public static double normalizedProbability(double baseProbability, int candidateCount) {
        if (candidateCount <= 0) {
            return 0.0D;
        }
        double safeBase = Double.isFinite(baseProbability)
                ? Math.max(0.0D, Math.min(1.0D, baseProbability))
                : 0.0D;
        double normalized = -Math.expm1(Math.log1p(-safeBase) / candidateCount);
        if (!Double.isFinite(normalized)) {
            return safeBase >= 1.0D ? 1.0D : 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, normalized));
    }

    public static double combinedProbability(double[] independentProbabilities) {
        double miss = 1.0D;
        for (double probability : independentProbabilities) {
            double safe = Double.isFinite(probability)
                    ? Math.max(0.0D, Math.min(1.0D, probability))
                    : 0.0D;
            miss *= 1.0D - safe;
        }
        return 1.0D - miss;
    }

    public static double expectedValidDaysWithGuarantee(
            double dailyProbability, int guaranteedDay) {
        double probability = Double.isFinite(dailyProbability)
                ? Math.max(0.0D, Math.min(1.0D, dailyProbability))
                : 0.0D;
        int safeGuaranteedDay = Math.max(1, guaranteedDay);
        if (probability == 0.0D) {
            return safeGuaranteedDay;
        }
        return (1.0D - Math.pow(1.0D - probability, safeGuaranteedDay))
                / probability;
    }
}
