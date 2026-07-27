package com.casper.goetyarkham.illager_treachery.event;

import com.casper.goetyarkham.illager_treachery.PlayerTreacheryResolution;
import com.casper.goetyarkham.illager_treachery.TreacheryContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Forge-bus extension points published in global preparation, per-player
 * preparation/immunity/resolution, and global completion order.
 */
public final class IllagerTreacheryEvents {
    private IllagerTreacheryEvents() {
    }

    public static final class GlobalPreparing extends Event {
        private final TreacheryContext context;
        private final EncounterSnapshot encounters;

        public GlobalPreparing(
                TreacheryContext context, EncounterSnapshot encounters) {
            this.context = Objects.requireNonNull(context);
            this.encounters = Objects.requireNonNull(encounters);
        }

        public TreacheryContext context() {
            return context;
        }

        public EncounterSnapshot encounters() {
            return encounters;
        }
    }

    public static final class PlayerPreparing extends Event {
        private final TreacheryContext context;
        private final ServerPlayer player;

        public PlayerPreparing(TreacheryContext context, ServerPlayer player) {
            this.context = Objects.requireNonNull(context);
            this.player = Objects.requireNonNull(player);
        }

        public TreacheryContext context() {
            return context;
        }

        public ServerPlayer player() {
            return player;
        }
    }

    public static final class PlayerImmunity extends Event {
        private final TreacheryContext context;
        private final ServerPlayer player;
        private boolean immune;

        public PlayerImmunity(TreacheryContext context, ServerPlayer player) {
            this.context = Objects.requireNonNull(context);
            this.player = Objects.requireNonNull(player);
        }

        public TreacheryContext context() {
            return context;
        }

        public ServerPlayer player() {
            return player;
        }

        public boolean isImmune() {
            return immune;
        }

        public void setImmune(boolean immune) {
            this.immune = immune;
        }
    }

    public static final class PlayerResolved extends Event {
        private final TreacheryContext context;
        private final PlayerTreacheryResolution resolution;

        public PlayerResolved(
                TreacheryContext context,
                PlayerTreacheryResolution resolution) {
            this.context = Objects.requireNonNull(context);
            this.resolution = Objects.requireNonNull(resolution);
        }

        public TreacheryContext context() {
            return context;
        }

        public PlayerTreacheryResolution resolution() {
            return resolution;
        }
    }

    public static final class GlobalResolved extends Event {
        private final TreacheryContext context;
        private final List<UUID> participants;
        private final List<PlayerTreacheryResolution> resolutions;

        public GlobalResolved(
                TreacheryContext context,
                List<UUID> participants,
                List<PlayerTreacheryResolution> resolutions) {
            this.context = Objects.requireNonNull(context);
            this.participants = List.copyOf(participants);
            this.resolutions = List.copyOf(resolutions);
        }

        public TreacheryContext context() {
            return context;
        }

        public List<UUID> participants() {
            return participants;
        }

        public List<PlayerTreacheryResolution> resolutions() {
            return resolutions;
        }
    }
}
