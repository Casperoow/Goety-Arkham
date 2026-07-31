package com.casper.goetyarkham.entity;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GoetyArkham.MOD_ID);

    public static final RegistryObject<EntityType<YoungDeepOneEntity>> YOUNG_DEEP_ONE =
            ENTITY_TYPES.register("young_deep_one", () ->
                    EntityType.Builder.of(YoungDeepOneEntity::new, MobCategory.CREATURE)
                            .sized(1.0F, 1.5F)
                            .clientTrackingRange(8)
                            .build(GoetyArkham.MOD_ID + ":young_deep_one"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
