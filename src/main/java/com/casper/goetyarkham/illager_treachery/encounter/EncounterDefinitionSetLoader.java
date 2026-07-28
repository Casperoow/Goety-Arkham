package com.casper.goetyarkham.illager_treachery.encounter;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pure, deterministic batch validation used by the server reload listener and
 * tests. Rejected files never appear in the immutable accepted collection.
 */
public final class EncounterDefinitionSetLoader {
    private EncounterDefinitionSetLoader() {
    }

    public static Result load(
            Map<ResourceLocation, JsonElement> resources,
            EncounterTypeRegistry types,
            EncounterParseContext context,
            Predicate<ResourceLocation> conflictsWithJava) {
        Map<ResourceLocation, IllagerTreacheryEncounter> accepted =
                new LinkedHashMap<>();
        java.util.ArrayList<Rejection> rejected = new java.util.ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(resource -> {
                    ResourceLocation id = resource.getKey();
                    JsonElement json = resource.getValue();
                    if (conflictsWithJava.test(id)) {
                        rejected.add(new Rejection(
                                id,
                                "id is already registered by the Java API"));
                        return;
                    }
                    try {
                        accepted.put(
                                id,
                                EncounterDefinitionParser.parse(
                                        id, json, types, context));
                    } catch (EncounterDefinitionException exception) {
                        rejected.add(new Rejection(
                                id, exception.getMessage()));
                    }
                });
        return new Result(
                Collections.unmodifiableMap(new LinkedHashMap<>(accepted)),
                List.copyOf(rejected));
    }

    public record Result(
            Map<ResourceLocation, IllagerTreacheryEncounter> accepted,
            List<Rejection> rejected) {
    }

    public record Rejection(ResourceLocation id, String reason) {
    }
}
