package com.casper.goetyarkham.client;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Detects the Medical Texts ability key and asks the server to run the
 * ability. No check, cooldown, or effect logic runs here - a press only ever
 * sends one request packet per accumulated click; every rule lives
 * server-side in {@code MedicalTextsAbilityService}.
 */
@Mod.EventBusSubscriber(
        modid = GoetyArkham.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class MedicalTextsKeyEvents {
    private MedicalTextsKeyEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
            return;
        }
        while (ModKeyMappings.MEDICAL_TEXTS_ABILITY.consumeClick()) {
            ModNetwork.sendUseMedicalTexts();
        }
    }
}
