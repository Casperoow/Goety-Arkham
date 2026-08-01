package com.casper.goetyarkham.sanity;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.command.SanityCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SanityEvents {
    private static final ResourceLocation PLAYER_SANITY_ID =
            new ResourceLocation(GoetyArkham.MOD_ID, "player_sanity");

    private SanityEvents() {
    }

    @Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(IPlayerSanity.class);
        }
    }

    @Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof ServerPlayer player) {
                PlayerSanityProvider provider = new PlayerSanityProvider(player);
                event.addCapability(PLAYER_SANITY_ID, provider);
                event.addListener(provider::invalidate);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void clonePlayer(PlayerEvent.Clone event) {
            if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)
                    || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
                return;
            }
            boolean revived = false;
            try {
                oldPlayer.reviveCaps();
                revived = true;
                Optional<PlayerSanityData> oldData = SanityService.mutable(oldPlayer);
                Optional<PlayerSanityData> newData = SanityService.mutable(newPlayer);
                CloneResult result = copyForClone(
                        oldData,
                        newData,
                        event.isWasDeath(),
                        SanityService.getMaximumAttributeValue(oldPlayer));
                GoetyArkham.LOGGER.info(
                        "[Sanity] Player clone: player={}, uuid={}, wasDeath={}, copied={}, collapseDeath={}, permanentLossAdded={}, duplicatePrevented={}",
                        newPlayer.getGameProfile().getName(),
                        newPlayer.getUUID(),
                        event.isWasDeath(),
                        result.copied(),
                        result.collapseDeath(),
                        result.permanentLossAdded(),
                        result.duplicatePrevented());
            } finally {
                if (revived) {
                    oldPlayer.invalidateCaps();
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            refreshAndSync(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                SanityService.completeCollapseRespawn(player);
                SanityService.refreshMaximum(player);
                SanityService.sync(player);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerChangedDimension(
                PlayerEvent.PlayerChangedDimensionEvent event) {
            refreshAndSync(event);
        }

        @SubscribeEvent
        public static void playerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END
                    && event.player instanceof ServerPlayer player) {
                SanityService.tick(player);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void foodFinished(LivingEntityUseItemEvent.Finish event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !event.getItem().isEdible()) {
                return;
            }
            restoreFromFoodIfEligible(player);
        }

        @SubscribeEvent
        public static void sleepFinished(SleepFinishedTimeEvent event) {
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            // Forge fires this before ServerLevel wakes the sleeping players.
            // Capture the exact long-enough sleepers now; players merely carried
            // by the percentage rule are intentionally absent.
            Set<UUID> participants = new LinkedHashSet<>();
            level.players().stream()
                    .filter(ServerPlayer::isSleepingLongEnough)
                    .map(ServerPlayer::getUUID)
                    .forEach(participants::add);
            for (ServerPlayer player : level.players()) {
                if (participants.contains(player.getUUID())) {
                    SanityService.restoreSanity(
                            player, 1, SanityChangeCause.SLEEP);
                }
            }
        }

        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            SanityCommand.register(event.getDispatcher());
        }

        public static int restoreFromFoodIfEligible(ServerPlayer player) {
            int current = SanityService.getCurrentSanity(player);
            int maximum = SanityService.getMaximumSanity(player);
            if (!SanityMath.canFoodRestore(current, maximum)) {
                return 0;
            }
            return SanityService.restoreSanity(
                    player, 1, SanityChangeCause.FOOD);
        }

        private static void refreshAndSync(PlayerEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                SanityService.refreshMaximum(player);
                SanityService.sync(player);
            }
        }
    }

    static CloneResult copyForClone(
            Optional<PlayerSanityData> oldOptional,
            Optional<PlayerSanityData> newOptional,
            boolean wasDeath,
            double preDeathAttributeValue) {
        if (oldOptional.isEmpty() || newOptional.isEmpty()) {
            return CloneResult.NOT_COPIED;
        }
        PlayerSanityData oldData = oldOptional.get();
        PlayerSanityData newData = newOptional.get();
        if (wasDeath && newData.isCloneDeathSettled()) {
            return new CloneResult(true, oldData.getCurrentSanity() == 0,
                    0, true);
        }

        int preDeathMaximum = SanityMath.maximumSanity(
                preDeathAttributeValue, oldData.getPermanentMaxLoss());
        newData.copyFrom(oldData, preDeathMaximum);

        boolean collapseDeath = wasDeath && oldData.getCurrentSanity() == 0;
        int permanentLossAdded = 0;
        if (collapseDeath && newData.beginCloneDeathSettlement()) {
            int loss = newData.getPermanentMaxLoss();
            if (loss < SanityConstants.MAX_PERMANENT_LOSS
                    && preDeathMaximum > SanityConstants.MINIMUM_MAXIMUM) {
                permanentLossAdded = Math.max(
                        0, newData.setPermanentMaxLoss(loss + 1));
            }
            int respawnMaximum = SanityMath.maximumSanity(
                    preDeathAttributeValue, newData.getPermanentMaxLoss());
            newData.setCurrentSanity(respawnMaximum, respawnMaximum);
            newData.resetCollapseAfterDeath();
            newData.markPendingCollapseRespawnRefill();
        }
        return new CloneResult(
                true, collapseDeath, permanentLossAdded, false);
    }

    record CloneResult(
            boolean copied,
            boolean collapseDeath,
            int permanentLossAdded,
            boolean duplicatePrevented) {
        private static final CloneResult NOT_COPIED =
                new CloneResult(false, false, 0, false);
    }
}
