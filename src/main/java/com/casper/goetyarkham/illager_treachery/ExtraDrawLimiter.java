package com.casper.goetyarkham.illager_treachery;

public final class ExtraDrawLimiter {
    private final int maximumExtraDraws;
    private int acceptedExtraDraws;

    public ExtraDrawLimiter(int maximumExtraDraws) {
        this.maximumExtraDraws = Math.max(0, maximumExtraDraws);
    }

    public boolean request() {
        if (acceptedExtraDraws >= maximumExtraDraws) {
            return false;
        }
        acceptedExtraDraws++;
        return true;
    }

    public int acceptedExtraDraws() {
        return acceptedExtraDraws;
    }
}
