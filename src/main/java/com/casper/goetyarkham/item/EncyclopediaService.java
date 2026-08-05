package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.curios.SharedBonusSlotProviderRegistry;
import com.casper.goetyarkham.curios.SharedBonusSlotService;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Owns the Encyclopedia's participation in the shared {@link
 * CurioSlotIds#SKILL_BONUS} slot pool, worn in either {@link
 * CurioSlotIds#HANDS} or {@link CurioSlotIds#BOOK}. This item never keeps
 * its own dedicated slot or modifier: it simply registers {@link
 * EncyclopediaBonusProvider} once (on first class load, before it is ever
 * consulted - see the static initializer below) and, on every observed
 * equip/unequip transition, asks {@link SharedBonusSlotService} to
 * recompute the shared slot's capacity from every currently registered
 * provider's equip state.
 */
public final class EncyclopediaService {
    private static final List<String> WORN_SLOTS =
            List.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK);

    static {
        SharedBonusSlotProviderRegistry.register(EncyclopediaBonusProvider.INSTANCE);
    }

    private EncyclopediaService() {
    }

    /**
     * Login/clone/dimension/sync-safe repair. Stateless: it only re-derives
     * the shared slot's target capacity from whichever providers are
     * currently equipped, so calling it repeatedly (or out of order with
     * any other provider's own reconcile call) never duplicates or loses
     * slots.
     */
    public static void reconcile(ServerPlayer player) {
        SharedBonusSlotService.reconcile(player);
    }

    public static boolean isWearing(ServerPlayer player) {
        return DynamicCurioSlotContributionService.isWearing(
                player, ModItems.ENCYCLOPEDIA.get(), WORN_SLOTS);
    }

    public static int equippedCount(ServerPlayer player) {
        return DynamicCurioSlotContributionService.equippedCount(
                player, ModItems.ENCYCLOPEDIA.get(), WORN_SLOTS);
    }
}
