package com.casper.goetyarkham.item;

import com.casper.goetyarkham.client.ClientEncyclopediaBonus;
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
 * Owns the Encyclopedia's participation in the shared {@link
 * CurioSlotIds#RESOURCE} slot pool, worn in either {@link
 * CurioSlotIds#HANDS} or {@link CurioSlotIds#BOOK}. This item never keeps
 * its own dedicated slot or modifier: it simply registers {@link
 * EncyclopediaBonusProvider} once (on first class load, before it is ever
 * consulted - see the static initializer below) and, on every observed
 * equip/unequip transition, asks {@link ResourceSlotService} to recompute
 * the shared slot's capacity from every currently registered provider's
 * equip state.
 */
public final class EncyclopediaService {
    private static final List<String> WORN_SLOTS =
            List.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK);

    static {
        ResourceSlotProviderRegistry.register(EncyclopediaBonusProvider.INSTANCE);
    }

    private EncyclopediaService() {
    }

    /**
     * Confirmed equip-state reconcile, for a real observed Curios
     * equip/unequip transition. Stateless: it only re-derives the shared
     * slot's target capacity from whichever providers are currently
     * equipped, so calling it repeatedly (or out of order with any other
     * provider's own reconcile call) never duplicates or loses slots. May
     * shrink the slot and evacuate overflow contents - see {@link
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
                player, ModItems.ENCYCLOPEDIA.get(), WORN_SLOTS);
    }

    /**
     * Widened, read-only overload of {@link #isWearing(ServerPlayer)} for
     * the local client player: whether the player has an Encyclopedia
     * equipped by item type - not whether the specific {@code ItemStack}
     * instance a tooltip happens to be hovering is the equipped one, so
     * viewing a second, unequipped Encyclopedia in the inventory while
     * another copy is worn still reports {@code true}, and wearing two at
     * once still reports a single {@code true} rather than double-counting.
     */
    public static boolean isWearing(LivingEntity entity) {
        return DynamicCurioSlotContributionService.isWearing(
                entity, ModItems.ENCYCLOPEDIA.get(), WORN_SLOTS);
    }

    public static int equippedCount(ServerPlayer player) {
        return DynamicCurioSlotContributionService.equippedCount(
                player, ModItems.ENCYCLOPEDIA.get(), WORN_SLOTS);
    }

    /**
     * Client-safe live snapshot of this provider's own current {@code
     * resource} contribution, for {@link EncyclopediaItem}'s Shift
     * tooltip. Mirrors {@link ShiftTooltipHelper}'s {@code DistExecutor}
     * pattern: returns {@link EncyclopediaTooltipBonus#UNAVAILABLE} on a
     * dedicated server or whenever there is no local client player (main
     * menu, creative item search).
     */
    public static EncyclopediaTooltipBonus currentClientBonus() {
        EncyclopediaTooltipBonus result = DistExecutor.unsafeCallWhenOn(
                Dist.CLIENT, () -> ClientEncyclopediaBonus::compute);
        return result == null ? EncyclopediaTooltipBonus.UNAVAILABLE : result;
    }
}
