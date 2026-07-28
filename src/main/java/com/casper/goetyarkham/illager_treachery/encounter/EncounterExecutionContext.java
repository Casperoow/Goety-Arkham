package com.casper.goetyarkham.illager_treachery.encounter;

import com.casper.goetyarkham.illager_treachery.TreacheryContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class EncounterExecutionContext {
    private final TreacheryContext treachery;
    private final ServerPlayer player;
    private final BooleanSupplier extraDrawRequest;
    private final Predicate<ResourceLocation> oncePerPlayerEventClaim;

    public EncounterExecutionContext(
            TreacheryContext treachery,
            ServerPlayer player,
            BooleanSupplier extraDrawRequest) {
        this(treachery, player, extraDrawRequest, ignored -> true);
    }

    public EncounterExecutionContext(
            TreacheryContext treachery,
            ServerPlayer player,
            BooleanSupplier extraDrawRequest,
            Predicate<ResourceLocation> oncePerPlayerEventClaim) {
        this.treachery = Objects.requireNonNull(treachery);
        this.player = Objects.requireNonNull(player);
        this.extraDrawRequest = Objects.requireNonNull(extraDrawRequest);
        this.oncePerPlayerEventClaim =
                Objects.requireNonNull(oncePerPlayerEventClaim);
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

    /**
     * Claims a key for this player in the current global event. The manager
     * owns the short-lived claim set, so encounters never retain players or
     * event state after resolution.
     */
    public boolean claimOncePerPlayerEvent(ResourceLocation key) {
        return oncePerPlayerEventClaim.test(Objects.requireNonNull(key));
    }
}
