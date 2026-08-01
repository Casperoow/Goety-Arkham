package com.casper.goetyarkham.sanity;

public record SanitySnapshot(
        int currentSanity,
        int maximumSanity,
        int permanentMaxLoss) {
    public static final SanitySnapshot UNAVAILABLE = new SanitySnapshot(0, 0, 0);
}
