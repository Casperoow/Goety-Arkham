package com.casper.goetyarkham.item;

import com.casper.goetyarkham.stats.PlayerStatsService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Supplies the Baseball Bat's +2 Strength bonus, mixed into {@link
 * PlayerStatsService#getFinalValue} exactly like {@link
 * MacheteStrengthBonusService}: recomputed fresh on every read from live
 * main-hand state and never written into the stats capability. Because it is
 * never stored anywhere but read live from {@link
 * ServerPlayer#getMainHandItem()}, it needs no dedicated reconcile on hand
 * swap, offhand swap, drop, break, death, dimension change, or relogin - the
 * next read simply reflects whatever is currently held.
 */
public final class BaseballBatStrengthBonusService {
    private BaseballBatStrengthBonusService() {
    }

    public static int strengthModifier(ServerPlayer player) {
        return isWielded(player) ? BaseballBatItem.STRENGTH_BONUS : 0;
    }

    private static boolean isWielded(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.BASEBALL_BAT.get());
    }
}
