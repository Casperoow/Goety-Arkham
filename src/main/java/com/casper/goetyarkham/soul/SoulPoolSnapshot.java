package com.casper.goetyarkham.soul;

public record SoulPoolSnapshot(
        int currentSoul,
        int maximumSoul,
        boolean hasContainer,
        boolean arcaMode) {

    public static final SoulPoolSnapshot EMPTY =
            new SoulPoolSnapshot(0, 0, false, false);
}
