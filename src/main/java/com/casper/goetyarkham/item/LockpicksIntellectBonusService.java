package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Supplies Lockpicks' Agility-into-Intellect bonus, mixed into {@link
 * PlayerStatsService#getFinalValue} exactly like {@code
 * RitaChandlersAuraEffectService#strengthModifier}/{@code
 * DreamsOfRlyehEffectService#willpowerModifier}: recomputed fresh on every
 * read from live equip state and Agility's own current final value, and
 * never written into the stats capability. This is what keeps the bonus in
 * sync with every kind of Agility change (base, equipment, or otherwise, and
 * with no dedicated login/respawn/dimension-change reconcile needed - there
 * is no snapshot to go stale), and what keeps it from being applied twice:
 * {@link com.casper.goetyarkham.curios.CurioEquipRules} already forbids
 * wearing two copies of the same mod Curio at once, and this only ever reads
 * Agility - never Intellect - so it can never feed into its own computation.
 */
public final class LockpicksIntellectBonusService {
    private LockpicksIntellectBonusService() {
    }

    public static int intellectModifier(ServerPlayer player) {
        return isEquipped(player)
                ? PlayerStatsService.getFinalValue(player, StatType.AGILITY)
                : 0;
    }

    private static boolean isEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.HANDS))
                .map(LockpicksIntellectBonusService::hasLockpicks)
                .orElse(false);
    }

    private static boolean hasLockpicks(ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (stacks.getStackInSlot(slot).is(ModItems.LOCKPICKS.get())) {
                return true;
            }
        }
        return false;
    }
}
