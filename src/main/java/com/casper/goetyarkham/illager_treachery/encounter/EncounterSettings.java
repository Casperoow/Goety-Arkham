package com.casper.goetyarkham.illager_treachery.encounter;

public record EncounterSettings(boolean enabled, long weight) {
    public EncounterSettings {
        weight = Math.max(0L, weight);
    }

    public boolean drawable() {
        return enabled && weight > 0L;
    }
}
