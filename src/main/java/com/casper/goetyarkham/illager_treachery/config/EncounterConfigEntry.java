package com.casper.goetyarkham.illager_treachery.config;

public record EncounterConfigEntry(boolean enabled, long weight) {
    public EncounterConfigEntry {
        if (weight < 0L) {
            throw new IllegalArgumentException("Encounter weight cannot be negative");
        }
    }

    public boolean drawable() {
        return enabled && weight > 0L;
    }
}
