package com.casper.goetyarkham.soul;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.willpower.WillpowerEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class SoulPoolEvents {
    private static final ResourceLocation PLAYER_SOUL_POOL_ID =
            new ResourceLocation(GoetyArkham.MOD_ID, "player_soul_pool");

    private SoulPoolEvents() {
    }

    @Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(PlayerSoulPoolData.class);
        }
    }

    @Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof ServerPlayer) {
                PlayerSoulPoolProvider provider = new PlayerSoulPoolProvider();
                event.addCapability(PLAYER_SOUL_POOL_ID, provider);
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
                        .getCapability(SoulPoolCapabilities.PLAYER_SOUL_POOL)
                        .resolve()
                        .ifPresent(oldData -> newPlayer
                                .getCapability(SoulPoolCapabilities.PLAYER_SOUL_POOL)
                                .resolve()
                                .ifPresent(newData -> newData.copyFrom(oldData)));
            } finally {
                event.getOriginal().invalidateCaps();
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            refresh(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
            refresh(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            refresh(event);
        }

        @SubscribeEvent
        public static void playerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END
                    && event.player instanceof ServerPlayer player) {
                WillpowerEffects.refreshSpellPowerMirror(player);
                SoulEnergyPoolService.refresh(player);
            }
        }

        private static void refresh(PlayerEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                SoulEnergyPoolService.refresh(player);
            }
        }
    }
}
