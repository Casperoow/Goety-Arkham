package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.entity.ModEntities;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GoetyArkham.MOD_ID);

    public static final RegistryObject<ForgeSpawnEggItem> YOUNG_DEEP_ONE_SPAWN_EGG =
            ITEMS.register("young_deep_one_spawn_egg", () ->
                    new ForgeSpawnEggItem(
                            ModEntities.YOUNG_DEEP_ONE,
                            0x354B43,
                            0xF2D94E,
                            new Item.Properties()
                    ));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addCreativeTabItems);
    }

    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event
    ) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(YOUNG_DEEP_ONE_SPAWN_EGG);
        }
    }
}
