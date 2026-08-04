package com.casper.goetyarkham.client;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.item.MedicalTextsItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client-only key bindings. Never referenced from common code. */
@Mod.EventBusSubscriber(
        modid = GoetyArkham.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ModKeyMappings {
    private static final String CATEGORY = "key.categories.goetyarkham";

    public static final KeyMapping MEDICAL_TEXTS_ABILITY = new KeyMapping(
            MedicalTextsItem.ABILITY_KEY_TRANSLATION_KEY,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R),
            CATEGORY);

    private ModKeyMappings() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MEDICAL_TEXTS_ABILITY);
    }
}
