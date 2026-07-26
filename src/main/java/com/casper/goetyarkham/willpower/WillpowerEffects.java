package com.casper.goetyarkham.willpower;

import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side effects derived from the authoritative willpower stat.
 */
public final class WillpowerEffects {
    private WillpowerEffects() {
    }

    public static int calculateSoulCapacityContribution(int willpower) {
        long contribution = (long) willpower * 10L;
        return (int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, contribution));
    }

    public static void refreshWillpowerEffects(ServerPlayer player) {
        SoulEnergyPoolService.refresh(player);
    }
}
