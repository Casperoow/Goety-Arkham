package com.casper.goetyarkham.illager_treachery.encounter;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class EncounterRegistry {
    public static final EncounterRegistry INSTANCE = new EncounterRegistry();

    private final Map<ResourceLocation, IllagerTreacheryEncounter> encounters =
            new LinkedHashMap<>();

    public synchronized void register(IllagerTreacheryEncounter encounter) {
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(encounter.id(), "encounter id");
        if (encounter.defaultWeight() < 0L) {
            throw new IllegalArgumentException(
                    "Encounter " + encounter.id() + " has a negative default weight");
        }
        IllagerTreacheryEncounter previous =
                encounters.putIfAbsent(encounter.id(), encounter);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate illager_treachery encounter id: " + encounter.id());
        }
    }

    public synchronized Optional<IllagerTreacheryEncounter> get(ResourceLocation id) {
        return Optional.ofNullable(encounters.get(id));
    }

    public synchronized Collection<IllagerTreacheryEncounter> values() {
        return Collections.unmodifiableList(new ArrayList<>(encounters.values()));
    }

    public synchronized int size() {
        return encounters.size();
    }

    public synchronized EncounterSettings defaults(ResourceLocation id) {
        IllagerTreacheryEncounter encounter = encounters.get(id);
        if (encounter == null) {
            throw new IllegalArgumentException("Unknown encounter id: " + id);
        }
        return new EncounterSettings(
                encounter.defaultEnabled(), encounter.defaultWeight());
    }

    public synchronized EncounterSnapshot snapshot(
            Function<IllagerTreacheryEncounter, EncounterSettings> settings) {
        List<EncounterSnapshot.Entry> entries = new ArrayList<>();
        for (IllagerTreacheryEncounter encounter : encounters.values()) {
            EncounterSettings effective = Objects.requireNonNull(settings.apply(encounter));
            if (effective.drawable()) {
                entries.add(new EncounterSnapshot.Entry(
                        encounter.id(), effective.weight(), encounter));
            }
        }
        return new EncounterSnapshot(entries);
    }
}
