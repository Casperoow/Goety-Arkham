package com.casper.goetyarkham.curios;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Owns the {@link CurioSlotIds#SKILL_BONUS} slot's capacity: the maximum
 * {@linkplain SharedBonusSlotProvider#declaredSlotCount() declared count} of
 * every currently equipped, distinct (by provider ID) {@link
 * SharedBonusSlotProvider} in {@link SharedBonusSlotProviderRegistry} - never
 * a sum, so three providers declaring {@code 1, 3, 3} yield a capacity of
 * {@code 3}, not {@code 7}. A single, service-owned slot modifier ({@link
 * DynamicCurioSlotContributionService#reconcileSize}) is kept in sync with
 * that value; nothing here is item-specific, so any future shared-slot
 * provider is picked up automatically the moment it registers itself.
 */
public final class SharedBonusSlotService {
    private static final UUID CAPACITY_MODIFIER_ID = UUID.fromString(
            "7d9a4b1e-2c3f-4a5b-9c6d-1e2f3a4b5c6d");
    private static final String CAPACITY_MODIFIER_NAME =
            "goetyarkham:skill_bonus_capacity";

    private SharedBonusSlotService() {
    }

    /**
     * Recomputes the target capacity from every registered provider's
     * current equip state and syncs the slot to it. Stateless and
     * idempotent: safe to call repeatedly, and safe to call whenever any
     * one provider's equip state might have changed, since it always
     * re-derives the answer from scratch rather than incrementing/
     * decrementing.
     */
    public static void reconcile(ServerPlayer player) {
        DynamicCurioSlotContributionService.reconcileSize(
                player,
                CurioSlotIds.SKILL_BONUS,
                CAPACITY_MODIFIER_ID,
                CAPACITY_MODIFIER_NAME,
                targetCapacity(player));
    }

    public static int targetCapacity(ServerPlayer player) {
        return computeCapacity(SharedBonusSlotProviderRegistry.providers(), player);
    }

    /** Pure capacity math, exposed for direct testing with throwaway provider stand-ins. */
    static int computeCapacity(List<SharedBonusSlotProvider> providers, ServerPlayer player) {
        int max = 0;
        for (SharedBonusSlotProvider provider : providers) {
            if (provider.isEquipped(player)) {
                max = Math.max(max, provider.declaredSlotCount());
            }
        }
        return max;
    }
}
