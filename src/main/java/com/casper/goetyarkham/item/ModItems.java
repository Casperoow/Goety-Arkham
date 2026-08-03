package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.entity.ModEntities;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

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

    public static final RegistryObject<GrotesqueStatueItem> GROTESQUE_STATUE =
            ITEMS.register("grotesque_statue", GrotesqueStatueItem::new);

    public static final RegistryObject<DiscOfItzamnaItem> DISC_OF_ITZAMNA =
            ITEMS.register("disc_of_itzamna", DiscOfItzamnaItem::new);

    public static final RegistryObject<HolyRosaryItem> HOLY_ROSARY =
            ITEMS.register("holy_rosary", HolyRosaryItem::new);

    public static final RegistryObject<HeirloomOfHyperboreaItem>
            HEIRLOOM_OF_HYPERBOREA = ITEMS.register(
                    "heirloom_of_hyperborea",
                    HeirloomOfHyperboreaItem::new);

    public static final RegistryObject<DarkMemoryItem> DARK_MEMORY =
            ITEMS.register("dark_memory", DarkMemoryItem::new);

    public static final RegistryObject<RabbitFootItem> RABBIT_FOOT =
            ITEMS.register("rabbit_foot", RabbitFootItem::new);

    public static final RegistryObject<WendysAmuletItem> WENDYS_AMULET =
            ITEMS.register("wendys_amulet", WendysAmuletItem::new);

    public static final RegistryObject<AbandonedAndAloneItem> ABANDONED_AND_ALONE =
            ITEMS.register("abandoned_and_alone", AbandonedAndAloneItem::new);

    public static final RegistryObject<PoliceBadgeItem> POLICE_BADGE =
            ITEMS.register("police_badge", PoliceBadgeItem::new);

    public static final RegistryObject<ElderSignAmuletItem> ELDER_SIGN_AMULET =
            ITEMS.register("elder_sign_amulet", ElderSignAmuletItem::new);

    public static final RegistryObject<LeatherCoatItem> LEATHER_COAT =
            ITEMS.register("leather_coat", LeatherCoatItem::new);

    public static final RegistryObject<BulletproofVestItem> BULLETPROOF_VEST =
            ITEMS.register("bulletproof_vest", BulletproofVestItem::new);

    public static final RegistryObject<AquinnahsTokenItem> AQUINNAHS_TOKEN =
            ITEMS.register("aquinnahs_token", AquinnahsTokenItem::new);

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::commonSetup);
        modEventBus.addListener(ModItems::addCreativeTabItems);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ITEMS.getEntries().forEach(entry -> {
            Item item = entry.get();
            if (item instanceof ICurioItem curioItem) {
                CuriosApi.registerCurio(item, curioItem);
            }
        }));
    }

    private static void addCreativeTabItems(
            BuildCreativeModeTabContentsEvent event
    ) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(YOUNG_DEEP_ONE_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(GROTESQUE_STATUE);
        }
    }
}
