package com.casper.goetyarkham.illager_treachery;

import java.util.Map;
import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;

public record TreacheryContext(
        UUID eventId,
        long serverTick,
        Set<TriggerSource> sources,
        Map<TriggerSource, Set<UUID>> submittedPlayers,
        Set<UUID> qualifyingTriggerPlayers,
        Set<UUID> probabilitySuccessfulPlayers,
        boolean guaranteed) {

    public TreacheryContext {
        sources = Set.copyOf(sources);
        EnumMap<TriggerSource, Set<UUID>> submittedCopy =
                new EnumMap<>(TriggerSource.class);
        submittedPlayers.forEach(
                (source, players) -> submittedCopy.put(source, Set.copyOf(players)));
        submittedPlayers = Map.copyOf(submittedCopy);
        qualifyingTriggerPlayers = Set.copyOf(qualifyingTriggerPlayers);
        probabilitySuccessfulPlayers = Set.copyOf(probabilitySuccessfulPlayers);
    }
}
