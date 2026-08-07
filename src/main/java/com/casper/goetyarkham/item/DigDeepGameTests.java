package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.stats.EquipmentStatsService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exercises Dig Deep's use of {@link DigDeepService} / {@link
 * DigDeepBonusProvider} to guarantee 3 {@link CurioSlotIds#RESOURCE} slots,
 * its own Ectoplasm/Rabbit's Foot scoring, and the Shift-tooltip "current
 * bonus" display - mirroring {@link PhysicalTrainingGameTests}'s shape
 * almost exactly, since both items plug into the exact same generic {@code
 * resource}-pool mechanism. Also verifies coexistence with the Encyclopedia
 * without either one's contribution leaking into or overriding the other's.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DigDeepGameTests {
    private static final String DIG_DEEP_TEST_BATCH = "goetyarkham:dig_deep";

    private DigDeepGameTests() {
    }

    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void registryAndBasicShape(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "dig_deep");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.DIG_DEEP.get())),
                "Dig Deep registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.DIG_DEEP.get());
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Dig Deep must not stack");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(java.util.Set.of(CurioSlotIds.ASSET)),
                "Dig Deep item tag must expose exactly the asset slot");

        helper.succeed();
    }

    /** Scenario A: 0 existing resource slots -> equipping Dig Deep grants exactly 3. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void zeroExistingSlotsBecomeThreeWhenDigDeepIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-zero-to-three", 400.0D);
        try {
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 0,
                    "Setup: expected 0 resource slots");

            equipDigDeep(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Dig Deep did not raise 0 existing resource slots to 3");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario B: 1 existing resource slot from another provider becomes 3. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void oneExistingSlotBecomesThreeWhenDigDeepIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-one-to-three", 401.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Setup: expected 1 resource slot from the Encyclopedia alone");

            equipDigDeep(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Dig Deep did not raise 1 existing resource slot to 3"
                            + " (found " + resourceHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario C: 2 existing resource slots (synthetic) become 3. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void twoExistingSlotsBecomeThreeWhenDigDeepIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-two-to-three", 402.0D);
        com.casper.goetyarkham.curios.ResourceSlotProvider declaresTwo =
                declaringProvider("test:dig_deep_declares_two", 2);
        try {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.register(declaresTwo);
            com.casper.goetyarkham.curios.ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 2,
                    "Setup: expected 2 resource slots");

            equipDigDeep(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Dig Deep did not raise 2 existing resource slots to 3"
                            + " (found " + resourceHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.unregister(
                    declaresTwo.providerId());
            wearer.discard();
        }
    }

    /** Scenario D: 3 or more existing resource slots are never raised further. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void existingSlotsAtOrAboveThreeAreNeverRaisedFurther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-already-high", 403.0D);
        com.casper.goetyarkham.curios.ResourceSlotProvider declaresFive =
                declaringProvider("test:dig_deep_declares_five", 5);
        try {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.register(declaresFive);
            com.casper.goetyarkham.curios.ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 5,
                    "Setup: expected 5 resource slots");

            equipDigDeep(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 5,
                    "Dig Deep changed capacity away from the"
                            + " higher-declaring provider's 5 (found "
                            + resourceHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.unregister(
                    declaresFive.providerId());
            wearer.discard();
        }
    }

    /**
     * Scenario E: unequipping Dig Deep only removes its own contribution,
     * never disturbing capacity still provided by another equipped provider
     * (the Encyclopedia here).
     */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void unequippingDigDeepOnlyRemovesItsOwnContribution(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-unequip-only-own", 404.0D);
        try {
            equipEncyclopedia(wearer);
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: expected 3 resource slots with both equipped");

            unequipDigDeep(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Unequipping Dig Deep did not shrink capacity"
                            + " back down to the Encyclopedia's own declared 1"
                            + " (found " + resourceHandler.getStacks().getSlots() + ")");

            unequipEncyclopedia(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 0,
                    "Capacity did not fall to 0 once every provider was gone");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario F/G: Ectoplasm grants +1 Willpower each. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void ectoplasmGrantsPlusOneWillpowerEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-ectoplasm", 405.0D);
        try {
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "One Ectoplasm did not grant +1 Willpower");

            resourceHandler.getStacks().setStackInSlot(
                    1, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(
                    2, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 3,
                    "Three Ectoplasm did not grant +3 Willpower, found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                                    - baseWillpower));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario H/I: Rabbit's Feet grant +1 Agility each. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void rabbitFeetGrantPlusOneAgilityEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-rabbit-foot", 406.0D);
        try {
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.RABBIT_FOOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                            == baseAgility + 1,
                    "One Rabbit's Foot did not grant +1 Agility");

            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.RABBIT_FOOT));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.RABBIT_FOOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                            == baseAgility + 3,
                    "Three Rabbit's Feet did not grant +3 Agility, found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                                    - baseAgility));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario J: 2 Ectoplasm + 1 Rabbit's Foot grants +2 Willpower, +1 Agility. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void twoEctoplasmAndOneRabbitFootGivePlusTwoWillpowerPlusOneAgility(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-mixed", 407.0D);
        try {
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(
                    1, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.RABBIT_FOOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 2,
                    "Two Ectoplasm did not grant +2 Willpower");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                            == baseAgility + 1,
                    "One Rabbit's Foot did not grant +1 Agility");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario K: unrelated items (Book, Iron Ingot) contribute nothing. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void unrelatedItemsGiveNoBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-unrelated", 408.0D);
        try {
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.IRON_INGOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "The one Ectoplasm did not grant its own +1 Willpower");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.AGILITY) == baseAgility,
                    "No Rabbit's Foot present, Agility must stay unchanged");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT) == baseIntellect,
                    "Book must not grant a Dig Deep bonus");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH) == baseStrength,
                    "Iron Ingot must not grant a Dig Deep bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Scenario L: changing resource contents immediately refreshes the
     * bonus with no residual/stacked modifier from the previous contents.
     */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void changingResourceContentsRefreshesBonusWithNoResidue(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-refresh", 409.0D);
        try {
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "Ectoplasm did not grant +1 Willpower");

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.RABBIT_FOOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER) == baseWillpower,
                    "Replacing the Ectoplasm left a residual +1 Willpower");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                            == baseAgility + 1,
                    "Replacing the Ectoplasm with Rabbit's Foot did not grant"
                            + " exactly +1 Agility (no stacking with the old bonus)");

            resourceHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER) == baseWillpower
                            && PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                                    == baseAgility,
                    "Emptying the slot left a residual bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario M/N: unequip zeroes the bonus immediately; re-equip does not double it. */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void unequipZeroesBonusAndReequipDoesNotDouble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-unequip-reequip", 410.0D);
        try {
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);
            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "Setup: Ectoplasm did not grant +1 Willpower");

            unequipDigDeep(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER) == baseWillpower,
                    "Unequipping Dig Deep left a residual +1 Willpower");

            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandlerAfter = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandlerAfter.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "Re-equipping and repeated refresh duplicated the +1"
                            + " Willpower bonus, found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                                    - baseWillpower));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Scenario O: Encyclopedia and Dig Deep worn together each
     * independently score the same shared resource contents under their own
     * rule, additively - neither one overrides the other's contribution.
     */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void encyclopediaAndDigDeepScoreIndependentlyAndAdditively(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-with-encyclopedia", 411.0D);
        try {
            equipEncyclopedia(wearer);
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: capacity did not reach 3 with both providers equipped");

            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.RABBIT_FOOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            // Ectoplasm: Encyclopedia +2, Dig Deep +1 = +3 Willpower.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 3,
                    "Ectoplasm did not total +3 Willpower (Encyclopedia +2,"
                            + " Dig Deep +1), found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                                    - baseWillpower));
            // Rabbit's Foot: Encyclopedia +2, Dig Deep +1 = +3 Agility.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                            == baseAgility + 3,
                    "Rabbit's Foot did not total +3 Agility (Encyclopedia +2,"
                            + " Dig Deep +1), found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                                    - baseAgility));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Scenario P: the Shift tooltip's "current bonus" must only ever report
     * Dig Deep's own contribution - never the Encyclopedia's simultaneously
     * active (and much larger) bonus for the very same items.
     */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void shiftTooltipOnlyReportsDigDeepsOwnContribution(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-tooltip-isolation", 412.0D);
        try {
            equipEncyclopedia(wearer);
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(
                    1, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.RABBIT_FOOT));

            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.WILLPOWER) == 2,
                    "Two Ectoplasm should report Dig Deep's own +2 Willpower"
                            + " only (not the Encyclopedia's +4), found "
                            + bonus.get(StatType.WILLPOWER));
            helper.assertTrue(bonus.get(StatType.AGILITY) == 1,
                    "One Rabbit's Foot should report Dig Deep's own +1"
                            + " Agility only (not the Encyclopedia's +2), found "
                            + bonus.get(StatType.AGILITY));
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 0,
                    "Dig Deep must never report the Encyclopedia's own"
                            + " Intellect-scoring rule");
            helper.assertTrue(bonus.get(StatType.STRENGTH) == 0,
                    "Dig Deep must never grant a Strength bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void emptyAndIllegalSlotContentsGiveNoBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "dig-deep-empty-illegal", 413.0D);
        try {
            equipDigDeep(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: Dig Deep alone should grant exactly 3 resource slots");

            Map<StatType, Integer> emptyBonus = computeCurrentBonus(wearer);
            helper.assertTrue(allZero(emptyBonus), "Empty slots must contribute no bonus");

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.DIRT));
            Map<StatType, Integer> illegalBonus = computeCurrentBonus(wearer);
            helper.assertTrue(allZero(illegalBonus),
                    "An illegal/unrecognized item must contribute no bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * A real {@code appendHoverText} call, on the server side where {@code
     * ShiftTooltipHelper.isShiftDown()} and {@code
     * DigDeepService.currentClientBonus()} both safely resolve to their
     * no-client-player defaults, proving the item never crashes when no
     * local client player is available.
     */
    @GameTest(template = "empty", batch = DIG_DEEP_TEST_BATCH)
    public static void appendHoverTextNeverCrashesWithoutAClientPlayer(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.DIG_DEEP.get());
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(!tooltip.isEmpty(), "Dig Deep tooltip must not be empty");
        helper.succeed();
    }

    private static Map<StatType, Integer> computeCurrentBonus(ServerPlayer wearer) {
        List<ItemStack> contents = DynamicCurioSlotContributionService.slotContents(
                wearer, CurioSlotIds.RESOURCE);
        return DigDeepBonusProvider.INSTANCE.computeBonus(contents);
    }

    private static boolean allZero(Map<StatType, Integer> bonus) {
        return bonus.values().stream().allMatch(value -> value == 0);
    }

    private static com.casper.goetyarkham.curios.ResourceSlotProvider declaringProvider(
            String id, int slotCount) {
        return new com.casper.goetyarkham.curios.ResourceSlotProvider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public int declaredSlotCount() {
                return slotCount;
            }

            @Override
            public boolean isEquipped(ServerPlayer player) {
                return true;
            }

            @Override
            public int statBonus(StatType stat, net.minecraft.world.item.Item item) {
                return 0;
            }
        };
    }

    private static void equipDigDeep(ServerPlayer wearer) {
        ICurioStacksHandler assetHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.ASSET).orElseThrow();
        int slot = firstEmptySlot(assetHandler);
        assetHandler.getStacks().setStackInSlot(
                slot, new ItemStack(ModItems.DIG_DEEP.get()));
        settleCurioChange(wearer);
        DigDeepService.reconcile(wearer);
    }

    private static void unequipDigDeep(ServerPlayer wearer) {
        ICurioStacksHandler assetHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.ASSET).orElseThrow();
        for (int slot = 0; slot < assetHandler.getStacks().getSlots(); slot++) {
            if (assetHandler.getStacks().getStackInSlot(slot)
                    .is(ModItems.DIG_DEEP.get())) {
                assetHandler.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        settleCurioChange(wearer);
        DigDeepService.reconcile(wearer);
    }

    private static int firstEmptySlot(ICurioStacksHandler handler) {
        for (int slot = 0; slot < handler.getStacks().getSlots(); slot++) {
            if (handler.getStacks().getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        throw new IllegalStateException("No empty asset slot available for the test");
    }

    private static void equipEncyclopedia(ServerPlayer wearer) {
        ICurioStacksHandler handsHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.HANDS).orElseThrow();
        handsHandler.getStacks().setStackInSlot(0, new ItemStack(ModItems.ENCYCLOPEDIA.get()));
        settleCurioChange(wearer);
        EncyclopediaService.reconcile(wearer);
    }

    private static void unequipEncyclopedia(ServerPlayer wearer) {
        ICurioStacksHandler handsHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.HANDS).orElseThrow();
        handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
        settleCurioChange(wearer);
        EncyclopediaService.reconcile(wearer);
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 0.0D);
        return player;
    }

    private static ICurioStacksHandler handler(
            ServerPlayer player, String slot, GameTestHelper helper) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inv -> inv.getStacksHandler(slot))
                .orElse(null);
        helper.assertTrue(handler != null, "Missing Curios handler: " + slot);
        return handler;
    }

    private static void settleCurioChange(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(java.util.UUID.randomUUID(), name));
        }

        @Override
        public void sendSystemMessage(Component message) {
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
