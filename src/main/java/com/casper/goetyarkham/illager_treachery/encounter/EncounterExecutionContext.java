package com.casper.goetyarkham.illager_treachery.encounter;

import com.casper.goetyarkham.illager_treachery.TreacheryContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class EncounterExecutionContext {
    private final TreacheryContext treachery;
    private final ServerPlayer player;
    private final BooleanSupplier extraDrawRequest;

    public EncounterExecutionContext(
            TreacheryContext treachery,
            ServerPlayer player,
            BooleanSupplier extraDrawRequest) {
        this.treachery = Objects.requireNonNull(treachery);
        this.player = Objects.requireNonNull(player);
        this.extraDrawRequest = Objects.requireNonNull(extraDrawRequest);
    }

    public TreacheryContext treachery() {
        return treachery;
    }

    public ServerPlayer player() {
        return player;
    }

    public boolean requestExtraDraw() {
        return extraDrawRequest.getAsBoolean();
    }
}
