package com.casper.goetyarkham.sanity;

public final class SanityMath {
    private SanityMath() {
    }

    public static int maximumSanity(double finalAttributeValue, int permanentMaxLoss) {
        int attributeMaximum = floorAttribute(finalAttributeValue);
        int loss = clampPermanentLoss(permanentMaxLoss);
        return Math.max(SanityConstants.MINIMUM_MAXIMUM, attributeMaximum - loss);
    }

    public static int floorAttribute(double finalAttributeValue) {
        if (!Double.isFinite(finalAttributeValue)) {
            return SanityConstants.BASE_MAXIMUM;
        }
        long floored = (long) Math.floor(finalAttributeValue);
        return (int) Math.max(
                SanityConstants.MINIMUM_MAXIMUM,
                Math.min(1024L, floored));
    }

    public static int clampPermanentLoss(int value) {
        return Math.max(0, Math.min(SanityConstants.MAX_PERMANENT_LOSS, value));
    }

    public static int clampCurrent(int value, int maximum) {
        return Math.max(0, Math.min(Math.max(SanityConstants.MINIMUM_MAXIMUM, maximum), value));
    }

    public static boolean canFoodRestore(int currentSanity, int maximumSanity) {
        return currentSanity > 0
                && currentSanity < maximumSanity
                && (long) currentSanity * 2L > maximumSanity;
    }

    public static int ticksUntilSoulDrain(boolean collapseActive, int counter) {
        if (!collapseActive) {
            return -1;
        }
        int normalized = Math.max(0, Math.min(
                SanityConstants.COLLAPSE_INTERVAL_TICKS - 1, counter));
        return SanityConstants.COLLAPSE_INTERVAL_TICKS - normalized;
    }
}
