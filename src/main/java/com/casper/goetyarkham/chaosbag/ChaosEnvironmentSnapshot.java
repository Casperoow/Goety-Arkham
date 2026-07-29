package com.casper.goetyarkham.chaosbag;

public record ChaosEnvironmentSnapshot(int nearbyGhoulCount) {
    public static final ChaosEnvironmentSnapshot EMPTY =
            new ChaosEnvironmentSnapshot(0);

    public ChaosEnvironmentSnapshot {
        if (nearbyGhoulCount < 0) {
            throw new IllegalArgumentException("nearbyGhoulCount cannot be negative");
        }
    }
}
