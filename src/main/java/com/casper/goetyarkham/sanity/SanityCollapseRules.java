package com.casper.goetyarkham.sanity;

public final class SanityCollapseRules {
    private SanityCollapseRules() {
    }

    public static boolean isSoulDepleted(boolean hasContainer, int currentSoul) {
        return !hasContainer || currentSoul <= 0;
    }
}
