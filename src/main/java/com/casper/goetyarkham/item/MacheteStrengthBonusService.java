package com.casper.goetyarkham.item;

import com.casper.goetyarkham.stats.PlayerStatsService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Supplies the Machete's +1 Strength bonus, mixed into {@link
 * PlayerStatsService#getFinalValue} exactly like {@link
 * SwitchbladeAgilityBonusService}: recomputed fresh on every read from live
 * main-hand state and never written into the stats capability. Because it is
 * never stored anywhere but read live from {@link
 * ServerPlayer#getMainHandItem()}, it needs no dedicated reconcile on hand
 * swap, offhand swap, drop, break, death, dimension change, or relogin - the
 * next read simply reflects whatever is currently held. {@link
 * SwitchbladeEvents} already forces a resync on any main-hand change (not
 * specific to the Switchblade), so the mirrored display attribute and the
 * client do not lag behind by a tick when the Machete is equipped either.
 */
public final class MacheteStrengthBonusService {
    private MacheteStrengthBonusService() {
    }

    public static int strengthModifier(ServerPlayer player) {
        return isWielded(player) ? MacheteItem.STRENGTH_BONUS : 0;
    }

    private static boolean isWielded(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.MACHETE.get());
    }
}
