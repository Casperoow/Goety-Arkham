package com.casper.goetyarkham.illager_treachery.encounter.formal;

import com.casper.goetyarkham.illager_treachery.IllagerTreacheryApi;

public final class BuiltInFormalEncounters {
    private static boolean registered;

    private BuiltInFormalEncounters() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        IllagerTreacheryApi.registerEncounter(new DreamsOfRlyehEncounter());
        IllagerTreacheryApi.registerEncounter(new TheYellowSignEncounter());
        IllagerTreacheryApi.registerEncounter(new YoungDeepOneEncounter());
        registered = true;
    }
}
