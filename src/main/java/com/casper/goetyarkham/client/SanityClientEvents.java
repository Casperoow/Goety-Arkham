package com.casper.goetyarkham.client;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = GoetyArkham.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SanityClientEvents {
    private SanityClientEvents() {
    }

    @SubscribeEvent
    public static void playerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSanity.clear();
    }
}
