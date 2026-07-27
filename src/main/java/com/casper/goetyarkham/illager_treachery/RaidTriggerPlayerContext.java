package com.casper.goetyarkham.illager_treachery;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class RaidTriggerPlayerContext {
    private static final ThreadLocal<ServerPlayer> CURRENT = new ThreadLocal<>();

    private RaidTriggerPlayerContext() {
    }

    public static void enter(ServerPlayer player) {
        CURRENT.set(player);
    }

    public static Optional<ServerPlayer> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void exit() {
        CURRENT.remove();
    }
}
