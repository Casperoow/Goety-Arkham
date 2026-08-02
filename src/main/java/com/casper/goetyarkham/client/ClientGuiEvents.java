package com.casper.goetyarkham.client;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = GoetyArkham.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class ClientGuiEvents {
    private ClientGuiEvents() {
    }

    @SubscribeEvent
    public static void moveChatAboveSanity(CustomizeGuiOverlayEvent.Chat event) {
        event.setPosY(event.getPosY() - SanityHud.chatOffsetY());
    }
}
