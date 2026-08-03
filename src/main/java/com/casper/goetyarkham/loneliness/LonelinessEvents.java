package com.casper.goetyarkham.loneliness;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class LonelinessEvents {
    private static final ResourceLocation PLAYER_LONELINESS_ID =
            new ResourceLocation(GoetyArkham.MOD_ID, "player_loneliness");

    private LonelinessEvents() {
    }

    @Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(IPlayerLoneliness.class);
        }
    }

    @Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof ServerPlayer) {
                PlayerLonelinessProvider provider = new PlayerLonelinessProvider();
                event.addCapability(PLAYER_LONELINESS_ID, provider);
                event.addListener(provider::invalidate);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void clonePlayer(PlayerEvent.Clone event) {
            if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
                return;
            }
            try {
                event.getOriginal().reviveCaps();
                event.getOriginal()
                        .getCapability(LonelinessCapabilities.PLAYER_LONELINESS)
                        .resolve()
                        .filter(PlayerLonelinessData.class::isInstance)
                        .map(PlayerLonelinessData.class::cast)
                        .ifPresent(oldData -> newPlayer
                                .getCapability(LonelinessCapabilities.PLAYER_LONELINESS)
                                .resolve()
                                .filter(PlayerLonelinessData.class::isInstance)
                                .map(PlayerLonelinessData.class::cast)
                                .ifPresent(newData -> newData.copyFrom(oldData)));
            } finally {
                event.getOriginal().invalidateCaps();
            }
            LonelinessService.reconcile(newPlayer);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            reconcile(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
            reconcile(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerChangedDimension(
                PlayerEvent.PlayerChangedDimensionEvent event) {
            reconcile(event);
        }

        private static void reconcile(PlayerEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                LonelinessService.reconcile(player);
            }
        }
    }
}
