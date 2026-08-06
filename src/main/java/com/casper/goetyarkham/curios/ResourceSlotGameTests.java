package com.casper.goetyarkham.curios;

import com.Polarice3.Goety.common.items.ModItems;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.item.EncyclopediaBonusProvider;
import com.casper.goetyarkham.item.EncyclopediaService;
import com.casper.goetyarkham.stats.EquipmentStatsService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.UUID;

/**
 * Exercises the generic {@link CurioSlotIds#RESOURCE} mechanism: {@link
 * ResourceSlotService}'s maximum-not-sum, deduplicated-by-provider-ID
 * capacity computation, {@link ResourceSlotContentPolicy}'s four-item
 * content restriction, safe shrink/refund via {@link
 * DynamicCurioSlotContributionService#reconcileSize}, and the Encyclopedia's
 * own {@link com.casper.goetyarkham.item.EncyclopediaBonusProvider} stat
 * scoring, including how it stacks with other simultaneously active
 * providers. The multi-provider math is exercised with throwaway {@link
 * TestProvider} stand-ins - matching how a future resource-slot-granting
 * item would plug in - rather than registering any unfinished real item.
 *
 * <p>Per the project's GameTest {@code TestPlayer} gotchas, these stand-ins
 * are deliberately never added to {@code level.players()}.</p>
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResourceSlotGameTests {
    /**
     * Isolates these tests from {@code defaultBatch}'s ~90 concurrently
     * running tests. Several of these tests spawn a real dropped {@code
     * ItemEntity}, which {@code ChunkMap} broadcasts to every entry in
     * {@code level.players()} - including the connectionless {@code
     * TestPlayer} stand-ins several *other*, pre-existing test suites leave
     * registered there, which NPEs on broadcast and can crash the whole
     * batch (see the project's GameTest {@code TestPlayer} gotchas). A
     * dedicated batch, exactly like the existing chaos-bag test suites' own
     * {@code CHAOS_BAG_TEST_BATCH}, is the established fix.
     */
    private static final String RESOURCE_TEST_BATCH = "goetyarkham:resource_slot";

    private ResourceSlotGameTests() {
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void slotStartsHiddenAtZeroCapacity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-zero", 0.0D);
        try {
            ICurioStacksHandler handler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(handler.getStacks().getSlots() == 0,
                    "resource did not start at base size 0");
            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario 1: 0 existing resource slots -> equipping the Encyclopedia grants exactly 1. */
    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void zeroExistingSlotsBecomeOneWhenEncyclopediaIsEquipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-zero-to-one", 1.0D);
        try {
            ICurioStacksHandler handler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(handler.getStacks().getSlots() == 0, "Setup: expected 0 slots");
            equipEncyclopedia(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 1,
                    "Encyclopedia did not raise 0 existing resource slots to 1");
            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario 2: 1 existing resource slot (from another provider) stays 1 with the Encyclopedia equipped. */
    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void oneExistingSlotStaysOneWhenEncyclopediaIsEquipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-one-stays-one", 2.0D);
        TestProvider declaresOne = new TestProvider("test:declares_one", 1, null, null, 0);
        try {
            ResourceSlotProviderRegistry.register(declaresOne);
            declaresOne.equipped = true;
            ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler handler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(handler.getStacks().getSlots() == 1, "Setup: expected 1 slot");

            equipEncyclopedia(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 1,
                    "Encyclopedia turned 1 existing resource slot into more than 1"
                            + " (found " + handler.getStacks().getSlots() + ")");
            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(declaresOne.providerId());
            wearer.discard();
        }
    }

    /** Scenario 3: 3 existing resource slots (from another provider) stay 3 with the Encyclopedia equipped. */
    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void threeExistingSlotsStayThreeWhenEncyclopediaIsEquipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-three-stays-three", 3.0D);
        TestProvider declaresThree = new TestProvider("test:declares_three", 3, null, null, 0);
        try {
            ResourceSlotProviderRegistry.register(declaresThree);
            declaresThree.equipped = true;
            ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler handler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(handler.getStacks().getSlots() == 3, "Setup: expected 3 slots");

            equipEncyclopedia(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 3,
                    "Encyclopedia changed capacity away from the higher-declaring"
                            + " provider's 3 (found " + handler.getStacks().getSlots() + ")");
            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(declaresThree.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void capacityIsTheMaximumDeclaredNotTheSum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-max-not-sum", 10.0D);
        TestProvider providerOne = new TestProvider("test:provider_one", 1, null, null, 0);
        TestProvider providerThree = new TestProvider("test:provider_three", 3, null, null, 0);
        TestProvider providerThreeAlt = new TestProvider("test:provider_three_alt", 3, null, null, 0);
        try {
            ResourceSlotProviderRegistry.register(providerOne);
            ResourceSlotProviderRegistry.register(providerThree);
            ResourceSlotProviderRegistry.register(providerThreeAlt);
            ICurioStacksHandler handler = handler(wearer, CurioSlotIds.RESOURCE, helper);

            providerOne.equipped = true;
            ResourceSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 1,
                    "A single declared-1 provider did not grant exactly 1 slot");

            providerThree.equipped = true;
            providerThreeAlt.equipped = true;
            ResourceSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 3,
                    "Providers declaring 1, 3, 3 did not cap capacity at the"
                            + " maximum (3), found " + handler.getStacks().getSlots());

            providerThree.equipped = false;
            ResourceSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 3,
                    "Removing one of two equally-declared providers changed"
                            + " capacity while another declaring the same"
                            + " maximum is still equipped");

            providerThreeAlt.equipped = false;
            ResourceSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 1,
                    "Capacity did not fall back to the remaining provider's"
                            + " declared count");

            providerOne.equipped = false;
            ResourceSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 0,
                    "Capacity did not return to 0 once every provider was gone");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(providerOne.providerId());
            ResourceSlotProviderRegistry.unregister(providerThree.providerId());
            ResourceSlotProviderRegistry.unregister(providerThreeAlt.providerId());
            wearer.discard();
        }
    }

    /** Scenario 4: two Encyclopedias worn simultaneously still only guarantee 1 slot. */
    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void twoEncyclopediasWornAtOnceDoNotDoubleCapacity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-dedup", 20.0D);
        try {
            ICuriosItemHandler inventory = inventory(wearer, helper);
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.BOOK, UUID.randomUUID(), "test:book_slot_capacity",
                    1.0D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler bookHandler = handler(wearer, CurioSlotIds.BOOK, helper);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);

            handsHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.casper.goetyarkham.item.ModItems.ENCYCLOPEDIA.get()));
            bookHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.casper.goetyarkham.item.ModItems.ENCYCLOPEDIA.get()));
            settleCurioChange(wearer);

            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Two simultaneously worn Encyclopedias granted more than"
                            + " the single declared resource slot (found "
                            + resourceHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /** Scenario 5/6: unequipping the Encyclopedia removes only its own contribution. */
    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void unequippingEncyclopediaOnlyRemovesItsOwnContribution(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-unequip-only-own", 21.0D);
        TestProvider otherProvider = new TestProvider("test:other_provider", 2, null, null, 0);
        try {
            ResourceSlotProviderRegistry.register(otherProvider);
            otherProvider.equipped = true;
            ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler handler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(handler.getStacks().getSlots() == 2, "Setup: expected 2 slots");

            equipEncyclopedia(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 2,
                    "Encyclopedia should not have raised capacity above the"
                            + " other provider's declared 2");

            unequipEncyclopedia(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 2,
                    "Unequipping the Encyclopedia removed capacity still"
                            + " provided by another equipped provider");

            otherProvider.equipped = false;
            ResourceSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 0,
                    "Capacity did not fall to 0 once every provider was gone");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(otherProvider.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void contentIsRestrictedToTheFourAllowedItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-restrictions", 30.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            IDynamicStackHandler stacks = resourceHandler.getStacks();

            helper.assertTrue(
                    stacks.isItemValid(0, new ItemStack(Items.IRON_INGOT, 64)),
                    "A stack of 64 Iron Ingots was rejected");
            helper.assertTrue(
                    stacks.isItemValid(0, new ItemStack(Items.RABBIT_FOOT, 1)),
                    "A single Rabbit's Foot was rejected");
            helper.assertTrue(
                    stacks.isItemValid(0, new ItemStack(Items.BOOK, 1)),
                    "A single Book was rejected");
            helper.assertTrue(
                    stacks.isItemValid(0, new ItemStack(ModItems.ECTOPLASM.get(), 1)),
                    "A single Ectoplasm was rejected");
            helper.assertTrue(
                    !stacks.isItemValid(0, new ItemStack(Items.STONE, 64)),
                    "An unrelated item (Stone) was incorrectly accepted");

            ItemStack ironLeftover = stacks.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false);
            helper.assertTrue(stacks.getStackInSlot(0).getCount() == 1
                            && ironLeftover.getCount() == 63,
                    "64 Iron Ingots did not split into exactly 1 in the slot"
                            + " and 63 leftover");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void occupiedSlotRejectsFurtherPlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-occupied", 40.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            IDynamicStackHandler stacks = resourceHandler.getStacks();

            stacks.insertItem(0, new ItemStack(Items.IRON_INGOT, 1), false);
            settleCurioChange(wearer);

            helper.assertTrue(!stacks.isItemValid(0, new ItemStack(Items.IRON_INGOT, 1)),
                    "An occupied slot accepted a second Iron Ingot");
            helper.assertTrue(!stacks.isItemValid(0, new ItemStack(Items.BOOK, 1)),
                    "An occupied slot accepted a different legal item (Book)");
            helper.assertTrue(stacks.getStackInSlot(0).is(Items.IRON_INGOT)
                            && stacks.getStackInSlot(0).getCount() == 1,
                    "The already-equipped item changed while a rejected"
                            + " placement was attempted");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void encyclopediaGrantsPlusTwoForEachAllowedItemOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-encyclopedia-stats", 50.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);

            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            place(resourceHandler, new ItemStack(Items.IRON_INGOT), wearer);
            assertOnly(helper, wearer, StatType.STRENGTH, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Iron Ingot");

            place(resourceHandler, new ItemStack(Items.RABBIT_FOOT), wearer);
            assertOnly(helper, wearer, StatType.AGILITY, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Rabbit's Foot");

            place(resourceHandler, new ItemStack(Items.BOOK), wearer);
            assertOnly(helper, wearer, StatType.INTELLECT, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Book");

            place(resourceHandler, new ItemStack(ModItems.ECTOPLASM.get()), wearer);
            assertOnly(helper, wearer, StatType.WILLPOWER, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Ectoplasm");

            // Repeated refreshes must never duplicate the currently-worn bonus.
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            assertOnly(helper, wearer, StatType.WILLPOWER, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Ectoplasm (after repeated refresh)");

            place(resourceHandler, ItemStack.EMPTY, wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH) == baseStrength
                            && PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                                    == baseAgility
                            && PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                                    == baseIntellect
                            && PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                                    == baseWillpower,
                    "Emptying the slot left a residual bonus");

            unequipEncyclopedia(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH) == baseStrength
                            && PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                                    == baseAgility
                            && PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                                    == baseIntellect
                            && PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                                    == baseWillpower,
                    "Unequipping the Encyclopedia left a residual bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void multipleProvidersStackTheirOwnRulesOnTheSameItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-multi-provider-stack", 60.0D);
        TestProvider perception = new TestProvider(
                "test:preternatural_perception", 3, StatType.INTELLECT, Items.BOOK, 1);
        TestProvider occultStudies = new TestProvider(
                "test:occult_studies", 3, StatType.INTELLECT, Items.BOOK, 1);
        try {
            ResourceSlotProviderRegistry.register(perception);
            ResourceSlotProviderRegistry.register(occultStudies);
            perception.equipped = true;
            occultStudies.equipped = true;
            equipEncyclopedia(wearer);

            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Two declared-3 providers plus a declared-1 Encyclopedia"
                            + " did not cap capacity at 3");

            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            for (int slot = 0; slot < 3; slot++) {
                resourceHandler.getStacks().setStackInSlot(slot, new ItemStack(Items.BOOK));
            }
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            int intellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            helper.assertTrue(intellect == baseIntellect + 12,
                    "3 Book-filled slots under Perception (+1/ea) + Occult"
                            + " Studies (+1/ea) + Encyclopedia (+2/ea) did not"
                            + " total +12 Intellect (found +"
                            + (intellect - baseIntellect) + ")");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(perception.providerId());
            ResourceSlotProviderRegistry.unregister(occultStudies.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void shrinkingCapacityPreservesLowIndexAndReturnsOverflow(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-shrink-return", 70.0D);
        TestProvider bigProvider = new TestProvider("test:shrink_big", 3, null, null, 0);
        try {
            ResourceSlotProviderRegistry.register(bigProvider);
            bigProvider.equipped = true;
            equipEncyclopedia(wearer);

            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: capacity did not reach 3 with both providers equipped");

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(ModItems.ECTOPLASM.get()));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.RABBIT_FOOT));
            int ectoplasmBefore = wearer.getInventory().countItem(ModItems.ECTOPLASM.get());
            int rabbitFootBefore = wearer.getInventory().countItem(Items.RABBIT_FOOT);

            bigProvider.equipped = false;
            ResourceSlotService.reconcile(wearer);

            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Capacity did not shrink back down to the remaining"
                            + " Encyclopedia's declared count of 1");
            helper.assertTrue(resourceHandler.getStacks().getStackInSlot(0).is(Items.BOOK),
                    "Shrinking disturbed the preserved low-index (slot 0) item");
            helper.assertTrue(
                    wearer.getInventory().countItem(ModItems.ECTOPLASM.get())
                            == ectoplasmBefore + 1,
                    "The Ectoplasm from vacated slot 1 was not returned to the"
                            + " wearer's inventory");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.RABBIT_FOOT)
                            == rabbitFootBefore + 1,
                    "The Rabbit's Foot from vacated slot 2 was not returned to"
                            + " the wearer's inventory");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(bigProvider.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void shrinkingCapacityDropsOverflowWhenInventoryIsFull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-shrink-drop", 80.0D);
        TestProvider bigProvider = new TestProvider("test:shrink_drop_big", 3, null, null, 0);
        try {
            ResourceSlotProviderRegistry.register(bigProvider);
            bigProvider.equipped = true;
            equipEncyclopedia(wearer);

            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: capacity did not reach 3 with both providers equipped");

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.RABBIT_FOOT));
            fillInventory(wearer);

            long rabbitFootDropsBefore = countNearbyRabbitFootDrops(level, wearer);

            bigProvider.equipped = false;
            ResourceSlotService.reconcile(wearer);

            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Capacity did not shrink back down to 1");
            long rabbitFootDropsAfter = countNearbyRabbitFootDrops(level, wearer);
            long rabbitFootDrops = rabbitFootDropsAfter - rabbitFootDropsBefore;
            helper.assertTrue(rabbitFootDrops == 1,
                    "The Rabbit's Foot from the vacated top slot was not"
                            + " dropped exactly once when the inventory was full"
                            + " (found " + rabbitFootDrops + ")");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(bigProvider.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void capacityDroppingToZeroReturnsEverything(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-to-zero", 90.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            int booksBefore = wearer.getInventory().countItem(Items.BOOK);

            unequipEncyclopedia(wearer);

            helper.assertTrue(resourceHandler.getStacks().getSlots() == 0,
                    "Capacity did not fall to 0 once the only provider was gone");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.BOOK) == booksBefore + 1,
                    "The Book left in the vacated slot was not returned to the"
                            + " wearer's inventory");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void reconciliationIsIsolatedBetweenPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer playerA = testPlayer(level, "resource-isolation-a", 100.0D);
        TestPlayer playerB = testPlayer(level, "resource-isolation-b", 110.0D);
        try {
            ICurioStacksHandler handlerA = handler(playerA, CurioSlotIds.RESOURCE, helper);
            ICurioStacksHandler handlerB = handler(playerB, CurioSlotIds.RESOURCE, helper);

            equipEncyclopedia(playerA);
            helper.assertTrue(handlerA.getStacks().getSlots() == 1,
                    "Player A's own Encyclopedia did not grant their slot");
            helper.assertTrue(handlerB.getStacks().getSlots() == 0,
                    "Equipping Player A's Encyclopedia leaked capacity to Player B");

            handlerA.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            int baseStrengthB = PlayerStatsService.getFinalValue(playerB, StatType.STRENGTH);
            EquipmentStatsService.refresh(playerB);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(playerB, StatType.STRENGTH)
                            == baseStrengthB,
                    "Player A's resource contents leaked a stat bonus to Player B");

            helper.succeed();
        } finally {
            playerA.discard();
            playerB.discard();
        }
    }

    /**
     * Reproduces the reported save/relogin bug directly: a provider that
     * transiently reports "not equipped" during a login-restore reconcile
     * must never shrink or evacuate {@code resource}, since a real player
     * entity's Curios handler may not have finished settling its equipped
     * state by the time the first post-login reconcile runs.
     */
    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void restoreReconcileNeverEvacuatesWhenProviderTransientlyUnreadable(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "resource-restore-transient", 120.0D);
        TestProvider provider = new TestProvider("test:restore_transient", 1, null, null, 0);
        try {
            ResourceSlotProviderRegistry.register(provider);
            provider.equipped = true;
            ResourceSlotService.reconcile(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            int booksInInventoryBefore = wearer.getInventory().countItem(Items.BOOK);

            provider.equipped = false;
            ResourceSlotService.reconcileRestore(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Restore reconcile shrank resource even though it must"
                            + " never shrink");
            helper.assertTrue(resourceHandler.getStacks().getStackInSlot(0).is(Items.BOOK),
                    "Restore reconcile evacuated the Book still sitting in"
                            + " resource slot 0");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.BOOK) == booksInInventoryBefore,
                    "Restore reconcile returned the Book to the inventory even"
                            + " though it must never evacuate");

            ResourceSlotService.reconcileRestore(wearer);
            ResourceSlotService.reconcileRestore(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1
                            && resourceHandler.getStacks().getStackInSlot(0).is(Items.BOOK),
                    "Repeated restore reconciles disturbed the preserved slot");

            provider.equipped = true;
            ResourceSlotService.reconcileRestore(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1
                            && resourceHandler.getStacks().getStackInSlot(0).is(Items.BOOK),
                    "Slot changed once the provider became readable again");

            provider.equipped = false;
            ResourceSlotService.reconcile(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 0,
                    "A confirmed reconcile after the provider was genuinely"
                            + " removed did not shrink resource");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.BOOK)
                            == booksInInventoryBefore + 1,
                    "The confirmed shrink did not return the Book exactly once");

            ResourceSlotService.reconcile(wearer);
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.BOOK)
                            == booksInInventoryBefore + 1,
                    "A repeated confirmed reconcile duplicated the refunded Book");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(provider.providerId());
            wearer.discard();
        }
    }

    /**
     * Login/respawn/clone/dimension-change restore round trip using the
     * real Encyclopedia item. Curios' own {@code ICuriosItemHandler#readTag}
     * rebuilds every slot handler at its live base size before any of this
     * mod's own restore logic runs (persistent attribute modifiers are
     * never themselves part of the NBT round trip - by design, this
     * mod always re-derives them live from current equip state), so a
     * base-size-0 slot like {@code resource} has nothing to receive the
     * deserialized content into yet: Curios safely returns it to the plain
     * inventory itself via its own recall path before {@link
     * EncyclopediaService#reconcileRestore} ever gets a chance to reinstate
     * the slot's capacity. The guarantee this architecture actually
     * provides - and the one this test verifies - is that capacity is
     * correctly restored and the Book is never lost or duplicated across
     * the round trip, not that it survives at its exact prior slot index.
     */
    @GameTest(template = "empty", batch = RESOURCE_TEST_BATCH)
    public static void encyclopediaAndResourceCapacityAndContentsSurviveRealNbtReloginRoundTrip(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer before = testPlayer(level, "resource-relogin-before", 130.0D);
        TestPlayer after = null;
        try {
            equipEncyclopedia(before);
            ICurioStacksHandler resourceHandlerBefore =
                    handler(before, CurioSlotIds.RESOURCE, helper);
            resourceHandlerBefore.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            settleCurioChange(before);
            int totalBooksBefore = totalBookCount(before, resourceHandlerBefore);
            int baselineIntellect = PlayerStatsService.getFinalValue(before, StatType.INTELLECT)
                    - EncyclopediaBonusProvider.INSTANCE.statBonus(StatType.INTELLECT, Items.BOOK);

            net.minecraft.nbt.Tag saved = inventory(before, helper).writeTag();
            before.discard();

            after = testPlayer(level, "resource-relogin-after", 130.0D);
            ICuriosItemHandler inventoryAfter = inventory(after, helper);
            inventoryAfter.readTag(saved);
            EncyclopediaService.reconcileRestore(after);
            // Curios' own readTag only queues content it couldn't place
            // (e.g. this base-size-0 slot at its live capacity of 0, before
            // the restore reconcile above reinstates it) into an internal
            // pending list via loseInvalidStack - handleInvalidStacks is
            // what actually hands it back to the player, exactly like
            // Curios' own regular per-entity tick does in real play.
            inventoryAfter.handleInvalidStacks();

            ICurioStacksHandler resourceHandlerAfter =
                    handler(after, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandlerAfter.getStacks().getSlots() == 1,
                    "Login-restore reconcile did not restore resource capacity"
                            + " to 1 even though the Encyclopedia is still equipped");
            int totalBooksAfter = totalBookCount(after, resourceHandlerAfter);
            helper.assertTrue(totalBooksAfter == totalBooksBefore,
                    "The Book was lost or duplicated across the NBT round trip"
                            + " (before=" + totalBooksBefore + ", after=" + totalBooksAfter + ")");

            EquipmentStatsService.refresh(after);
            boolean bookBackInResourceSlot =
                    resourceHandlerAfter.getStacks().getStackInSlot(0).is(Items.BOOK);
            int expectedBonus = bookBackInResourceSlot
                    ? EncyclopediaBonusProvider.INSTANCE.statBonus(StatType.INTELLECT, Items.BOOK)
                    : 0;
            int intellectAfterRefresh = PlayerStatsService.getFinalValue(after, StatType.INTELLECT);
            helper.assertTrue(intellectAfterRefresh == baselineIntellect + expectedBonus,
                    "Intellect after relogin did not match whatever the Book's"
                            + " actual restored location implies (bookInSlot="
                            + bookBackInResourceSlot + ")");

            EncyclopediaService.reconcile(after);
            helper.assertTrue(resourceHandlerAfter.getStacks().getSlots() == 1,
                    "A follow-up confirmed reconcile disturbed the restored"
                            + " capacity even though the Encyclopedia is still"
                            + " equipped");
            helper.assertTrue(totalBookCount(after, resourceHandlerAfter) == totalBooksBefore,
                    "A follow-up confirmed reconcile lost or duplicated the Book");

            helper.succeed();
        } finally {
            before.discard();
            if (after != null) {
                after.discard();
            }
        }
    }

    /** Total Books either sitting in the resource slot or in the plain inventory. */
    private static int totalBookCount(ServerPlayer player, ICurioStacksHandler resourceHandler) {
        int inResourceSlot = resourceHandler.getStacks().getStackInSlot(0).is(Items.BOOK) ? 1 : 0;
        return player.getInventory().countItem(Items.BOOK) + inResourceSlot;
    }

    private static void equipEncyclopedia(ServerPlayer wearer) {
        ICurioStacksHandler handsHandler = CuriosApi.getCuriosInventory(wearer)
                .resolve().orElseThrow().getStacksHandler(CurioSlotIds.HANDS).orElseThrow();
        handsHandler.getStacks().setStackInSlot(
                0, new ItemStack(com.casper.goetyarkham.item.ModItems.ENCYCLOPEDIA.get()));
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

    private static void place(ICurioStacksHandler resourceHandler, ItemStack stack, ServerPlayer wearer) {
        resourceHandler.getStacks().setStackInSlot(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        settleCurioChange(wearer);
        EquipmentStatsService.refresh(wearer);
    }

    private static void assertOnly(
            GameTestHelper helper,
            ServerPlayer wearer,
            StatType expected,
            int baseStrength,
            int baseAgility,
            int baseIntellect,
            int baseWillpower,
            String label) {
        helper.assertTrue(
                PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                        == baseStrength + (expected == StatType.STRENGTH ? 2 : 0),
                label + " Strength mismatch");
        helper.assertTrue(
                PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                        == baseAgility + (expected == StatType.AGILITY ? 2 : 0),
                label + " Agility mismatch");
        helper.assertTrue(
                PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                        == baseIntellect + (expected == StatType.INTELLECT ? 2 : 0),
                label + " Intellect mismatch");
        helper.assertTrue(
                PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                        == baseWillpower + (expected == StatType.WILLPOWER ? 2 : 0),
                label + " Willpower mismatch");
    }

    /**
     * Counts dropped Rabbit's Foot {@link ItemEntity} instances near {@code
     * wearer}. Used as a before/after delta rather than a raw count, since a
     * concurrently-running sibling test in this same batch may legitimately
     * have its own Rabbit's Foot drop land nearby.
     */
    private static long countNearbyRabbitFootDrops(ServerLevel level, ServerPlayer wearer) {
        return level.getEntitiesOfClass(ItemEntity.class, wearer.getBoundingBox().inflate(3.0D))
                .stream()
                .filter(entity -> entity.getItem().is(Items.RABBIT_FOOT))
                .count();
    }

    private static void fillInventory(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, new ItemStack(Items.DIRT, 64));
        }
    }

    private static ICuriosItemHandler inventory(ServerPlayer player, GameTestHelper helper) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        helper.assertTrue(inventory != null, "Missing Curios inventory");
        return inventory;
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

    /**
     * Each test uses its own {@code x} offset so concurrently-running tests
     * within this batch never share a position - important for the tests
     * that assert on nearby dropped {@code ItemEntity} instances.
     */
    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 0.0D);
        return player;
    }

    /**
     * A minimal, controllable {@link ResourceSlotProvider} stand-in for
     * exercising the generic capacity-math and multi-provider stat-stacking
     * rules without registering any unfinished real item. When {@code stat}
     * is {@code null} it never scores anything (used by the pure
     * capacity-math tests).
     */
    private static final class TestProvider implements ResourceSlotProvider {
        private final String id;
        private final int slotCount;
        private final StatType stat;
        private final Item scoredItem;
        private final int amount;
        private volatile boolean equipped;

        TestProvider(String id, int slotCount, StatType stat, Item scoredItem, int amount) {
            this.id = id;
            this.slotCount = slotCount;
            this.stat = stat;
            this.scoredItem = scoredItem;
            this.amount = amount;
        }

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
            return equipped;
        }

        @Override
        public int statBonus(StatType queriedStat, Item item) {
            if (stat == null) {
                return 0;
            }
            return queriedStat == stat && item == scoredItem ? amount : 0;
        }
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
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
