package com.casper.goetyarkham.illager_treachery;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public record TriggerRequest(
        TriggerSource source,
        Set<UUID> players,
        boolean guaranteed) {

    public TriggerRequest {
        players = Set.copyOf(players);
    }

    public static TriggerRequest of(
            TriggerSource source,
            Collection<UUID> players) {
        return new TriggerRequest(source, Set.copyOf(players), false);
    }

    public static TriggerRequest daily(boolean guaranteed) {
        return new TriggerRequest(TriggerSource.DAILY, Set.of(), guaranteed);
    }
}
