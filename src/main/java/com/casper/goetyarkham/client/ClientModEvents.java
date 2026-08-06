package com.casper.goetyarkham.client;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.client.renderer.ThrownKnifeRenderer;
import com.casper.goetyarkham.client.renderer.YoungDeepOneRenderer;
import com.casper.goetyarkham.entity.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = GoetyArkham.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntities.YOUNG_DEEP_ONE.get(),
                YoungDeepOneRenderer::new
        );
        event.registerEntityRenderer(
                ModEntities.THROWN_KNIFE.get(),
                ThrownKnifeRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(
                "sanity",
                SanityHud::render);
    }
}
