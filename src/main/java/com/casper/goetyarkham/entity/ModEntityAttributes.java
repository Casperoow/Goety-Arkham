package com.casper.goetyarkham.entity;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModEntityAttributes {
    private ModEntityAttributes() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModEntityAttributes::createEntityAttributes);
    }

    private static void createEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(
                ModEntities.YOUNG_DEEP_ONE.get(),
                YoungDeepOneEntity.createAttributes().build()
        );
    }
}
