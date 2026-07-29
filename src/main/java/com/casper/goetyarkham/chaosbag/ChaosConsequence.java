package com.casper.goetyarkham.chaosbag;

public record ChaosConsequence(
        Kind kind,
        int amount,
        ChaosToken causedBy) {

    public enum Kind {
        REMOVE_SOUL,
        DAMAGE,
        SPAWN_GHOUL
    }
}
