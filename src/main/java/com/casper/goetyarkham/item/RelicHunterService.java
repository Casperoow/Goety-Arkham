package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.curios.MultiEquippableCurio;
import com.casper.goetyarkham.curios.ReconcileMode;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Owns the {@link CurioSlotIds#CHARM} slot capacity contributed by every
 * equipped Relic Hunter, worn in {@link CurioSlotIds#ASSET}. Unlike the
 * single-instance focus-slot contributors ({@link
 * ArcaneInitiatesTokenService}, {@link BookOfShadowsService}), Relic Hunter
 * is a {@link MultiEquippableCurio}: more than one may be equipped at once,
 * each contributing its own +1 charm slot. Its target capacity is therefore
 * a live count - the number of currently equipped Relic Hunters - kept in
 * sync via a single, service-owned modifier through {@link
 * DynamicCurioSlotContributionService#reconcileSize}, the same mechanism
 * {@link com.casper.goetyarkham.curios.ResourceSlotService} uses for a
 * computed-capacity shared slot, rather than the boolean per-item
 * ensure/remove used for a single-instance grant.
 */
public final class RelicHunterService {
    private static final UUID CHARM_SLOT_MODIFIER_ID = UUID.fromString(
            "b1f4a3c9-6d2e-4f7a-8b3c-5e9d2a71f406");
    private static final String CHARM_SLOT_MODIFIER_NAME =
            "goetyarkham:relic_hunter";
    private static final List<String> WORN_SLOTS = List.of(CurioSlotIds.ASSET);

    private RelicHunterService() {
    }

    /**
     * Confirmed equip-state reconcile, for a real observed Curios
     * equip/unequip transition. Stateless: it only re-derives the target
     * capacity from however many Relic Hunters are currently equipped, so
     * calling it repeatedly never duplicates or loses slots. May shrink the
     * charm slot and safely evacuate overflow contents - see {@link
     * DynamicCurioSlotContributionService#reconcileSize}.
     */
    public static void reconcile(ServerPlayer player) {
        reconcile(player, ReconcileMode.CONFIRMED_SHRINK, "confirmed");
    }

    /**
     * Login/respawn/clone/dimension-change restore. Unlike {@link
     * #reconcile}, this never shrinks the charm slot or evacuates its
     * contents: a Relic Hunter that is transiently unreadable at this point
     * (Curios handler not yet resolved) must not be misread as unequipped.
     */
    public static void reconcileRestore(ServerPlayer player) {
        reconcile(player, ReconcileMode.RESTORE, "restore");
    }

    private static void reconcile(ServerPlayer player, ReconcileMode mode, String source) {
        DynamicCurioSlotContributionService.reconcileSize(
                player,
                CurioSlotIds.CHARM,
                CHARM_SLOT_MODIFIER_ID,
                CHARM_SLOT_MODIFIER_NAME,
                equippedCount(player),
                mode,
                source);
    }

    public static int equippedCount(ServerPlayer player) {
        return DynamicCurioSlotContributionService.equippedCount(
                player, ModItems.RELIC_HUNTER.get(), WORN_SLOTS);
    }
}
