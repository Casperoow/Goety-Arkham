package com.casper.goetyarkham.illager_treachery.encounter;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EncounterTypeRegistry {
    public static final EncounterTypeRegistry INSTANCE = new EncounterTypeRegistry();

    private final Map<ResourceLocation, EncounterType<?>> types =
            new LinkedHashMap<>();
    private boolean frozen;

    public synchronized void register(
            ResourceLocation id, EncounterType<?> type) {
        Objects.requireNonNull(id, "encounter type id");
        Objects.requireNonNull(type, "encounter type");
        if (frozen) {
            throw new IllegalStateException(
                    "Encounter type registration is frozen: " + id);
        }
        EncounterType<?> previous = types.putIfAbsent(id, type);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate illager_treachery encounter type id: " + id);
        }
    }

    public synchronized Optional<EncounterType<?>> get(ResourceLocation id) {
        return Optional.ofNullable(types.get(id));
    }

    public synchronized Map<ResourceLocation, EncounterType<?>> values() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(types));
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean frozen() {
        return frozen;
    }
}
