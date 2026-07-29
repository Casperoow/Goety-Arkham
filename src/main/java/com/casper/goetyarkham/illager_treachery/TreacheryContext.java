package com.casper.goetyarkham.illager_treachery;

import com.casper.goetyarkham.chaosbag.ChaosBagSnapshot;

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
        boolean guaranteed,
        ChaosBagSnapshot chaosBagSnapshot) {

    public TreacheryContext {
        java.util.Objects.requireNonNull(chaosBagSnapshot, "chaosBagSnapshot");
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
