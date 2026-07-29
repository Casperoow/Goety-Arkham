package com.casper.goetyarkham.effect;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.stats.PlayerStatsService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class DreamsOfRlyehEffectEvents {
    private DreamsOfRlyehEffectEvents() {
    }

    @SubscribeEvent
    public static void effectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffectInstance().getEffect()
                == ModEffects.DREAMS_OF_RLYEH.get()) {
            PlayerStatsService.sync(player);
        }
    }

    @SubscribeEvent
    public static void effectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect()
                == ModEffects.DREAMS_OF_RLYEH.get()) {
            refreshAfterRemoval(event);
        }
    }

    @SubscribeEvent
    public static void effectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() == ModEffects.DREAMS_OF_RLYEH.get()) {
            refreshAfterRemoval(event);
        }
    }

    private static void refreshAfterRemoval(MobEffectEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getServer().execute(() -> PlayerStatsService.sync(player));
        }
    }
}
