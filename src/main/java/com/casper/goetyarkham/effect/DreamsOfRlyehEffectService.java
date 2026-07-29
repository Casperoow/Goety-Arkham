package com.casper.goetyarkham.effect;

import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;

public final class DreamsOfRlyehEffectService {
    public static final int DURATION_TICKS = 24_000;
    public static final int WILLPOWER_MODIFIER = -1;
    public static final int SOUL_CAPACITY_MODIFIER = -1_000;

    private DreamsOfRlyehEffectService() {
    }

    public static void applyOrRefresh(ServerPlayer player) {
        if (player.hasEffect(ModEffects.DREAMS_OF_RLYEH.get())) {
            player.removeEffect(ModEffects.DREAMS_OF_RLYEH.get());
        }
        MobEffectInstance refreshed = new MobEffectInstance(
                ModEffects.DREAMS_OF_RLYEH.get(),
                DURATION_TICKS,
                0,
                false,
                true,
                true);
        refreshed.setCurativeItems(List.of());
        player.addEffect(refreshed);
        PlayerStatsService.sync(player);
        SoulEnergyPoolService.refresh(player);
    }

    public static boolean removeExplicitly(ServerPlayer player) {
        boolean removed = player.removeEffect(ModEffects.DREAMS_OF_RLYEH.get());
        if (removed) {
            PlayerStatsService.sync(player);
            SoulEnergyPoolService.refresh(player);
        }
        return removed;
    }

    public static boolean isActive(ServerPlayer player) {
        return ModEffects.DREAMS_OF_RLYEH.isPresent()
                && player.hasEffect(ModEffects.DREAMS_OF_RLYEH.get());
    }

    public static int willpowerModifier(ServerPlayer player) {
        return isActive(player) ? WILLPOWER_MODIFIER : 0;
    }

    public static int soulCapacityModifier(ServerPlayer player) {
        return isActive(player) ? SOUL_CAPACITY_MODIFIER : 0;
    }
}
