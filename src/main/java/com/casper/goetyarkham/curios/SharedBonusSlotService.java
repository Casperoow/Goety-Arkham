package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
     * current equip state and syncs the slot to it, allowing the slot to
     * shrink (and evacuate any overflow contents) if the computed target is
     * smaller than the current capacity. Stateless and idempotent: safe to
     * call repeatedly. Use this only for a genuinely confirmed equip-state
     * change (e.g. a real {@code CurioChangeEvent} transition) - never for a
     * login/respawn/clone/dimension-change restore, where a provider's
     * equip state may not have finished settling yet; see {@link
     * #reconcileRestore}.
     */
    public static void reconcile(ServerPlayer player) {
        reconcile(player, ReconcileMode.CONFIRMED_SHRINK, "confirmed");
    }

    /**
     * Same recomputation as {@link #reconcile}, but for a player
     * entity/Capability/Curios handler that was just (re)created - login,
     * respawn, clone, or dimension change. The slot may grow (or have its
     * modifier re-applied at the same size) but is never shrunk or
     * evacuated here: a provider that is transiently unreadable at this
     * point (Curios handler not yet resolved, equipped-item state not yet
     * settled) must not be misread as "unequipped" and trigger a
     * destructive evacuation of whatever the player had stored.
     */
    public static void reconcileRestore(ServerPlayer player) {
        reconcile(player, ReconcileMode.RESTORE, "restore");
    }

    private static void reconcile(ServerPlayer player, ReconcileMode mode, String source) {
        List<SharedBonusSlotProvider> providers = SharedBonusSlotProviderRegistry.providers();
        int target = computeCapacity(providers, player);
        if (GoetyArkham.LOGGER.isDebugEnabled()) {
            GoetyArkham.LOGGER.debug(
                    "[SharedBonusSlot] Reconcile player={} uuid={} entityId={} mode={} source={}"
                            + " target={} providers={}",
                    player.getGameProfile().getName(), player.getUUID(), player.getId(), mode,
                    source, target,
                    providers.stream()
                            .map(provider -> provider.providerId() + "="
                                    + provider.isEquipped(player) + "/"
                                    + provider.declaredSlotCount())
                            .collect(Collectors.joining(",")));
        }
        DynamicCurioSlotContributionService.reconcileSize(
                player,
                CurioSlotIds.SKILL_BONUS,
                CAPACITY_MODIFIER_ID,
                CAPACITY_MODIFIER_NAME,
                target,
                mode,
                source);
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
