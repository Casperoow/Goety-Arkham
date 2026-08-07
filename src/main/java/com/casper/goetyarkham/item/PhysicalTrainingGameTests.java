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
 * Exercises Physical Training's use of {@link PhysicalTrainingService} /
 * {@link PhysicalTrainingBonusProvider} to guarantee 3 {@link
 * CurioSlotIds#RESOURCE} slots, its own Iron Ingot/Ectoplasm scoring, and
 * the Shift-tooltip "current bonus" display - mirroring {@link
 * EncyclopediaGameTests}'s shape almost exactly, since both items plug into
 * the exact same generic {@code resource}-pool mechanism (see {@code
 * ResourceSlotGameTests} for the shared mechanism's own dedicated
 * coverage). Also verifies the two coexist without either one's
 * contribution leaking into or overriding the other's.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhysicalTrainingGameTests {
    /**
     * Isolates this suite from {@code defaultBatch}'s concurrently running
     * tests, matching the established fix for the pre-existing {@code
     * defaultBatch} crash landmine (see {@code ResourceSlotGameTests}).
     */
    private static final String PHYSICAL_TRAINING_TEST_BATCH = "goetyarkham:physical_training";

    private PhysicalTrainingGameTests() {
    }

    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void registryAndBasicShape(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "physical_training");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.PHYSICAL_TRAINING.get())),
                "Physical Training registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.PHYSICAL_TRAINING.get());
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Physical Training must not stack");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(java.util.Set.of(CurioSlotIds.ASSET)),
                "Physical Training item tag must expose exactly the asset slot");

        helper.succeed();
    }

    /** Scenario A: 0 existing resource slots -> equipping Physical Training grants exactly 3. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void zeroExistingSlotsBecomeThreeWhenPhysicalTrainingIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-zero-to-three", 200.0D);
        try {
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 0,
                    "Setup: expected 0 resource slots");

            equipPhysicalTraining(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Physical Training did not raise 0 existing resource slots to 3");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario B: 1 existing resource slot from another provider becomes 3. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void oneExistingSlotBecomesThreeWhenPhysicalTrainingIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-one-to-three", 201.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Setup: expected 1 resource slot from the Encyclopedia alone");

            equipPhysicalTraining(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Physical Training did not raise 1 existing resource slot to 3"
                            + " (found " + resourceHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario C: 2 existing resource slots (synthetic) become 3. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void twoExistingSlotsBecomeThreeWhenPhysicalTrainingIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-two-to-three", 202.0D);
        com.casper.goetyarkham.curios.ResourceSlotProvider declaresTwo =
                declaringProvider("test:physical_training_declares_two", 2);
        try {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.register(declaresTwo);
            com.casper.goetyarkham.curios.ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 2,
                    "Setup: expected 2 resource slots");

            equipPhysicalTraining(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Physical Training did not raise 2 existing resource slots to 3"
                            + " (found " + resourceHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.unregister(
                    declaresTwo.providerId());
            wearer.discard();
        }
    }

    /** Scenario D: 3 or more existing resource slots are never raised further. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void existingSlotsAtOrAboveThreeAreNeverRaisedFurther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-already-high", 203.0D);
        com.casper.goetyarkham.curios.ResourceSlotProvider declaresFive =
                declaringProvider("test:physical_training_declares_five", 5);
        try {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.register(declaresFive);
            com.casper.goetyarkham.curios.ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 5,
                    "Setup: expected 5 resource slots");

            equipPhysicalTraining(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 5,
                    "Physical Training changed capacity away from the"
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
     * Scenario E: unequipping Physical Training only removes its own
     * contribution, never disturbing capacity still provided by another
     * equipped provider (the Encyclopedia here).
     */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void unequippingPhysicalTrainingOnlyRemovesItsOwnContribution(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-unequip-only-own", 204.0D);
        try {
            equipEncyclopedia(wearer);
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: expected 3 resource slots with both equipped");

            unequipPhysicalTraining(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Unequipping Physical Training did not shrink capacity"
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

    /** Scenario F/G: Iron Ingots grant +1 Strength each. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void ironIngotsGrantPlusOneStrengthEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-iron", 205.0D);
        try {
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 1,
                    "One Iron Ingot did not grant +1 Strength");

            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.IRON_INGOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 3,
                    "Three Iron Ingots did not grant +3 Strength, found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                                    - baseStrength));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario H/I: Ectoplasm grants +1 Willpower each. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void ectoplasmGrantsPlusOneWillpowerEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-ectoplasm", 206.0D);
        try {
            equipPhysicalTraining(wearer);
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

    /** Scenario J: 2 Iron Ingots + 1 Ectoplasm grants +2 Strength, +1 Willpower. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void twoIronIngotsAndOneEctoplasmGivePlusTwoStrengthPlusOneWillpower(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-mixed", 207.0D);
        try {
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
            resourceHandler.getStacks().setStackInSlot(
                    2, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 2,
                    "Two Iron Ingots did not grant +2 Strength");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "One Ectoplasm did not grant +1 Willpower");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario K: unrelated items (Book, Rabbit's Foot) contribute nothing. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void unrelatedItemsGiveNoBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-unrelated", 208.0D);
        try {
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.RABBIT_FOOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 1,
                    "The one Iron Ingot did not grant its own +1 Strength");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.AGILITY) == baseAgility,
                    "Rabbit's Foot must not grant a Physical Training bonus");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT) == baseIntellect,
                    "Book must not grant a Physical Training bonus");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER) == baseWillpower,
                    "No Ectoplasm present, Willpower must stay unchanged");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Scenario L: changing resource contents immediately refreshes the
     * bonus with no residual/stacked modifier from the previous contents.
     */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void changingResourceContentsRefreshesBonusWithNoResidue(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-refresh", 209.0D);
        try {
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 1,
                    "Iron Ingot did not grant +1 Strength");

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH) == baseStrength,
                    "Replacing the Iron Ingot left a residual +1 Strength");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "Replacing the Iron Ingot with Ectoplasm did not grant"
                            + " exactly +1 Willpower (no stacking with the old bonus)");

            resourceHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH) == baseStrength
                            && PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                                    == baseWillpower,
                    "Emptying the slot left a residual bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario M/N: unequip zeroes the bonus immediately; re-equip does not double it. */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void unequipZeroesBonusAndReequipDoesNotDouble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-unequip-reequip", 210.0D);
        try {
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 1,
                    "Setup: Iron Ingot did not grant +1 Strength");

            unequipPhysicalTraining(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH) == baseStrength,
                    "Unequipping Physical Training left a residual +1 Strength");

            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandlerAfter = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandlerAfter.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 1,
                    "Re-equipping and repeated refresh duplicated the +1"
                            + " Strength bonus, found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                                    - baseStrength));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Scenario O: Encyclopedia and Physical Training worn together each
     * independently score the same shared resource contents under their own
     * rule, additively - neither one overrides the other's contribution.
     */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void encyclopediaAndPhysicalTrainingScoreIndependentlyAndAdditively(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-with-encyclopedia", 211.0D);
        try {
            equipEncyclopedia(wearer);
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: capacity did not reach 3 with both providers equipped");

            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(
                    2, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            // Iron Ingot: Encyclopedia +2, Physical Training +1 = +3 Strength.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 3,
                    "Iron Ingot did not total +3 Strength (Encyclopedia +2,"
                            + " Physical Training +1), found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                                    - baseStrength));
            // Book: Encyclopedia +2 Intellect only, Physical Training gives nothing for Book.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 2,
                    "Book did not total +2 Intellect from the Encyclopedia alone");
            // Ectoplasm: Encyclopedia +2, Physical Training +1 = +3 Willpower.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 3,
                    "Ectoplasm did not total +3 Willpower (Encyclopedia +2,"
                            + " Physical Training +1), found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                                    - baseWillpower));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Scenario P: the Shift tooltip's "current bonus" must only ever report
     * Physical Training's own contribution - never the Encyclopedia's
     * simultaneously active (and much larger) bonus for the very same
     * items.
     */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void shiftTooltipOnlyReportsPhysicalTrainingsOwnContribution(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-tooltip-isolation", 212.0D);
        try {
            equipEncyclopedia(wearer);
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
            resourceHandler.getStacks().setStackInSlot(
                    2, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));

            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.STRENGTH) == 2,
                    "Two Iron Ingots should report Physical Training's own +2"
                            + " Strength only (not the Encyclopedia's +4), found "
                            + bonus.get(StatType.STRENGTH));
            helper.assertTrue(bonus.get(StatType.WILLPOWER) == 1,
                    "One Ectoplasm should report Physical Training's own +1"
                            + " Willpower only (not the Encyclopedia's +2), found "
                            + bonus.get(StatType.WILLPOWER));
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 0,
                    "Physical Training must never report the Encyclopedia's"
                            + " own Intellect-scoring rule");
            helper.assertTrue(bonus.get(StatType.AGILITY) == 0,
                    "Physical Training must never grant an Agility bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void emptyAndIllegalSlotContentsGiveNoBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "physical-training-empty-illegal", 213.0D);
        try {
            equipPhysicalTraining(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: Physical Training alone should grant exactly 3 resource slots");

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
     * PhysicalTrainingService.currentClientBonus()} both safely resolve to
     * their no-client-player defaults, proving the item never crashes when
     * no local client player is available.
     */
    @GameTest(template = "empty", batch = PHYSICAL_TRAINING_TEST_BATCH)
    public static void appendHoverTextNeverCrashesWithoutAClientPlayer(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.PHYSICAL_TRAINING.get());
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(!tooltip.isEmpty(), "Physical Training tooltip must not be empty");
        helper.succeed();
    }

    private static Map<StatType, Integer> computeCurrentBonus(ServerPlayer wearer) {
        List<ItemStack> contents = DynamicCurioSlotContributionService.slotContents(
                wearer, CurioSlotIds.RESOURCE);
        return PhysicalTrainingBonusProvider.INSTANCE.computeBonus(contents);
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

    private static void equipPhysicalTraining(ServerPlayer wearer) {
        ICurioStacksHandler assetHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.ASSET).orElseThrow();
        int slot = firstEmptySlot(assetHandler);
        assetHandler.getStacks().setStackInSlot(
                slot, new ItemStack(ModItems.PHYSICAL_TRAINING.get()));
        settleCurioChange(wearer);
        PhysicalTrainingService.reconcile(wearer);
    }

    private static void unequipPhysicalTraining(ServerPlayer wearer) {
        ICurioStacksHandler assetHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.ASSET).orElseThrow();
        for (int slot = 0; slot < assetHandler.getStacks().getSlots(); slot++) {
            if (assetHandler.getStacks().getStackInSlot(slot)
                    .is(ModItems.PHYSICAL_TRAINING.get())) {
                assetHandler.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        settleCurioChange(wearer);
        PhysicalTrainingService.reconcile(wearer);
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
