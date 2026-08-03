package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.command.CuriosCommand;
import com.casper.goetyarkham.item.HeirloomOfHyperboreaService;
import com.casper.goetyarkham.item.ModItems;
import com.casper.goetyarkham.item.WendysAmuletService;
import com.casper.goetyarkham.sanity.SanityService;
import com.casper.goetyarkham.sanity.weakness.ILockedWeakness;
import com.casper.goetyarkham.stats.EquipmentStatsService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class CuriosForgeEvents {
    private static final Set<UUID> DIRTY_EQUIPMENT =
            ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_HEIRLOOM_RECONCILE =
            ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_WENDYS_AMULET_RECONCILE =
            ConcurrentHashMap.newKeySet();

    private CuriosForgeEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CuriosCommand.register(event.getDispatcher());
    }

    /** Curios posts this event before applying its attribute add/remove operation. */
    @SubscribeEvent
    public static void curioChanged(CurioChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DIRTY_EQUIPMENT.add(player.getUUID());
            handleHeirloomTransition(player, event);
            handleWendysAmuletTransition(player, event);
        }
    }

    /** Generic server-side final guard for locked weakness items. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void curioUnequip(CurioUnequipEvent event) {
        if (CurioSlotIds.WEAKNESS.equals(
                event.getSlotContext().identifier())
                && event.getStack().getItem() instanceof ILockedWeakness locked
                && locked.preventsManualUnequip(
                event.getSlotContext(), event.getStack())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** Settle once, later in the same entity tick, after Curios finishes the change. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PENDING_HEIRLOOM_RECONCILE.remove(player.getUUID())) {
            HeirloomOfHyperboreaService.reconcile(player);
        }
        if (PENDING_WENDYS_AMULET_RECONCILE.remove(player.getUUID())) {
            WendysAmuletService.reconcile(player);
        }
        if (!DIRTY_EQUIPMENT.remove(player.getUUID())) {
            return;
        }
        EquipmentStatsService.refresh(player);
        SanityService.refreshMaximum(player);
        SanityService.sync(player);
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DIRTY_EQUIPMENT.remove(player.getUUID());
            PENDING_HEIRLOOM_RECONCILE.remove(player.getUUID());
            PENDING_WENDYS_AMULET_RECONCILE.remove(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        queueReconcile(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        queueReconcile(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        queueReconcile(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void clonePlayer(PlayerEvent.Clone event) {
        HeirloomOfHyperboreaService.copyPersistentState(
                event.getOriginal(), event.getEntity());
        WendysAmuletService.copyPersistentState(
                event.getOriginal(), event.getEntity());
        queueReconcile(event);
    }

    private static void queueReconcile(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_HEIRLOOM_RECONCILE.add(player.getUUID());
            PENDING_WENDYS_AMULET_RECONCILE.add(player.getUUID());
        }
    }

    private static void handleWendysAmuletTransition(
            ServerPlayer player, CurioChangeEvent event) {
        if (!CurioSlotIds.CHARM.equals(event.getIdentifier())) {
            return;
        }
        boolean from = event.getFrom().is(
                ModItems.WENDYS_AMULET.get());
        boolean to = event.getTo().is(
                ModItems.WENDYS_AMULET.get());
        if (!from && !to) {
            return;
        }

        // Curios discovers the change while comparing its previous stack to
        // the already-committed current handler contents.
        int after = WendysAmuletService.equippedCount(player);
        int before = after - (to ? 1 : 0) + (from ? 1 : 0);
        if (before <= 0 && after > 0) {
            WendysAmuletService.equipTransition(player);
        } else if (before > 0 && after <= 0) {
            // This occurs before Curios settles any remaining modifiers.
            WendysAmuletService.unequipTransition(player);
        } else {
            PENDING_WENDYS_AMULET_RECONCILE.add(player.getUUID());
        }
    }

    private static void handleHeirloomTransition(
            ServerPlayer player, CurioChangeEvent event) {
        if (!CurioSlotIds.NECKLACE.equals(event.getIdentifier())) {
            return;
        }
        boolean from = event.getFrom().is(
                ModItems.HEIRLOOM_OF_HYPERBOREA.get());
        boolean to = event.getTo().is(
                ModItems.HEIRLOOM_OF_HYPERBOREA.get());
        if (!from && !to) {
            return;
        }

        // Curios discovers the change while comparing its previous stack to
        // the already-committed current handler contents.
        int after = HeirloomOfHyperboreaService.equippedCount(player);
        int before = after - (to ? 1 : 0) + (from ? 1 : 0);
        if (before <= 0 && after > 0) {
            HeirloomOfHyperboreaService.equipTransition(player);
        } else if (before > 0 && after <= 0) {
            // This occurs before Curios settles any remaining modifiers.
            HeirloomOfHyperboreaService.unequipTransition(player);
        } else {
            PENDING_HEIRLOOM_RECONCILE.add(player.getUUID());
        }
    }
}
