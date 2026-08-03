package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.loneliness.LonelinessService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wendy's Amulet immunizes its wearer against incoming negative status
 * effects, converting each blocked application into one stack of Loneliness.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class WendysAmuletEffectEvents {
    private WendysAmuletEffectEvents() {
    }

    @SubscribeEvent
    public static void mobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getResult() != Event.Result.DEFAULT) {
            // Another listener already decided this effect; Wendy's Amulet
            // did not itself block it, so no Loneliness is gained.
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getEffectInstance().getEffect().getCategory()
                != MobEffectCategory.HARMFUL) {
            return;
        }
        if (!WendysAmuletService.isWearing(player)) {
            return;
        }
        event.setResult(Event.Result.DENY);
        LonelinessService.addLoneliness(player);
    }
}
