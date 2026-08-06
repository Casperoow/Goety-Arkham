package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Every {@link #INTERVAL_TICKS} server ticks while Cover Up is worn, checks
 * the wearer's final Intellect (all sources applied, including Cover Up's
 * own -6) and drains {@link #DRAIN_AMOUNT} Soul Energy through {@link
 * SoulEnergyPoolService} whenever it is negative. {@link
 * SoulEnergyPoolService#removeSoul} already floors at 0, so no extra clamp is
 * needed here.
 */
public final class CoverUpSoulDrainService {
    public static final int INTERVAL_TICKS = 20;
    public static final int DRAIN_AMOUNT = 10;

    private CoverUpSoulDrainService() {
    }

    public static void tick(ServerPlayer player) {
        if (!isWorn(player)) {
            return;
        }
        if (PlayerStatsService.getFinalValue(player, StatType.INTELLECT) >= 0) {
            return;
        }
        SoulEnergyPoolService.removeSoul(player, DRAIN_AMOUNT);
    }

    static boolean isWorn(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.WEAKNESS))
                .map(CoverUpSoulDrainService::hasCoverUp)
                .orElse(false);
    }

    private static boolean hasCoverUp(ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (stacks.getStackInSlot(slot).is(ModItems.COVER_UP.get())) {
                return true;
            }
        }
        return false;
    }
}
