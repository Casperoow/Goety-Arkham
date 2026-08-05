package com.casper.goetyarkham.curios;

import com.Polarice3.Goety.common.items.ModItems;
import com.casper.goetyarkham.GoetyArkham;
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
 * Exercises {@link DynamicCurioSlotContributionService} and {@link
 * EncyclopediaSkillContentPolicy} against the {@link
 * CurioSlotIds#ENCYCLOPEDIA_SKILL} slot. No provider item exists yet, so
 * every test drives the generic slot-growth service directly with a plain
 * vanilla stand-in item worn in an unrelated slot - exactly the shape a
 * future Encyclopedia item's own service wrapper would call.
 *
 * <p>Per the project's GameTest {@code TestPlayer} gotchas, these stand-ins
 * are deliberately never added to {@code level.players()}.</p>
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EncyclopediaSkillGameTests {
    /**
     * Isolates these tests from {@code defaultBatch}'s ~90 concurrently
     * running tests. Two of these tests spawn a real dropped {@code
     * ItemEntity}, which {@code ChunkMap} broadcasts to every entry in
     * {@code level.players()} - including the connectionless {@code
     * TestPlayer} stand-ins several *other*, pre-existing test suites
     * (unrelated to this feature) leave registered there, which NPEs on
     * broadcast and can crash the whole batch (see the project's GameTest
     * {@code TestPlayer} gotchas). A dedicated batch, exactly like the
     * existing chaos-bag test suites' own {@code CHAOS_BAG_TEST_BATCH}, is
     * the established fix.
     */
    private static final String ENCYCLOPEDIA_SKILL_TEST_BATCH =
            "goetyarkham:encyclopedia_skill";

    private static final UUID PROVIDER_A_MODIFIER_ID = UUID.fromString(
            "1b2c3d4e-5f60-4a1b-8c2d-3e4f5a6b7c8d");
    private static final String PROVIDER_A_MODIFIER_NAME =
            "test:encyclopedia_skill_provider_a";
    private static final List<String> PROVIDER_A_WORN_SLOTS = List.of(CurioSlotIds.TOKEN);

    private static final UUID PROVIDER_B_MODIFIER_ID = UUID.fromString(
            "2c3d4e5f-6071-4b2c-9d3e-4f5a6b7c8d9e");
    private static final String PROVIDER_B_MODIFIER_NAME =
            "test:encyclopedia_skill_provider_b";
    private static final List<String> PROVIDER_B_WORN_SLOTS = List.of(CurioSlotIds.HANDS);

    private EncyclopediaSkillGameTests() {
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_SKILL_TEST_BATCH)
    public static void slotStartsAtZeroAndGrowsExactlyOnceWhileWorn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-skill-growth", 0.0D);
        try {
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);

            helper.assertTrue(skillHandler.getStacks().getSlots() == 0,
                    "encyclopedia_skill did not start at base size 0");

            reconcileProviderA(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 0,
                    "encyclopedia_skill grew without a provider actually worn");

            tokenHandler.getStacks().setStackInSlot(0, new ItemStack(Items.STICK));
            settleCurioChange(wearer);
            reconcileProviderA(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 1,
                    "Worn provider did not grant exactly one encyclopedia_skill slot");

            // Repeated reconciliation (login/respawn/dimension-change stand-in)
            // must never duplicate the slot.
            reconcileProviderA(wearer);
            reconcileProviderA(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 1,
                    "Repeated reconciliation duplicated the encyclopedia_skill slot");

            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            reconcileProviderA(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 0,
                    "Unequipping the provider did not remove the granted slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_SKILL_TEST_BATCH)
    public static void slotContentIsRestrictedToTheFourAllowedItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-skill-restrictions", 40.0D);
        try {
            grantOneSlot(wearer);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);

            // Item-type legality never depends on the source stack's count -
            // a full stack of a legal item is a legal *attempt* (exactly one
            // unit is what actually ends up taken; see the dedicated split
            // tests below), just like a full stack held near an item frame.
            helper.assertTrue(
                    skillHandler.getStacks().isItemValid(0, new ItemStack(Items.IRON_INGOT, 64)),
                    "A stack of 64 Iron Ingots was rejected");
            helper.assertTrue(
                    skillHandler.getStacks().isItemValid(0, new ItemStack(Items.RABBIT_FOOT, 1)),
                    "A single Rabbit's Foot was rejected");
            helper.assertTrue(
                    skillHandler.getStacks().isItemValid(0, new ItemStack(Items.BOOK, 1)),
                    "A single Book was rejected");
            helper.assertTrue(
                    skillHandler.getStacks().isItemValid(
                            0, new ItemStack(ModItems.ECTOPLASM.get(), 1)),
                    "A single Ectoplasm was rejected");

            helper.assertTrue(
                    !skillHandler.getStacks().isItemValid(0, new ItemStack(Items.STONE, 1)),
                    "An unrelated item (Stone) was incorrectly accepted");
            helper.assertTrue(
                    !skillHandler.getStacks().isItemValid(0, new ItemStack(Items.STONE, 64)),
                    "A stack of 64 unrelated items (Stone) was incorrectly accepted");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_SKILL_TEST_BATCH)
    public static void insertingFromALargeStackTakesExactlyOneUnit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-skill-split", 60.0D);
        try {
            grantOneSlot(wearer);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);
            IDynamicStackHandler stacks = skillHandler.getStacks();

            // 64 Iron Ingots -> slot gets 1, source stack (the value the
            // caller holds, as returned by insertItem) becomes 63. This is
            // the exact same IItemHandler#insertItem path every real
            // transfer route (pickup-and-place, shift-click, right-click
            // auto-equip, creative placement) resolves to, so exercising it
            // directly is a faithful test of all of them without needing to
            // simulate GUI click algorithms.
            ItemStack ironLeftover = stacks.insertItem(
                    0, new ItemStack(Items.IRON_INGOT, 64), false);
            helper.assertTrue(stacks.getStackInSlot(0).is(Items.IRON_INGOT)
                            && stacks.getStackInSlot(0).getCount() == 1,
                    "64 Iron Ingots did not leave exactly 1 in the slot");
            helper.assertTrue(ironLeftover.getCount() == 63,
                    "64 Iron Ingots did not return exactly 63 as the leftover"
                            + " (found " + ironLeftover.getCount() + ")");
            // No custom splitting logic exists anywhere in this feature -
            // getSlotLimit alone drives insertItem's own split - so simple
            // conservation (1 in slot + 63 returned == 64 original) is
            // already proof there is no double-decrement from any
            // client/server duplicate-processing path.
            helper.assertTrue(1 + ironLeftover.getCount() == 64,
                    "Item count was not conserved across the split (lost or duplicated)");

            settleCurioChange(wearer);
            stacks.setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);

            // 32 Rabbit's Feet -> slot gets 1, leftover is 31.
            ItemStack footLeftover = stacks.insertItem(
                    0, new ItemStack(Items.RABBIT_FOOT, 32), false);
            helper.assertTrue(stacks.getStackInSlot(0).is(Items.RABBIT_FOOT)
                            && stacks.getStackInSlot(0).getCount() == 1,
                    "32 Rabbit's Feet did not leave exactly 1 in the slot");
            helper.assertTrue(footLeftover.getCount() == 31,
                    "32 Rabbit's Feet did not return exactly 31 as the leftover"
                            + " (found " + footLeftover.getCount() + ")");

            settleCurioChange(wearer);
            stacks.setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);

            // A single Book -> slot gets 1, leftover is empty.
            ItemStack bookLeftover = stacks.insertItem(0, new ItemStack(Items.BOOK, 1), false);
            helper.assertTrue(stacks.getStackInSlot(0).is(Items.BOOK)
                            && stacks.getStackInSlot(0).getCount() == 1,
                    "A single Book did not end up in the slot");
            helper.assertTrue(bookLeftover.isEmpty(),
                    "A single Book left a non-empty leftover");

            settleCurioChange(wearer);
            stacks.setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);

            // 64 Stone (illegal item) -> fully rejected, nothing taken.
            ItemStack stoneLeftover = stacks.insertItem(
                    0, new ItemStack(Items.STONE, 64), false);
            helper.assertTrue(stacks.getStackInSlot(0).isEmpty(),
                    "64 Stone was incorrectly accepted into the slot");
            helper.assertTrue(stoneLeftover.getCount() == 64,
                    "64 Stone was not returned in full when rejected");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_SKILL_TEST_BATCH)
    public static void occupiedSlotRejectsFurtherPlacementWithoutChangingEitherCount(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-skill-occupied", 70.0D);
        try {
            grantOneSlot(wearer);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);
            IDynamicStackHandler stacks = skillHandler.getStacks();

            stacks.insertItem(0, new ItemStack(Items.IRON_INGOT, 1), false);
            settleCurioChange(wearer);
            helper.assertTrue(stacks.getStackInSlot(0).getCount() == 1,
                    "Setup: the slot did not end up holding the Iron Ingot");

            // The pre-equip gate must deny any further placement attempt -
            // same item or a different legal one - while occupied, never
            // silently swap or truncate.
            helper.assertTrue(
                    !stacks.isItemValid(0, new ItemStack(Items.IRON_INGOT, 1)),
                    "An occupied slot accepted a second Iron Ingot");
            helper.assertTrue(
                    !stacks.isItemValid(0, new ItemStack(Items.BOOK, 1)),
                    "An occupied slot accepted a different legal item (Book)");

            ItemStack bookAttempt = new ItemStack(Items.BOOK, 5);
            ItemStack leftover = stacks.insertItem(0, bookAttempt.copy(), false);
            helper.assertTrue(leftover.getCount() == 5 && leftover.is(Items.BOOK),
                    "insertItem changed the source stack's count against an occupied slot");
            helper.assertTrue(stacks.getStackInSlot(0).is(Items.IRON_INGOT)
                            && stacks.getStackInSlot(0).getCount() == 1,
                    "The already-equipped Iron Ingot changed while a rejected"
                            + " placement was attempted");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_SKILL_TEST_BATCH)
    public static void eachAllowedItemGrantsOnlyItsOwnStatBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-skill-stats", 50.0D);
        try {
            grantOneSlot(wearer);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);

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

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_SKILL_TEST_BATCH)
    public static void vacatedSlotItemIsReturnedToInventoryWhenThereIsSpace(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-skill-return-inventory", 10.0D);
        try {
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);

            tokenHandler.getStacks().setStackInSlot(0, new ItemStack(Items.STICK));
            settleCurioChange(wearer);
            reconcileProviderA(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 1,
                    "Setup: provider did not grant the encyclopedia_skill slot");

            skillHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            int ironBefore = wearer.getInventory().countItem(Items.IRON_INGOT);

            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            reconcileProviderA(wearer);

            helper.assertTrue(skillHandler.getStacks().getSlots() == 0,
                    "Removing the provider did not shrink the encyclopedia_skill slot");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.IRON_INGOT) == ironBefore + 1,
                    "The Iron Ingot left in the vacated slot was not returned"
                            + " to the wearer's inventory");
            long ironDropsNearby = level.getEntitiesOfClass(ItemEntity.class,
                            wearer.getBoundingBox().inflate(3.0D)).stream()
                    .filter(entity -> entity.getItem().is(Items.IRON_INGOT))
                    .count();
            helper.assertTrue(ironDropsNearby == 0,
                    "An Iron Ingot was dropped in the world even though inventory space"
                            + " existed (possible duplication)");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_SKILL_TEST_BATCH)
    public static void vacatedSlotItemIsDroppedWhenInventoryIsFull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-skill-return-drop", 20.0D);
        try {
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);

            tokenHandler.getStacks().setStackInSlot(0, new ItemStack(Items.STICK));
            settleCurioChange(wearer);
            reconcileProviderA(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 1,
                    "Setup: provider did not grant the encyclopedia_skill slot");

            skillHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK, 1));
            fillInventory(wearer);

            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            reconcileProviderA(wearer);

            helper.assertTrue(skillHandler.getStacks().getSlots() == 0,
                    "Removing the provider did not shrink the encyclopedia_skill slot");

            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class,
                    wearer.getBoundingBox().inflate(3.0D));
            long bookDrops = dropped.stream()
                    .filter(entity -> entity.getItem().is(Items.BOOK))
                    .count();
            long totalBookCount = dropped.stream()
                    .filter(entity -> entity.getItem().is(Items.BOOK))
                    .mapToLong(entity -> entity.getItem().getCount())
                    .sum();
            helper.assertTrue(bookDrops == 1 && totalBookCount == 1,
                    "The Book was not dropped exactly once when the inventory was full"
                            + " (found " + bookDrops + " drop entities totalling "
                            + totalBookCount + ")");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_SKILL_TEST_BATCH)
    public static void twoProvidersStackAndRemovingOneOnlyReturnsTheTopSlot(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-skill-multi-provider", 30.0D);
        try {
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);

            tokenHandler.getStacks().setStackInSlot(0, new ItemStack(Items.STICK));
            handsHandler.getStacks().setStackInSlot(0, new ItemStack(Items.STICK));
            settleCurioChange(wearer);
            reconcileProviderA(wearer);
            reconcileProviderB(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 2,
                    "Two independent providers did not additively grant two slots");

            skillHandler.getStacks().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            skillHandler.getStacks().setStackInSlot(1, new ItemStack(Items.RABBIT_FOOT, 1));

            int rabbitFootBefore = wearer.getInventory().countItem(Items.RABBIT_FOOT);

            // Removing only provider B must shrink by exactly one slot and
            // return only the highest-indexed (slot 1) item.
            handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            reconcileProviderB(wearer);

            helper.assertTrue(skillHandler.getStacks().getSlots() == 1,
                    "Removing one provider did not shrink the slot count by exactly one");
            helper.assertTrue(
                    skillHandler.getStacks().getStackInSlot(0).is(Items.IRON_INGOT),
                    "Removing provider B disturbed the remaining slot's (index 0) contents");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.RABBIT_FOOT)
                            == rabbitFootBefore + 1,
                    "The Rabbit's Foot from the vacated top slot was not returned");

            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            reconcileProviderA(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == 0,
                    "Removing the remaining provider left a residual slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    private static void place(ICurioStacksHandler skillHandler, ItemStack stack, ServerPlayer wearer) {
        skillHandler.getStacks().setStackInSlot(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        settleCurioChange(wearer);
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

    private static void grantOneSlot(ServerPlayer wearer) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(wearer).resolve().orElse(null);
        if (inventory == null) {
            return;
        }
        inventory.getStacksHandler(CurioSlotIds.TOKEN)
                .ifPresent(handler -> handler.getStacks().setStackInSlot(
                        0, new ItemStack(Items.STICK)));
        settleCurioChange(wearer);
        reconcileProviderA(wearer);
    }

    private static void reconcileProviderA(ServerPlayer player) {
        DynamicCurioSlotContributionService.reconcile(
                player,
                CurioSlotIds.ENCYCLOPEDIA_SKILL,
                PROVIDER_A_MODIFIER_ID,
                PROVIDER_A_MODIFIER_NAME,
                stickSupplier(),
                PROVIDER_A_WORN_SLOTS,
                1.0D);
    }

    private static void reconcileProviderB(ServerPlayer player) {
        DynamicCurioSlotContributionService.reconcile(
                player,
                CurioSlotIds.ENCYCLOPEDIA_SKILL,
                PROVIDER_B_MODIFIER_ID,
                PROVIDER_B_MODIFIER_NAME,
                stickSupplier(),
                PROVIDER_B_WORN_SLOTS,
                1.0D);
    }

    private static java.util.function.Supplier<Item> stickSupplier() {
        return () -> Items.STICK;
    }

    private static void fillInventory(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, new ItemStack(Items.DIRT, 64));
        }
    }

    private static ICurioStacksHandler handler(
            ServerPlayer player, String slot, GameTestHelper helper) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(slot))
                .orElse(null);
        helper.assertTrue(handler != null, "Missing Curios handler: " + slot);
        return handler;
    }

    private static void settleCurioChange(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    /**
     * Each test uses its own {@code x} offset so concurrently-running tests
     * within this batch never share a position - important for the two
     * tests that assert on nearby dropped {@code ItemEntity} instances.
     */
    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 0.0D);
        return player;
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
