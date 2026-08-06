package com.casper.goetyarkham.item;

import com.casper.goetyarkham.stats.PlayerStatsService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Supplies the Switchblade's +2 Agility bonus, mixed into {@link
 * PlayerStatsService#getFinalValue} exactly like {@link
 * LockpicksIntellectBonusService}: recomputed fresh on every read from live
 * main-hand state and never written into the stats capability. Because it is
 * never stored anywhere but read live from {@link
 * ServerPlayer#getMainHandItem()}, it needs no dedicated reconcile on hand
 * swap, offhand swap, drop, death, dimension change, or relogin - the next
 * read simply reflects whatever is currently held. {@link
 * SwitchbladeEvents} still forces a resync on main-hand change so the
 * mirrored display attribute and the client do not lag behind by a tick.
 */
public final class SwitchbladeAgilityBonusService {
    private SwitchbladeAgilityBonusService() {
    }

    public static int agilityModifier(ServerPlayer player) {
        return isWielded(player) ? SwitchbladeItem.AGILITY_BONUS : 0;
    }

    private static boolean isWielded(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.SWITCHBLADE.get());
    }
}
