package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The mod-owned tab automatically displays every item registered by Goety: Arkham. */
public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(
                    Registries.CREATIVE_MODE_TAB,
                    GoetyArkham.MOD_ID
            );

    public static final RegistryObject<CreativeModeTab> GOETY_ARKHAM =
            CREATIVE_MODE_TABS.register("goety_arkham", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable(
                                    "creativetab.goetyarkham.goety_arkham"))
                            .icon(() -> new ItemStack(
                                    ModItems.DISC_OF_ITZAMNA.get()))
                            .displayItems((parameters, output) ->
                                    ModItems.ITEMS.getEntries().forEach(
                                            entry -> output.accept(entry.get())))
                            .build());

    private ModCreativeModeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
