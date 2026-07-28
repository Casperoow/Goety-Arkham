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

    private final Map<ResourceLocation, IllagerTreacheryEncounter> javaEncounters =
            new LinkedHashMap<>();
    private Map<ResourceLocation, IllagerTreacheryEncounter> dataEncounters = Map.of();

    public synchronized void register(IllagerTreacheryEncounter encounter) {
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(encounter.id(), "encounter id");
        if (encounter.defaultWeight() < 0L) {
            throw new IllegalArgumentException(
                    "Encounter " + encounter.id() + " has a negative default weight");
        }
        if (dataEncounters.containsKey(encounter.id())) {
            throw new IllegalStateException(
                    "Java encounter conflicts with data-pack encounter id: "
                            + encounter.id());
        }
        IllagerTreacheryEncounter previous =
                javaEncounters.putIfAbsent(encounter.id(), encounter);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate illager_treachery encounter id: " + encounter.id());
        }
    }

    public synchronized Optional<IllagerTreacheryEncounter> get(ResourceLocation id) {
        IllagerTreacheryEncounter javaEncounter = javaEncounters.get(id);
        return Optional.ofNullable(
                javaEncounter == null ? dataEncounters.get(id) : javaEncounter);
    }

    public synchronized Collection<IllagerTreacheryEncounter> values() {
        return Collections.unmodifiableList(new ArrayList<>(combined().values()));
    }

    public synchronized int size() {
        return javaEncounters.size() + dataEncounters.size();
    }

    public synchronized EncounterSettings defaults(ResourceLocation id) {
        IllagerTreacheryEncounter encounter = get(id).orElse(null);
        if (encounter == null) {
            throw new IllegalArgumentException("Unknown encounter id: " + id);
        }
        return new EncounterSettings(
                encounter.defaultEnabled(), encounter.defaultWeight());
    }

    public synchronized EncounterSnapshot snapshot(
            Function<IllagerTreacheryEncounter, EncounterSettings> settings) {
        List<EncounterSnapshot.Entry> entries = new ArrayList<>();
        for (IllagerTreacheryEncounter encounter : combined().values()) {
            EncounterSettings effective = Objects.requireNonNull(settings.apply(encounter));
            if (effective.drawable()) {
                entries.add(new EncounterSnapshot.Entry(
                        encounter.id(), effective.weight(), encounter));
            }
        }
        return new EncounterSnapshot(entries);
    }

    /**
     * Atomically replaces only data-pack encounters. Java registrations are
     * retained and always conflict explicitly instead of winning by load order.
     */
    public synchronized void replaceDataDriven(
            Map<ResourceLocation, ? extends IllagerTreacheryEncounter> replacements) {
        Objects.requireNonNull(replacements, "replacements");
        for (ResourceLocation id : replacements.keySet()) {
            if (javaEncounters.containsKey(id)) {
                throw new IllegalStateException(
                        "Data-pack encounter conflicts with Java encounter id: " + id);
            }
        }
        dataEncounters = Collections.unmodifiableMap(
                new LinkedHashMap<>(replacements));
    }

    public synchronized boolean isJavaEncounter(ResourceLocation id) {
        return javaEncounters.containsKey(id);
    }

    public synchronized Map<ResourceLocation, IllagerTreacheryEncounter> definitions() {
        return Collections.unmodifiableMap(combined());
    }

    private Map<ResourceLocation, IllagerTreacheryEncounter> combined() {
        Map<ResourceLocation, IllagerTreacheryEncounter> combined =
                new LinkedHashMap<>(javaEncounters);
        combined.putAll(dataEncounters);
        return combined;
    }
}
