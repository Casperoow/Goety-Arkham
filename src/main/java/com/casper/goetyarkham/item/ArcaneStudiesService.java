package com.casper.goetyarkham.item;

import com.casper.goetyarkham.client.ClientArcaneStudiesBonus;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.curios.ResourceSlotProviderRegistry;
import com.casper.goetyarkham.curios.ResourceSlotService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

/**
 * Owns Arcane Studies' participation in the shared {@link
 * CurioSlotIds#RESOURCE} slot pool, worn in {@link CurioSlotIds#ASSET}.
 * Mirrors {@link PhysicalTrainingService} exactly: registers {@link
 * ArcaneStudiesBonusProvider} once (on first class load) and, on every
 * observed equip/unequip transition, asks {@link ResourceSlotService} to
 * recompute the shared slot's capacity from every currently registered
 * provider's equip state - the same generic mechanism the Encyclopedia and
 * Physical Training already use, so all of them coexist without any one
 * needing to know about the others.
 */
public final class ArcaneStudiesService {
    private static final List<String> WORN_SLOTS = List.of(CurioSlotIds.ASSET);

    static {
        ResourceSlotProviderRegistry.register(ArcaneStudiesBonusProvider.INSTANCE);
    }

    private ArcaneStudiesService() {
    }

    /**
     * Confirmed equip-state reconcile, for a real observed Curios
     * equip/unequip transition. Stateless: it only re-derives the shared
     * slot's target capacity from whichever providers are currently
     * equipped, so calling it repeatedly (or out of order with any other
     * provider's own reconcile call) never duplicates or loses slots. May
     * shrink the shared slot and evacuate overflow contents - see {@link
     * ResourceSlotService#reconcile}.
     */
    public static void reconcile(ServerPlayer player) {
        ResourceSlotService.reconcile(player);
    }

    /**
     * Login/respawn/clone/dimension-change restore. Unlike {@link
     * #reconcile}, this never shrinks the shared slot or evacuates its
     * contents - see {@link ResourceSlotService#reconcileRestore}.
     */
    public static void reconcileRestore(ServerPlayer player) {
        ResourceSlotService.reconcileRestore(player);
    }

    public static boolean isWearing(ServerPlayer player) {
        return DynamicCurioSlotContributionService.isWearing(
                player, ModItems.ARCANE_STUDIES.get(), WORN_SLOTS);
    }

    /** Widened, read-only overload of {@link #isWearing(ServerPlayer)} for the local client player. */
    public static boolean isWearing(LivingEntity entity) {
        return DynamicCurioSlotContributionService.isWearing(
                entity, ModItems.ARCANE_STUDIES.get(), WORN_SLOTS);
    }

    public static int equippedCount(ServerPlayer player) {
        return DynamicCurioSlotContributionService.equippedCount(
                player, ModItems.ARCANE_STUDIES.get(), WORN_SLOTS);
    }

    /**
     * Client-safe live snapshot of this provider's own current {@code
     * resource} contribution, for {@code ArcaneStudiesItem}'s Shift
     * tooltip. Mirrors {@link PhysicalTrainingService#currentClientBonus()}.
     */
    public static ResourceSlotBonusSnapshot currentClientBonus() {
        ResourceSlotBonusSnapshot result = DistExecutor.unsafeCallWhenOn(
                Dist.CLIENT, () -> ClientArcaneStudiesBonus::compute);
        return result == null ? ResourceSlotBonusSnapshot.UNAVAILABLE : result;
    }
}
