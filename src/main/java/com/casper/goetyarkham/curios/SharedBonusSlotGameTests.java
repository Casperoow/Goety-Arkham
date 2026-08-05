package com.casper.goetyarkham.curios;

import com.Polarice3.Goety.common.items.ModItems;
import com.casper.goetyarkham.GoetyArkham;
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
 * Exercises the generic {@link CurioSlotIds#SKILL_BONUS} mechanism: {@link
 * SharedBonusSlotService}'s maximum-not-sum, deduplicated-by-provider-ID
 * capacity computation, {@link SharedBonusSlotContentPolicy}'s four-item
 * content restriction, safe shrink/refund via {@link
 * DynamicCurioSlotContributionService#reconcileSize}, and the Encyclopedia's
 * own {@link com.casper.goetyarkham.item.EncyclopediaBonusProvider} stat
 * scoring, including how it stacks with other simultaneously active
 * providers. The multi-provider math is exercised with throwaway {@link
 * TestProvider} stand-ins - matching how a future "Preternatural Perception"
 * or "Occult Studies" item would plug in - rather than registering any
 * unfinished real item.
 *
 * <p>Per the project's GameTest {@code TestPlayer} gotchas, these stand-ins
 * are deliberately never added to {@code level.players()}.</p>
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SharedBonusSlotGameTests {
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
    private static final String SKILL_BONUS_TEST_BATCH = "goetyarkham:skill_bonus";

    private SharedBonusSlotGameTests() {
    }

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void slotStartsHiddenAtZeroCapacity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-zero", 0.0D);
        try {
            ICurioStacksHandler handler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);
            helper.assertTrue(handler.getStacks().getSlots() == 0,
                    "skill_bonus did not start at base size 0");
            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void capacityIsTheMaximumDeclaredNotTheSum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-max-not-sum", 10.0D);
        TestProvider providerOne = new TestProvider("test:provider_one", 1, null, null, 0);
        TestProvider providerThree = new TestProvider("test:provider_three", 3, null, null, 0);
        TestProvider providerThreeAlt = new TestProvider("test:provider_three_alt", 3, null, null, 0);
        try {
            SharedBonusSlotProviderRegistry.register(providerOne);
            SharedBonusSlotProviderRegistry.register(providerThree);
            SharedBonusSlotProviderRegistry.register(providerThreeAlt);
            ICurioStacksHandler handler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);

            providerOne.equipped = true;
            SharedBonusSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 1,
                    "A single declared-1 provider did not grant exactly 1 slot");

            providerThree.equipped = true;
            providerThreeAlt.equipped = true;
            SharedBonusSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 3,
                    "Providers declaring 1, 3, 3 did not cap capacity at the"
                            + " maximum (3), found " + handler.getStacks().getSlots());

            providerThree.equipped = false;
            SharedBonusSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 3,
                    "Removing one of two equally-declared providers changed"
                            + " capacity while another declaring the same"
                            + " maximum is still equipped");

            providerThreeAlt.equipped = false;
            SharedBonusSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 1,
                    "Capacity did not fall back to the remaining provider's"
                            + " declared count");

            providerOne.equipped = false;
            SharedBonusSlotService.reconcile(wearer);
            helper.assertTrue(handler.getStacks().getSlots() == 0,
                    "Capacity did not return to 0 once every provider was gone");

            helper.succeed();
        } finally {
            SharedBonusSlotProviderRegistry.unregister(providerOne.providerId());
            SharedBonusSlotProviderRegistry.unregister(providerThree.providerId());
            SharedBonusSlotProviderRegistry.unregister(providerThreeAlt.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void twoEncyclopediasWornAtOnceDoNotDoubleCapacity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-dedup", 20.0D);
        try {
            ICuriosItemHandler inventory = inventory(wearer, helper);
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.BOOK, UUID.randomUUID(), "test:book_slot_capacity",
                    1.0D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler bookHandler = handler(wearer, CurioSlotIds.BOOK, helper);
            ICurioStacksHandler skillHandler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);

            handsHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.casper.goetyarkham.item.ModItems.ENCYCLOPEDIA.get()));
            bookHandler.getStacks().setStackInSlot(
                    0, new ItemStack(com.casper.goetyarkham.item.ModItems.ENCYCLOPEDIA.get()));
            settleCurioChange(wearer);

            helper.assertTrue(skillHandler.getStacks().getSlots() == 1,
                    "Two simultaneously worn Encyclopedias granted more than"
                            + " the single declared skill_bonus slot (found "
                            + skillHandler.getStacks().getSlots() + ")");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void contentIsRestrictedToTheFourAllowedItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-restrictions", 30.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler skillHandler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);
            IDynamicStackHandler stacks = skillHandler.getStacks();

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

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void occupiedSlotRejectsFurtherPlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-occupied", 40.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler skillHandler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);
            IDynamicStackHandler stacks = skillHandler.getStacks();

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

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void encyclopediaGrantsPlusTwoForEachAllowedItemOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-encyclopedia-stats", 50.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler skillHandler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);

            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);
            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            place(skillHandler, new ItemStack(Items.IRON_INGOT), wearer);
            assertOnly(helper, wearer, StatType.STRENGTH, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Iron Ingot");

            place(skillHandler, new ItemStack(Items.RABBIT_FOOT), wearer);
            assertOnly(helper, wearer, StatType.AGILITY, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Rabbit's Foot");

            place(skillHandler, new ItemStack(Items.BOOK), wearer);
            assertOnly(helper, wearer, StatType.INTELLECT, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Book");

            place(skillHandler, new ItemStack(ModItems.ECTOPLASM.get()), wearer);
            assertOnly(helper, wearer, StatType.WILLPOWER, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Ectoplasm");

            // Repeated refreshes must never duplicate the currently-worn bonus.
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            assertOnly(helper, wearer, StatType.WILLPOWER, baseStrength, baseAgility,
                    baseIntellect, baseWillpower, "Ectoplasm (after repeated refresh)");

            place(skillHandler, ItemStack.EMPTY, wearer);
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

    /**
     * Simulates the future "3 slots, all Books" example from the design:
     * two hypothetical providers (standing in for Preternatural Perception
     * and Occult Studies) each declaring 3 slots and +1 Intellect per Book,
     * plus the real Encyclopedia (+2 Intellect per Book, still scanning all
     * 3 slots even though it only declares 1 itself) - total +12 Intellect
     * across 3 Book-filled slots.
     */
    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void multipleProvidersStackTheirOwnRulesOnTheSameItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-multi-provider-stack", 60.0D);
        TestProvider perception = new TestProvider(
                "test:preternatural_perception", 3, StatType.INTELLECT, Items.BOOK, 1);
        TestProvider occultStudies = new TestProvider(
                "test:occult_studies", 3, StatType.INTELLECT, Items.BOOK, 1);
        try {
            SharedBonusSlotProviderRegistry.register(perception);
            SharedBonusSlotProviderRegistry.register(occultStudies);
            perception.equipped = true;
            occultStudies.equipped = true;
            equipEncyclopedia(wearer);

            ICurioStacksHandler skillHandler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 3,
                    "Two declared-3 providers plus a declared-1 Encyclopedia"
                            + " did not cap capacity at 3");

            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            for (int slot = 0; slot < 3; slot++) {
                skillHandler.getStacks().setStackInSlot(slot, new ItemStack(Items.BOOK));
            }
            settleCurioChange(wearer);
            EquipmentStatsService.refresh(wearer);

            int intellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            helper.assertTrue(intellect == baseIntellect + 12,
                    "3 Book-filled slots under Perception (+1/ea) + Occult"
                            + " Studies (+1/ea) + Encyclopedia (+2/ea) did not"
                            + " total +12 Intellect (found +"
                            + (intellect - baseIntellect) + ")");

            // A provider that does not recognize an item contributes 0 for it.
            TestProvider ironOnly = new TestProvider(
                    "test:iron_only", 3, StatType.STRENGTH, Items.IRON_INGOT, 5);
            SharedBonusSlotProviderRegistry.register(ironOnly);
            ironOnly.equipped = true;
            EquipmentStatsService.refresh(wearer);
            int intellectAfterIronOnly =
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            helper.assertTrue(intellectAfterIronOnly == intellect,
                    "A provider unrelated to Books changed the Book-derived"
                            + " Intellect bonus");
            SharedBonusSlotProviderRegistry.unregister(ironOnly.providerId());

            helper.succeed();
        } finally {
            SharedBonusSlotProviderRegistry.unregister(perception.providerId());
            SharedBonusSlotProviderRegistry.unregister(occultStudies.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void shrinkingCapacityPreservesLowIndexAndReturnsOverflow(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-shrink-return", 70.0D);
        TestProvider bigProvider = new TestProvider("test:shrink_big", 3, null, null, 0);
        try {
            SharedBonusSlotProviderRegistry.register(bigProvider);
            bigProvider.equipped = true;
            equipEncyclopedia(wearer);

            ICurioStacksHandler skillHandler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 3,
                    "Setup: capacity did not reach 3 with both providers equipped");

            skillHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            skillHandler.getStacks().setStackInSlot(1, new ItemStack(ModItems.ECTOPLASM.get()));
            skillHandler.getStacks().setStackInSlot(2, new ItemStack(Items.RABBIT_FOOT));
            int ectoplasmBefore = wearer.getInventory().countItem(ModItems.ECTOPLASM.get());
            int rabbitFootBefore = wearer.getInventory().countItem(Items.RABBIT_FOOT);

            bigProvider.equipped = false;
            SharedBonusSlotService.reconcile(wearer);

            helper.assertTrue(skillHandler.getStacks().getSlots() == 1,
                    "Capacity did not shrink back down to the remaining"
                            + " Encyclopedia's declared count of 1");
            helper.assertTrue(skillHandler.getStacks().getStackInSlot(0).is(Items.BOOK),
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
            SharedBonusSlotProviderRegistry.unregister(bigProvider.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void shrinkingCapacityDropsOverflowWhenInventoryIsFull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-shrink-drop", 80.0D);
        TestProvider bigProvider = new TestProvider("test:shrink_drop_big", 3, null, null, 0);
        try {
            SharedBonusSlotProviderRegistry.register(bigProvider);
            bigProvider.equipped = true;
            equipEncyclopedia(wearer);

            ICurioStacksHandler skillHandler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 3,
                    "Setup: capacity did not reach 3 with both providers equipped");

            skillHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            skillHandler.getStacks().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
            skillHandler.getStacks().setStackInSlot(2, new ItemStack(Items.RABBIT_FOOT));
            fillInventory(wearer);

            bigProvider.equipped = false;
            SharedBonusSlotService.reconcile(wearer);

            helper.assertTrue(skillHandler.getStacks().getSlots() == 1,
                    "Capacity did not shrink back down to 1");
            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class,
                    wearer.getBoundingBox().inflate(3.0D));
            long rabbitFootDrops = dropped.stream()
                    .filter(entity -> entity.getItem().is(Items.RABBIT_FOOT))
                    .count();
            helper.assertTrue(rabbitFootDrops == 1,
                    "The Rabbit's Foot from the vacated top slot was not"
                            + " dropped exactly once when the inventory was full"
                            + " (found " + rabbitFootDrops + ")");

            helper.succeed();
        } finally {
            SharedBonusSlotProviderRegistry.unregister(bigProvider.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void capacityDroppingToZeroReturnsEverything(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "skill-bonus-to-zero", 90.0D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler skillHandler = handler(wearer, CurioSlotIds.SKILL_BONUS, helper);
            skillHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            int booksBefore = wearer.getInventory().countItem(Items.BOOK);

            unequipEncyclopedia(wearer);

            helper.assertTrue(skillHandler.getStacks().getSlots() == 0,
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

    @GameTest(template = "empty", batch = SKILL_BONUS_TEST_BATCH)
    public static void reconciliationIsIsolatedBetweenPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer playerA = testPlayer(level, "skill-bonus-isolation-a", 100.0D);
        TestPlayer playerB = testPlayer(level, "skill-bonus-isolation-b", 110.0D);
        try {
            ICurioStacksHandler handlerA = handler(playerA, CurioSlotIds.SKILL_BONUS, helper);
            ICurioStacksHandler handlerB = handler(playerB, CurioSlotIds.SKILL_BONUS, helper);

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
                    "Player A's skill_bonus contents leaked a stat bonus to Player B");

            helper.succeed();
        } finally {
            playerA.discard();
            playerB.discard();
        }
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

    private static void place(ICurioStacksHandler skillHandler, ItemStack stack, ServerPlayer wearer) {
        skillHandler.getStacks().setStackInSlot(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
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
     * A minimal, controllable {@link SharedBonusSlotProvider} stand-in for
     * exercising the generic capacity-math and multi-provider stat-stacking
     * rules without registering any unfinished real item. When {@code stat}
     * is {@code null} it never scores anything (used by the pure
     * capacity-math tests).
     */
    private static final class TestProvider implements SharedBonusSlotProvider {
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
