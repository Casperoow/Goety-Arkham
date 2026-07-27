package com.casper.goetyarkham.stats;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.command.StatsCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

public final class StatsEvents {
    private static final ResourceLocation PLAYER_STATS_ID =
            new ResourceLocation(GoetyArkham.MOD_ID, "player_stats");

    private StatsEvents() {
    }

    @Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(IPlayerStats.class);
        }
    }

    @Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof ServerPlayer player) {
                PlayerStatsProvider provider = new PlayerStatsProvider(player);
                event.addCapability(PLAYER_STATS_ID, provider);
                event.addListener(provider::invalidate);
                GoetyArkham.LOGGER.info(
                        "[StatsCapability] AttachCapabilitiesEvent: player={}, uuid={}, entityIdentity={}, attached=true",
                        safePlayerName(player),
                        player.getUUID(),
                        System.identityHashCode(player)
                );
            }
        }

        @SubscribeEvent
        public static void clonePlayer(PlayerEvent.Clone event) {
            boolean presentBeforeRevive =
                    event.getOriginal().getCapability(ModCapabilities.PLAYER_STATS).isPresent();
            boolean presentAfterRevive = false;
            boolean newCapabilityPresent = false;
            boolean copied = false;
            try {
                event.getOriginal().reviveCaps();
                presentAfterRevive =
                        event.getOriginal().getCapability(ModCapabilities.PLAYER_STATS).isPresent();

                Optional<PlayerStats> oldStats = event.getOriginal()
                        .getCapability(ModCapabilities.PLAYER_STATS)
                        .resolve()
                        .filter(PlayerStats.class::isInstance)
                        .map(PlayerStats.class::cast);
                Optional<PlayerStats> newStats = event.getEntity()
                        .getCapability(ModCapabilities.PLAYER_STATS)
                        .resolve()
                        .filter(PlayerStats.class::isInstance)
                        .map(PlayerStats.class::cast);
                newCapabilityPresent = newStats.isPresent();

                copied = copyStatsForClone(oldStats, newStats);
            } finally {
                GoetyArkham.LOGGER.info(
                        "[StatsCapability] PlayerEvent.Clone: isWasDeath={}, oldUuid={}, newUuid={}, oldIdentity={}, newIdentity={}, oldCapabilityPresentBeforeRevive={}, oldCapabilityPresentAfterRevive={}, newCapabilityPresent={}, copied={}",
                        event.isWasDeath(),
                        event.getOriginal().getUUID(),
                        event.getEntity().getUUID(),
                        System.identityHashCode(event.getOriginal()),
                        System.identityHashCode(event.getEntity()),
                        presentBeforeRevive,
                        presentAfterRevive,
                        newCapabilityPresent,
                        copied
                );
                event.getOriginal().invalidateCaps();
            }
        }

        static boolean copyStatsForClone(
                Optional<PlayerStats> oldStats,
                Optional<PlayerStats> newStats) {
            if (oldStats.isEmpty() || newStats.isEmpty()) {
                return false;
            }
            newStats.get().copyFrom(oldStats.get());
            return true;
        }

        @SubscribeEvent
        public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                boolean present = hasStats(player);
                GoetyArkham.LOGGER.info(
                        "[StatsCapability] PlayerLoggedInEvent: player={}, uuid={}, entityIdentity={}, capabilityPresent={}",
                        player.getGameProfile().getName(),
                        player.getUUID(),
                        System.identityHashCode(player),
                        present
                );
                PlayerStatsService.sync(player);
            }
        }

        @SubscribeEvent
        public static void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                boolean present = hasStats(player);
                GoetyArkham.LOGGER.info(
                        "[StatsCapability] PlayerRespawnEvent: player={}, uuid={}, entityIdentity={}, capabilityPresent={}",
                        player.getGameProfile().getName(),
                        player.getUUID(),
                        System.identityHashCode(player),
                        present
                );
                PlayerStatsService.sync(player);
            }
        }

        @SubscribeEvent
        public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                boolean present = hasStats(player);
                GoetyArkham.LOGGER.info(
                        "[StatsCapability] PlayerChangedDimensionEvent: player={}, uuid={}, entityIdentity={}, from={}, to={}, capabilityPresent={}",
                        player.getGameProfile().getName(),
                        player.getUUID(),
                        System.identityHashCode(player),
                        event.getFrom().location(),
                        event.getTo().location(),
                        present
                );
                PlayerStatsService.sync(player);
            }
        }

        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            StatsCommand.register(event.getDispatcher());
        }

        private static boolean hasStats(ServerPlayer player) {
            return player.getCapability(ModCapabilities.PLAYER_STATS).isPresent();
        }

        private static String safePlayerName(ServerPlayer player) {
            return player.getGameProfile() == null
                    ? "<uninitialized>"
                    : player.getGameProfile().getName();
        }
    }
}
