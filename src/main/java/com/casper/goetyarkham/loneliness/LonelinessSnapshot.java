package com.casper.goetyarkham.loneliness;

public record LonelinessSnapshot(int loneliness) {
    public static final LonelinessSnapshot UNAVAILABLE = new LonelinessSnapshot(0);
}
