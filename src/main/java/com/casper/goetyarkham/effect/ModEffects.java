package com.casper.goetyarkham.effect;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, GoetyArkham.MOD_ID);

    public static final RegistryObject<MobEffect> DREAMS_OF_RLYEH =
            EFFECTS.register("dreams_of_rlyeh", DreamsOfRlyehEffect::new);

    public static final RegistryObject<MobEffect> RITA_CHANDLERS_BLESSING =
            EFFECTS.register(
                    "rita_chandlers_blessing", RitaChandlersBlessingEffect::new);

    public static final RegistryObject<MobEffect> TRUE_INVISIBILITY =
            EFFECTS.register("true_invisibility", TrueInvisibilityEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
