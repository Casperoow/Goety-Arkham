package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.curios.MultiEquippableCurio;
import com.casper.goetyarkham.curios.ReconcileMode;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Owns the {@link CurioSlotIds#TOKEN} slot capacity contributed by every
 * equipped Charisma, worn in {@link CurioSlotIds#ASSET}. Mirrors {@link
 * RelicHunterService} exactly, but targets the {@code token} slot: Charisma
 * is also a {@link MultiEquippableCurio}, so more than one may be equipped
 * at once, each contributing its own +1 token slot. Its target capacity is
 * therefore a live count - the number of currently equipped Charismas - kept
 * in sync via a single, service-owned modifier through {@link
 * DynamicCurioSlotContributionService#reconcileSize}.
 */
public final class CharismaService {
    private static final UUID TOKEN_SLOT_MODIFIER_ID = UUID.fromString(
            "c2a5b4d0-7e3f-4a8b-9c4d-6f0a3b82e517");
    private static final String TOKEN_SLOT_MODIFIER_NAME =
            "goetyarkham:charisma";
    private static final List<String> WORN_SLOTS = List.of(CurioSlotIds.ASSET);

    private CharismaService() {
    }

    /**
     * Confirmed equip-state reconcile, for a real observed Curios
     * equip/unequip transition. Stateless: it only re-derives the target
     * capacity from however many Charismas are currently equipped, so
     * calling it repeatedly never duplicates or loses slots. May shrink the
     * token slot and safely evacuate overflow contents - see {@link
     * DynamicCurioSlotContributionService#reconcileSize}.
     */
    public static void reconcile(ServerPlayer player) {
        reconcile(player, ReconcileMode.CONFIRMED_SHRINK, "confirmed");
    }

    /**
     * Login/respawn/clone/dimension-change restore. Unlike {@link
     * #reconcile}, this never shrinks the token slot or evacuates its
     * contents: a Charisma that is transiently unreadable at this point
     * (Curios handler not yet resolved) must not be misread as unequipped.
     */
    public static void reconcileRestore(ServerPlayer player) {
        reconcile(player, ReconcileMode.RESTORE, "restore");
    }

    private static void reconcile(ServerPlayer player, ReconcileMode mode, String source) {
        DynamicCurioSlotContributionService.reconcileSize(
                player,
                CurioSlotIds.TOKEN,
                TOKEN_SLOT_MODIFIER_ID,
                TOKEN_SLOT_MODIFIER_NAME,
                equippedCount(player),
                mode,
                source);
    }

    public static int equippedCount(ServerPlayer player) {
        return DynamicCurioSlotContributionService.equippedCount(
                player, ModItems.CHARISMA.get(), WORN_SLOTS);
    }
}
