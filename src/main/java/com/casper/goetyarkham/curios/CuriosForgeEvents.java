package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.command.CuriosCommand;
import com.casper.goetyarkham.item.ArcaneInitiatesTokenService;
import com.casper.goetyarkham.item.BookOfShadowsService;
import com.casper.goetyarkham.item.EncyclopediaService;
import com.casper.goetyarkham.item.HeirloomOfHyperboreaService;
import com.casper.goetyarkham.item.ModItems;
import com.casper.goetyarkham.item.RolandsThirtyEightSpecialService;
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
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class CuriosForgeEvents {
    /**
     * How long after a login/respawn/clone/dimension-change restore reconcile
     * (see {@link EncyclopediaService#reconcileRestore}, which never shrinks)
     * to run one follow-up confirmed reconcile that re-derives the real
     * equip state and is allowed to shrink. This is only ever a
     * <em>secondary</em> signal alongside the real state check performed at
     * that later tick - never the sole basis for a destructive action - so a
     * provider that is genuinely still equipped is unaffected no matter how
     * long Curios took to settle, while a provider that was truly removed
     * (e.g. edited out of the save between sessions) still eventually gets
     * its stale capacity corrected instead of staying inflated forever.
     */
    private static final int ENCYCLOPEDIA_RESTORE_CONFIRM_DELAY_TICKS = 20;

    private static final Set<UUID> DIRTY_EQUIPMENT =
            ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_HEIRLOOM_RECONCILE =
            ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_WENDYS_AMULET_RECONCILE =
            ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_ARCANE_TOKEN_RECONCILE =
            ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_BOOK_OF_SHADOWS_RECONCILE =
            ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_ENCYCLOPEDIA_RECONCILE =
            ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_ROLAND_RECONCILE =
            ConcurrentHashMap.newKeySet();
    /** Tick (per-player {@code tickCount}) at which to run the follow-up confirmed reconcile. */
    private static final Map<UUID, Integer> ENCYCLOPEDIA_RESTORE_CONFIRM_AT_TICK =
            new ConcurrentHashMap<>();

    private CuriosForgeEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CuriosCommand.register(event.getDispatcher());
    }

    /**
     * Pre-equip legality gate shared by every Goety: Arkham Curio. Curios
     * posts this event from inside its slot's {@code isItemValid} check
     * (drag-and-drop, shift-click quick move, right-click auto-equip, and
     * creative-mode placement all route through it), so denying here stops
     * the item before it ever enters the slot rather than ejecting it
     * afterward. Only this mod's own registered items are restricted.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventDuplicateGoetyArkhamCurio(CurioEquipEvent event) {
        if (CurioEquipRules.isDuplicateElsewhere(
                event.getSlotContext(), event.getStack())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** Curios posts this event before applying its attribute add/remove operation. */
    @SubscribeEvent
    public static void curioChanged(CurioChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DIRTY_EQUIPMENT.add(player.getUUID());
            handleHeirloomTransition(player, event);
            handleWendysAmuletTransition(player, event);
            handleArcaneInitiatesTokenTransition(player, event);
            handleBookOfShadowsTransition(player, event);
            handleEncyclopediaTransition(player, event);
            handleRolandTransition(player, event);
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
        UUID uuid = player.getUUID();
        if (PENDING_HEIRLOOM_RECONCILE.remove(uuid)) {
            HeirloomOfHyperboreaService.reconcile(player);
        }
        if (PENDING_WENDYS_AMULET_RECONCILE.remove(uuid)) {
            WendysAmuletService.reconcile(player);
        }
        if (PENDING_ARCANE_TOKEN_RECONCILE.remove(uuid)) {
            ArcaneInitiatesTokenService.reconcile(player);
        }
        if (PENDING_BOOK_OF_SHADOWS_RECONCILE.remove(uuid)) {
            BookOfShadowsService.reconcile(player);
        }
        if (PENDING_ENCYCLOPEDIA_RECONCILE.remove(uuid)) {
            // Restore, not confirmed: the Curios handler for a just
            // (re)created player entity may not have finished settling its
            // equipped-item state yet, so this must never shrink/evacuate
            // resource - only grow or leave it alone. See the follow-up
            // confirmed pass scheduled right below and run once the restore
            // window elapses.
            EncyclopediaService.reconcileRestore(player);
            ENCYCLOPEDIA_RESTORE_CONFIRM_AT_TICK.put(uuid,
                    player.tickCount + ENCYCLOPEDIA_RESTORE_CONFIRM_DELAY_TICKS);
        }
        Integer confirmAtTick = ENCYCLOPEDIA_RESTORE_CONFIRM_AT_TICK.get(uuid);
        if (confirmAtTick != null && player.tickCount >= confirmAtTick) {
            ENCYCLOPEDIA_RESTORE_CONFIRM_AT_TICK.remove(uuid);
            // The delay is only ever a secondary signal: this still
            // re-derives the real, current equip state before deciding
            // whether to shrink, so it can never wrongly evict a provider
            // that is genuinely still equipped, however long Curios took to
            // settle. It exists so a provider that really is gone (e.g. the
            // save was edited between sessions) doesn't leave resource's
            // capacity permanently stuck inflated from the restore pass.
            EncyclopediaService.reconcile(player);
        }
        if (PENDING_ROLAND_RECONCILE.remove(uuid)) {
            RolandsThirtyEightSpecialService.reconcile(player);
        }
        if (!DIRTY_EQUIPMENT.remove(uuid)) {
            return;
        }
        EquipmentStatsService.refresh(player);
        SanityService.refreshMaximum(player);
        SanityService.sync(player);
        // Curios has already applied/removed max-health modifiers (e.g. the
        // Leather Coat's) by this point, but vanilla never re-clamps current
        // health down when an attribute reduces the maximum on its own.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            DIRTY_EQUIPMENT.remove(uuid);
            PENDING_HEIRLOOM_RECONCILE.remove(uuid);
            PENDING_WENDYS_AMULET_RECONCILE.remove(uuid);
            PENDING_ARCANE_TOKEN_RECONCILE.remove(uuid);
            PENDING_BOOK_OF_SHADOWS_RECONCILE.remove(uuid);
            PENDING_ENCYCLOPEDIA_RECONCILE.remove(uuid);
            PENDING_ROLAND_RECONCILE.remove(uuid);
            ENCYCLOPEDIA_RESTORE_CONFIRM_AT_TICK.remove(uuid);
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
        RolandsThirtyEightSpecialService.copyPersistentState(
                event.getOriginal(), event.getEntity());
        queueReconcile(event);
    }

    private static void queueReconcile(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            PENDING_HEIRLOOM_RECONCILE.add(uuid);
            PENDING_WENDYS_AMULET_RECONCILE.add(uuid);
            PENDING_ARCANE_TOKEN_RECONCILE.add(uuid);
            PENDING_BOOK_OF_SHADOWS_RECONCILE.add(uuid);
            PENDING_ENCYCLOPEDIA_RECONCILE.add(uuid);
            PENDING_ROLAND_RECONCILE.add(uuid);
        }
    }

    /**
     * The extra focus slot is never auto-filled with a generated item, so
     * unlike {@link #handleHeirloomTransition} / {@link
     * #handleWendysAmuletTransition} there is no one-time side effect to
     * guard against replaying: {@link ArcaneInitiatesTokenService#reconcile}
     * is idempotent and can simply be called on every observed token slot
     * change.
     */
    private static void handleArcaneInitiatesTokenTransition(
            ServerPlayer player, CurioChangeEvent event) {
        if (!CurioSlotIds.TOKEN.equals(event.getIdentifier())) {
            return;
        }
        if (event.getFrom().is(ModItems.ARCANE_INITIATES_TOKEN.get())
                || event.getTo().is(ModItems.ARCANE_INITIATES_TOKEN.get())) {
            ArcaneInitiatesTokenService.reconcile(player);
        }
    }

    /**
     * Same rationale as {@link #handleArcaneInitiatesTokenTransition}: the
     * extra focus slot is never auto-filled, so {@link
     * BookOfShadowsService#reconcile} is idempotent and safe to call on
     * every observed change to either supported slot.
     */
    private static void handleBookOfShadowsTransition(
            ServerPlayer player, CurioChangeEvent event) {
        if (!CurioSlotIds.HANDS.equals(event.getIdentifier())
                && !CurioSlotIds.BOOK.equals(event.getIdentifier())) {
            return;
        }
        if (event.getFrom().is(ModItems.BOOK_OF_SHADOWS.get())
                || event.getTo().is(ModItems.BOOK_OF_SHADOWS.get())) {
            BookOfShadowsService.reconcile(player);
        }
    }

    /**
     * Same rationale as {@link #handleBookOfShadowsTransition}: the shared
     * {@link CurioSlotIds#RESOURCE} slot's capacity is never auto-filled, so
     * {@link EncyclopediaService#reconcile} (which recomputes that shared
     * capacity from every registered provider) is idempotent and safe to
     * call on every observed change to either supported slot.
     */
    private static void handleEncyclopediaTransition(
            ServerPlayer player, CurioChangeEvent event) {
        if (!CurioSlotIds.HANDS.equals(event.getIdentifier())
                && !CurioSlotIds.BOOK.equals(event.getIdentifier())) {
            return;
        }
        if (event.getFrom().is(ModItems.ENCYCLOPEDIA.get())
                || event.getTo().is(ModItems.ENCYCLOPEDIA.get())) {
            EncyclopediaService.reconcile(player);
        }
    }

    private static void handleRolandTransition(
            ServerPlayer player, CurioChangeEvent event) {
        if (!CurioSlotIds.HANDS.equals(event.getIdentifier())) {
            return;
        }
        boolean from = event.getFrom().is(
                ModItems.ROLANDS_38_SPECIAL.get());
        boolean to = event.getTo().is(
                ModItems.ROLANDS_38_SPECIAL.get());
        if (!from && !to) {
            return;
        }

        // Curios discovers the change while comparing its previous stack to
        // the already-committed current handler contents.
        int after = RolandsThirtyEightSpecialService.equippedCount(player);
        int before = after - (to ? 1 : 0) + (from ? 1 : 0);
        if (before <= 0 && after > 0) {
            RolandsThirtyEightSpecialService.equipTransition(player);
        } else if (before > 0 && after <= 0) {
            // This occurs before Curios settles any remaining modifiers.
            RolandsThirtyEightSpecialService.unequipTransition(player);
        } else {
            PENDING_ROLAND_RECONCILE.add(player.getUUID());
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
