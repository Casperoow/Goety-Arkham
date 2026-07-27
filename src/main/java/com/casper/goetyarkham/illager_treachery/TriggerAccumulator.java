package com.casper.goetyarkham.illager_treachery;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TriggerAccumulator {
    private final long tick;
    private final EnumMap<TriggerSource, LinkedHashSet<UUID>> players =
            new EnumMap<>(TriggerSource.class);
    private boolean guaranteed;

    public TriggerAccumulator(long tick) {
        this.tick = tick;
    }

    public void merge(TriggerRequest request) {
        players.computeIfAbsent(request.source(), ignored -> new LinkedHashSet<>())
                .addAll(request.players());
        guaranteed |= request.guaranteed();
    }

    public long tick() {
        return tick;
    }

    public boolean guaranteed() {
        return guaranteed;
    }

    public Set<TriggerSource> sources() {
        if (players.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(players.keySet()));
    }

    public Set<UUID> players(TriggerSource source) {
        return Collections.unmodifiableSet(
                players.getOrDefault(source, new LinkedHashSet<>()));
    }

    public Set<UUID> allPlayers() {
        LinkedHashSet<UUID> all = new LinkedHashSet<>();
        players.values().forEach(all::addAll);
        return Collections.unmodifiableSet(all);
    }

    public Map<TriggerSource, Set<UUID>> playersBySource() {
        EnumMap<TriggerSource, Set<UUID>> copy = new EnumMap<>(TriggerSource.class);
        players.forEach((source, ids) -> copy.put(source, Set.copyOf(ids)));
        return Collections.unmodifiableMap(copy);
    }
}
