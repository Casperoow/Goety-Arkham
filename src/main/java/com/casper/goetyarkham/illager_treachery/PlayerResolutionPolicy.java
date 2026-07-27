package com.casper.goetyarkham.illager_treachery;

public final class PlayerResolutionPolicy {
    private PlayerResolutionPolicy() {
    }

    public static PlayerTreacheryResult choose(
            boolean stillParticipant, boolean immune) {
        if (!stillParticipant) {
            return PlayerTreacheryResult.EXCLUDED;
        }
        return immune
                ? PlayerTreacheryResult.IMMUNE
                : PlayerTreacheryResult.DRAWN;
    }
}
