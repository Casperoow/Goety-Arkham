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
 * Exercises Arcane Studies' use of {@link ArcaneStudiesService} / {@link
 * ArcaneStudiesBonusProvider} to guarantee 3 {@link CurioSlotIds#RESOURCE}
 * slots, its own Ectoplasm/Book scoring, and the Shift-tooltip "current
 * bonus" display - mirroring {@link PhysicalTrainingGameTests}'s shape
 * almost exactly, since both items plug into the exact same generic {@code
 * resource}-pool mechanism. Also verifies coexistence with the Encyclopedia
 * without either one's contribution leaking into or overriding the other's.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArcaneStudiesGameTests {
    private static final String ARCANE_STUDIES_TEST_BATCH = "goetyarkham:arcane_studies";

    private ArcaneStudiesGameTests() {
    }

    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void registryAndBasicShape(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "arcane_studies");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.ARCANE_STUDIES.get())),
                "Arcane Studies registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.ARCANE_STUDIES.get());
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Arcane Studies must not stack");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(java.util.Set.of(CurioSlotIds.ASSET)),
                "Arcane Studies item tag must expose exactly the asset slot");

        helper.succeed();
    }

    /** Scenario A: 0 existing resource slots -> equipping Arcane Studies grants exactly 3. */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void zeroExistingSlotsBecomeThreeWhenArcaneStudiesIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-zero-to-three", 500.0D);
        try {
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 0,
                    "Setup: expected 0 resource slots");

            equipArcaneStudies(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Arcane Studies did not raise 0 existing resource slots to 3");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario B: 1 existing resource slot from another provider becomes 3. */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void oneExistingSlotBecomesThreeWhenArcaneStudiesIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-one-to-three", 501.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Setup: expected 1 resource slot from the Encyclopedia alone");

            equipArcaneStudies(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Arcane Studies did not raise 1 existing resource slot to 3"
                            + " (found " + resourceHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario C: 2 existing resource slots (synthetic) become 3. */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void twoExistingSlotsBecomeThreeWhenArcaneStudiesIsEquipped(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-two-to-three", 502.0D);
        com.casper.goetyarkham.curios.ResourceSlotProvider declaresTwo =
                declaringProvider("test:arcane_studies_declares_two", 2);
        try {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.register(declaresTwo);
            com.casper.goetyarkham.curios.ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 2,
                    "Setup: expected 2 resource slots");

            equipArcaneStudies(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Arcane Studies did not raise 2 existing resource slots to 3"
                            + " (found " + resourceHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.unregister(
                    declaresTwo.providerId());
            wearer.discard();
        }
    }

    /** Scenario D: 3 or more existing resource slots are never raised further. */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void existingSlotsAtOrAboveThreeAreNeverRaisedFurther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-already-high", 503.0D);
        com.casper.goetyarkham.curios.ResourceSlotProvider declaresFive =
                declaringProvider("test:arcane_studies_declares_five", 5);
        try {
            com.casper.goetyarkham.curios.ResourceSlotProviderRegistry.register(declaresFive);
            com.casper.goetyarkham.curios.ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 5,
                    "Setup: expected 5 resource slots");

            equipArcaneStudies(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 5,
                    "Arcane Studies changed capacity away from the"
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
     * Scenario E: unequipping Arcane Studies only removes its own
     * contribution, never disturbing capacity still provided by another
     * equipped provider (the Encyclopedia here).
     */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void unequippingArcaneStudiesOnlyRemovesItsOwnContribution(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-unequip-only-own", 504.0D);
        try {
            equipEncyclopedia(wearer);
            equipArcaneStudies(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: expected 3 resource slots with both equipped");

            unequipArcaneStudies(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Unequipping Arcane Studies did not shrink capacity"
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
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void ectoplasmGrantsPlusOneWillpowerEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-ectoplasm", 505.0D);
        try {
            equipArcaneStudies(wearer);
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

    /** Scenario H/I: Books grant +1 Intellect each. */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void booksGrantPlusOneIntellectEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-book", 506.0D);
        try {
            equipArcaneStudies(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 1,
                    "One Book did not grant +1 Intellect");

            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.BOOK));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 3,
                    "Three Books did not grant +3 Intellect, found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                                    - baseIntellect));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario J: 2 Ectoplasm + 1 Book grants +2 Willpower, +1 Intellect. */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void twoEctoplasmAndOneBookGivePlusTwoWillpowerPlusOneIntellect(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-mixed", 507.0D);
        try {
            equipArcaneStudies(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(
                    1, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.BOOK));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 2,
                    "Two Ectoplasm did not grant +2 Willpower");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 1,
                    "One Book did not grant +1 Intellect");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario K: unrelated items (Rabbit's Foot, Iron Ingot) contribute nothing. */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void unrelatedItemsGiveNoBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-unrelated", 508.0D);
        try {
            equipArcaneStudies(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.RABBIT_FOOT));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.IRON_INGOT));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "The one Ectoplasm did not grant its own +1 Willpower");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.AGILITY) == baseAgility,
                    "Rabbit's Foot must not grant an Arcane Studies bonus");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH) == baseStrength,
                    "Iron Ingot must not grant an Arcane Studies bonus");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT) == baseIntellect,
                    "No Book present, Intellect must stay unchanged");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Scenario L: changing resource contents immediately refreshes the
     * bonus with no residual/stacked modifier from the previous contents.
     */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void changingResourceContentsRefreshesBonusWithNoResidue(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-refresh", 509.0D);
        try {
            equipArcaneStudies(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 1,
                    "Ectoplasm did not grant +1 Willpower");

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER) == baseWillpower,
                    "Replacing the Ectoplasm left a residual +1 Willpower");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 1,
                    "Replacing the Ectoplasm with Book did not grant exactly"
                            + " +1 Intellect (no stacking with the old bonus)");

            resourceHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER) == baseWillpower
                            && PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                                    == baseIntellect,
                    "Emptying the slot left a residual bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario M/N: unequip zeroes the bonus immediately; re-equip does not double it. */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void unequipZeroesBonusAndReequipDoesNotDouble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-unequip-reequip", 510.0D);
        try {
            equipArcaneStudies(wearer);
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

            unequipArcaneStudies(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER) == baseWillpower,
                    "Unequipping Arcane Studies left a residual +1 Willpower");

            equipArcaneStudies(wearer);
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
     * Scenario O: Encyclopedia and Arcane Studies worn together each
     * independently score the same shared resource contents under their own
     * rule, additively - neither one overrides the other's contribution.
     */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void encyclopediaAndArcaneStudiesScoreIndependentlyAndAdditively(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-with-encyclopedia", 511.0D);
        try {
            equipEncyclopedia(wearer);
            equipArcaneStudies(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: capacity did not reach 3 with both providers equipped");

            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);

            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.BOOK));
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            // Ectoplasm: Encyclopedia +2, Arcane Studies +1 = +3 Willpower.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + 3,
                    "Ectoplasm did not total +3 Willpower (Encyclopedia +2,"
                            + " Arcane Studies +1), found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                                    - baseWillpower));
            // Book: Encyclopedia +2, Arcane Studies +1 = +3 Intellect.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 3,
                    "Book did not total +3 Intellect (Encyclopedia +2,"
                            + " Arcane Studies +1), found +"
                            + (PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                                    - baseIntellect));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Scenario P: the Shift tooltip's "current bonus" must only ever report
     * Arcane Studies' own contribution - never the Encyclopedia's
     * simultaneously active (and much larger) bonus for the very same
     * items.
     */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void shiftTooltipOnlyReportsArcaneStudiesOwnContribution(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-tooltip-isolation", 512.0D);
        try {
            equipEncyclopedia(wearer);
            equipArcaneStudies(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(
                    1, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.BOOK));

            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.WILLPOWER) == 2,
                    "Two Ectoplasm should report Arcane Studies' own +2"
                            + " Willpower only (not the Encyclopedia's +4), found "
                            + bonus.get(StatType.WILLPOWER));
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 1,
                    "One Book should report Arcane Studies' own +1 Intellect"
                            + " only (not the Encyclopedia's +2), found "
                            + bonus.get(StatType.INTELLECT));
            helper.assertTrue(bonus.get(StatType.AGILITY) == 0,
                    "Arcane Studies must never report the Encyclopedia's own"
                            + " Agility-scoring rule");
            helper.assertTrue(bonus.get(StatType.STRENGTH) == 0,
                    "Arcane Studies must never grant a Strength bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void emptyAndIllegalSlotContentsGiveNoBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-studies-empty-illegal", 513.0D);
        try {
            equipArcaneStudies(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: Arcane Studies alone should grant exactly 3 resource slots");

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
     * ArcaneStudiesService.currentClientBonus()} both safely resolve to
     * their no-client-player defaults, proving the item never crashes when
     * no local client player is available.
     */
    @GameTest(template = "empty", batch = ARCANE_STUDIES_TEST_BATCH)
    public static void appendHoverTextNeverCrashesWithoutAClientPlayer(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.ARCANE_STUDIES.get());
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(!tooltip.isEmpty(), "Arcane Studies tooltip must not be empty");
        helper.succeed();
    }

    private static Map<StatType, Integer> computeCurrentBonus(ServerPlayer wearer) {
        List<ItemStack> contents = DynamicCurioSlotContributionService.slotContents(
                wearer, CurioSlotIds.RESOURCE);
        return ArcaneStudiesBonusProvider.INSTANCE.computeBonus(contents);
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

    private static void equipArcaneStudies(ServerPlayer wearer) {
        ICurioStacksHandler assetHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.ASSET).orElseThrow();
        int slot = firstEmptySlot(assetHandler);
        assetHandler.getStacks().setStackInSlot(
                slot, new ItemStack(ModItems.ARCANE_STUDIES.get()));
        settleCurioChange(wearer);
        ArcaneStudiesService.reconcile(wearer);
    }

    private static void unequipArcaneStudies(ServerPlayer wearer) {
        ICurioStacksHandler assetHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.ASSET).orElseThrow();
        for (int slot = 0; slot < assetHandler.getStacks().getSlots(); slot++) {
            if (assetHandler.getStacks().getStackInSlot(slot)
                    .is(ModItems.ARCANE_STUDIES.get())) {
                assetHandler.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        settleCurioChange(wearer);
        ArcaneStudiesService.reconcile(wearer);
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
