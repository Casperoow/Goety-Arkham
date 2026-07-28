package com.casper.goetyarkham.illager_treachery.encounter.type;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterTypeRegistry;
import net.minecraft.resources.ResourceLocation;

public final class BuiltInEncounterTypes {
    public static final ResourceLocation MESSAGE =
            id("message");
    public static final ResourceLocation MOB_EFFECT =
            id("mob_effect");
    public static final ResourceLocation EXTRA_DRAW =
            id("extra_draw");

    private BuiltInEncounterTypes() {
    }

    public static void register() {
        register(EncounterTypeRegistry.INSTANCE);
    }

    public static void register(EncounterTypeRegistry registry) {
        registry.register(MESSAGE, new MessageEncounterType());
        registry.register(MOB_EFFECT, new MobEffectEncounterType());
        registry.register(EXTRA_DRAW, new ExtraDrawEncounterType());
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(GoetyArkham.MOD_ID, path);
    }
}
