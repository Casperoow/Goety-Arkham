package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Supplies Roland's .38 Special's conditional +2 Strength, mixed into {@link
 * PlayerStatsService#getFinalValue} exactly like {@link
 * LockpicksIntellectBonusService}: recomputed fresh on every read from live
 * equip state and Intellect's own current final value, and never written
 * into the stats capability. Reading Intellect from a Strength contribution
 * cannot cycle back into itself. {@link RolandDamageBonusService} reuses the
 * same eligibility check for the matching +1 Attack Damage attribute.
 */
public final class RolandConditionalStrengthService {
    public static final int STRENGTH_BONUS = 2;
    public static final int INTELLECT_THRESHOLD = 2;

    private RolandConditionalStrengthService() {
    }

    public static int strengthModifier(ServerPlayer player) {
        return isEligible(player) ? STRENGTH_BONUS : 0;
    }

    static boolean isEligible(ServerPlayer player) {
        return isWorn(player)
                && PlayerStatsService.getFinalValue(player, StatType.INTELLECT)
                        >= INTELLECT_THRESHOLD;
    }

    private static boolean isWorn(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.HANDS))
                .map(RolandConditionalStrengthService::hasPistol)
                .orElse(false);
    }

    private static boolean hasPistol(ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (stacks.getStackInSlot(slot).is(ModItems.ROLANDS_38_SPECIAL.get())) {
                return true;
            }
        }
        return false;
    }
}
