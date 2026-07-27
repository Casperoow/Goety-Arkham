package com.casper.goetyarkham.illager_treachery;

public record TreacherySettings(
        boolean enabled,
        int minimumSoul,
        double curveMaximumSoul,
        double minimumProbability,
        double maximumProbability,
        int guaranteedValidDays,
        long cooldownTicks,
        int maximumExtraDraws) {

    public static final TreacherySettings DEFAULTS = sanitize(
            true, 1_000, 100_000, 0.075D, 0.35D, 6, 24_000L, 999);

    public static TreacherySettings sanitize(
            boolean enabled,
            int minimumSoul,
            long curveMaximumSoul,
            double minimumProbability,
            double maximumProbability,
            int guaranteedValidDays,
            long cooldownTicks,
            int maximumExtraDraws) {
        int safeMinimumSoul = Math.max(0, minimumSoul);
        double safeCurveMaximum = curveMaximumSoul;
        if (!Double.isFinite(safeCurveMaximum)
                || safeCurveMaximum <= safeMinimumSoul) {
            safeCurveMaximum = (double) safeMinimumSoul + 1.0D;
        }

        double safeMinimumProbability = clampProbability(minimumProbability);
        double safeMaximumProbability = clampProbability(maximumProbability);
        if (safeMinimumProbability > safeMaximumProbability) {
            double swap = safeMinimumProbability;
            safeMinimumProbability = safeMaximumProbability;
            safeMaximumProbability = swap;
        }

        return new TreacherySettings(
                enabled,
                safeMinimumSoul,
                safeCurveMaximum,
                safeMinimumProbability,
                safeMaximumProbability,
                Math.max(1, guaranteedValidDays),
                Math.max(0L, cooldownTicks),
                Math.max(0, maximumExtraDraws)
        );
    }

    private static double clampProbability(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
