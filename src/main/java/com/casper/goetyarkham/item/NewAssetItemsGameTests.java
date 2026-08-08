package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.SanityService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Functional coverage for Emergency Cache, I've Had Worse, and Hot Streak. */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NewAssetItemsGameTests {
    // Keep these dynamic-slot tests in the established early-running batch;
    // the repository's historical defaultBatch can crash before later
    // batches execute because some legacy fake players have no connection.
    private static final String BATCH = "goetyarkham:resource_slot";

    private NewAssetItemsGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void registrationTagsAndTooltips(GameTestHelper helper) {
        verifyRegistration(helper, "emergency_cache", ModItems.EMERGENCY_CACHE.get());
        verifyRegistration(helper, "ive_had_worse", ModItems.IVE_HAD_WORSE.get());
        verifyRegistration(helper, "hot_streak", ModItems.HOT_STREAK.get());

        verifyAssetTag(helper, ModItems.EMERGENCY_CACHE.get());
        verifyAssetTag(helper, ModItems.IVE_HAD_WORSE.get());
        verifyAssetTag(helper, ModItems.HOT_STREAK.get());

        helper.assertTrue(tooltip(ModItems.EMERGENCY_CACHE.get(), helper).equals(
                        List.of("When worn:", "+3 Asset Slots")),
                "Emergency Cache tooltip mismatch");
        helper.assertTrue(tooltip(ModItems.IVE_HAD_WORSE.get(), helper).equals(
                        List.of("When worn:", "+5 Max Sanity", "+1 Agility", "+2 Will")),
                "I've Had Worse tooltip mismatch");
        helper.assertTrue(tooltip(ModItems.HOT_STREAK.get(), helper).equals(
                        List.of("When worn:", "+10 Asset Slots", "+1 Luck", "+1 Will")),
                "Hot Streak tooltip mismatch");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void emergencyCacheEquipUnequipAndRepeat(GameTestHelper helper) {
        TestPlayer wearer = testPlayer(helper.getLevel(), "emergency-cache", 10.0D);
        try {
            ICurioStacksHandler asset = handler(wearer, helper);
            int baseSlots = asset.getStacks().getSlots();
            helper.assertTrue(baseSlots == CurioSlotIds.BASE_SIZES.get(CurioSlotIds.ASSET),
                    "Unequipped asset capacity differs from its configured base");

            for (int cycle = 0; cycle < 3; cycle++) {
                asset.getStacks().setStackInSlot(
                        0, new ItemStack(ModItems.EMERGENCY_CACHE.get()));
                settle(wearer);
                helper.assertTrue(asset.getStacks().getSlots()
                                == baseSlots + EmergencyCacheItem.ASSET_SLOT_BONUS,
                        "Emergency Cache did not grant exactly three asset slots");
                AssetSlotBonusService.reconcile(wearer);
                helper.assertTrue(asset.getStacks().getSlots()
                                == baseSlots + EmergencyCacheItem.ASSET_SLOT_BONUS,
                        "Repeated reconcile duplicated Emergency Cache slots");

                asset.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                settle(wearer);
                helper.assertTrue(asset.getStacks().getSlots() == baseSlots,
                        "Emergency Cache left a slot modifier after unequip");
            }
            helper.succeed();
        } finally {
            removeTestPlayer(helper.getLevel(), wearer);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void iveHadWorseModifiersFollowEquipment(GameTestHelper helper) {
        TestPlayer wearer = testPlayer(helper.getLevel(), "ive-had-worse", 20.0D);
        try {
            ICurioStacksHandler asset = handler(wearer, helper);
            double baseMaximum = SanityService.getMaximumAttributeValue(wearer);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);
            int storedBaseAgility = storedBase(wearer, StatType.AGILITY);
            int storedBaseWillpower = storedBase(wearer, StatType.WILLPOWER);

            assertIveHadWorseValues(
                    helper, wearer, baseMaximum, baseAgility, baseWillpower,
                    "before equip");

            for (int cycle = 0; cycle < 2; cycle++) {
                asset.getStacks().setStackInSlot(
                        0, new ItemStack(ModItems.IVE_HAD_WORSE.get()));
                settle(wearer);
                helper.assertTrue(SanityService.getMaximumAttributeValue(wearer)
                                == baseMaximum + IveHadWorseItem.MAX_SANITY_BONUS,
                        "I've Had Worse did not add exactly five maximum sanity");
                helper.assertTrue(PlayerStatsService.getFinalValue(
                                wearer, StatType.AGILITY)
                                == baseAgility + IveHadWorseItem.AGILITY_BONUS,
                        "I've Had Worse did not add exactly one Agility");
                helper.assertTrue(PlayerStatsService.getFinalValue(
                                wearer, StatType.WILLPOWER)
                                == baseWillpower + IveHadWorseItem.WILLPOWER_BONUS,
                        "I've Had Worse did not add exactly two Willpower");

                asset.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                settle(wearer);
                assertIveHadWorseValues(
                        helper, wearer, baseMaximum, baseAgility, baseWillpower,
                        "after unequip");
            }

            helper.assertTrue(storedBase(wearer, StatType.AGILITY) == storedBaseAgility,
                    "I've Had Worse permanently changed base Agility");
            helper.assertTrue(storedBase(wearer, StatType.WILLPOWER) == storedBaseWillpower,
                    "I've Had Worse permanently changed base Willpower");
            helper.succeed();
        } finally {
            removeTestPlayer(helper.getLevel(), wearer);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void hotStreakAndEmergencyCacheCombineIndependently(
            GameTestHelper helper) {
        TestPlayer wearer = testPlayer(helper.getLevel(), "hot-streak", 30.0D);
        try {
            ICurioStacksHandler asset = handler(wearer, helper);
            int baseSlots = asset.getStacks().getSlots();
            double baseLuck = wearer.getAttribute(Attributes.LUCK).getValue();
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            asset.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.EMERGENCY_CACHE.get()));
            settle(wearer);
            asset.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.HOT_STREAK.get()));
            settle(wearer);
            helper.assertTrue(asset.getStacks().getSlots()
                            == baseSlots
                            + EmergencyCacheItem.ASSET_SLOT_BONUS
                            + HotStreakItem.ASSET_SLOT_BONUS,
                    "Emergency Cache and Hot Streak slot modifiers overwrote each other");
            helper.assertTrue(wearer.getAttribute(Attributes.LUCK).getValue()
                            == baseLuck + HotStreakItem.LUCK_BONUS,
                    "Hot Streak did not add exactly one vanilla Luck");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER)
                            == baseWillpower + HotStreakItem.WILLPOWER_BONUS,
                    "Hot Streak did not add exactly one Willpower");

            AssetSlotBonusService.reconcileRestore(wearer);
            AssetSlotBonusService.reconcileRestore(wearer);
            helper.assertTrue(asset.getStacks().getSlots() == baseSlots + 13,
                    "Restore reconciliation duplicated combined asset capacity");

            asset.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settle(wearer);
            helper.assertTrue(asset.getStacks().getSlots()
                            == baseSlots + HotStreakItem.ASSET_SLOT_BONUS,
                    "Removing Emergency Cache disturbed Hot Streak capacity");

            asset.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settle(wearer);
            helper.assertTrue(asset.getStacks().getSlots() == baseSlots,
                    "Removing Hot Streak did not restore base asset capacity");
            helper.assertTrue(wearer.getAttribute(Attributes.LUCK).getValue() == baseLuck,
                    "Hot Streak left a Luck modifier after unequip");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWillpower,
                    "Hot Streak left a Willpower bonus after unequip");
            helper.succeed();
        } finally {
            removeTestPlayer(helper.getLevel(), wearer);
        }
    }

    private static void assertIveHadWorseValues(
            GameTestHelper helper,
            ServerPlayer player,
            double maximum,
            int agility,
            int willpower,
            String phase) {
        helper.assertTrue(SanityService.getMaximumAttributeValue(player) == maximum,
                "I've Had Worse maximum sanity mismatch " + phase);
        helper.assertTrue(PlayerStatsService.getFinalValue(player, StatType.AGILITY)
                        == agility,
                "I've Had Worse Agility mismatch " + phase);
        helper.assertTrue(PlayerStatsService.getFinalValue(player, StatType.WILLPOWER)
                        == willpower,
                "I've Had Worse Willpower mismatch " + phase);
    }

    private static int storedBase(ServerPlayer player, StatType stat) {
        return PlayerStatsService.get(player)
                .map(stats -> stats.get(stat).base())
                .orElse(0);
    }

    private static void verifyRegistration(
            GameTestHelper helper, String path, Item item) {
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, path);
        helper.assertTrue(expected.equals(ForgeRegistries.ITEMS.getKey(item)),
                path + " registry ID mismatch");
    }

    private static void verifyAssetTag(GameTestHelper helper, Item item) {
        Set<String> slots = CuriosApi.getItemStackSlots(
                new ItemStack(item), helper.getLevel()).keySet();
        helper.assertTrue(slots.equals(Set.of(CurioSlotIds.ASSET)),
                item + " must expose only the asset Curios slot");
    }

    private static List<String> tooltip(Item item, GameTestHelper helper) {
        List<Component> components = new ArrayList<>();
        item.appendHoverText(
                new ItemStack(item), helper.getLevel(), components, TooltipFlag.NORMAL);
        return components.stream().map(Component::getString).toList();
    }

    private static TestPlayer testPlayer(
            ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 0.0D);
        level.players().add(player);
        return player;
    }

    private static void removeTestPlayer(ServerLevel level, TestPlayer player) {
        level.players().remove(player);
        player.discard();
    }

    private static ICurioStacksHandler handler(
            ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.ASSET))
                .orElse(null);
        helper.assertTrue(handler != null, "Missing asset Curios handler");
        return handler;
    }

    private static void settle(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public void sendSystemMessage(Component message) {
            // GameTest players intentionally have no network connection.
        }

        @Override
        protected void onEffectAdded(
                net.minecraft.world.effect.MobEffectInstance effectInstance,
                net.minecraft.world.entity.Entity source) {
        }

        @Override
        protected void onEffectUpdated(
                net.minecraft.world.effect.MobEffectInstance effectInstance,
                boolean forced,
                net.minecraft.world.entity.Entity source) {
        }

        @Override
        protected void onEffectRemoved(
                net.minecraft.world.effect.MobEffectInstance effectInstance) {
        }
    }
}
