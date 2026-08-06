package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.curios.ResourceSlotProvider;
import com.casper.goetyarkham.curios.ResourceSlotProviderRegistry;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
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
import java.util.Set;
import java.util.UUID;

/**
 * Exercises the Encyclopedia item's use of {@link EncyclopediaService} /
 * {@link EncyclopediaBonusProvider} to guarantee {@link
 * CurioSlotIds#RESOURCE} slot capacity, and the Shift-tooltip "current
 * bonus" / "when equipped" prediction. It deliberately never re-tests the
 * resource slot's own content restrictions or stat bonuses - those already
 * have dedicated coverage in {@code ResourceSlotGameTests} and are not
 * re-implemented by this item.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EncyclopediaGameTests {
    /**
     * Isolates this suite from {@code defaultBatch}'s concurrently running
     * tests, matching the established fix for the pre-existing {@code
     * defaultBatch} crash landmine (see {@code ResourceSlotGameTests}).
     */
    private static final String ENCYCLOPEDIA_TEST_BATCH = "goetyarkham:encyclopedia";

    private EncyclopediaGameTests() {
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void encyclopediaGuaranteesResourceSlotInHandsSlot(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "encyclopedia");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.ENCYCLOPEDIA.get())),
                "Encyclopedia registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.ENCYCLOPEDIA.get());
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Encyclopedia must not stack");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(
                        Set.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK)),
                "Encyclopedia item tag must expose exactly the hands and book slots");

        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-hands", 1.5D);
        try {
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler resourceHandler =
                    handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseResourceSlots = resourceHandler.getStacks().getSlots();
            helper.assertTrue(baseResourceSlots == 0,
                    "resource did not start at base size 0");

            handsHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots + 1,
                    "Equipping in hands did not add exactly one resource slot");

            // Repeated reconciliation (login/respawn/dimension-change stand-in)
            // must never duplicate the slot.
            EncyclopediaService.reconcile(wearer);
            EncyclopediaService.reconcile(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots + 1,
                    "Repeated reconciliation duplicated the resource slot (hands)");

            handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots,
                    "Unequipping from hands did not remove the granted resource slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void encyclopediaGuaranteesResourceSlotInBookSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-book", 1.5D);
        try {
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(wearer)
                    .resolve().orElse(null);
            helper.assertTrue(inventory != null,
                    "Missing Curios inventory for book slot test");
            ICurioStacksHandler bookHandler = handler(wearer, CurioSlotIds.BOOK, helper);
            ICurioStacksHandler resourceHandler =
                    handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseResourceSlots = resourceHandler.getStacks().getSlots();

            // The book slot's base size is 0; it only exists once some
            // other dynamic modifier grants it capacity. Simulate that here
            // with a synthetic capacity modifier so the item can actually be
            // placed into the slot for this test.
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.BOOK, UUID.randomUUID(), "test:book_slot_capacity",
                    1.0D, AttributeModifier.Operation.ADDITION);
            bookHandler.getStacks().getSlots();
            helper.assertTrue(bookHandler.getStacks().getSlots() >= 1,
                    "Test setup's synthetic book-slot capacity modifier did not apply");

            bookHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.ENCYCLOPEDIA.get()));
            settleCurioChange(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots + 1,
                    "Equipping in the book slot did not add exactly one resource slot");

            bookHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots,
                    "Unequipping from the book slot did not remove the granted"
                            + " resource slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void encyclopediaMoveBetweenSlotsDoesNotDuplicateOrLoseTheSlot(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-move", 1.5D);
        try {
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(wearer)
                    .resolve().orElse(null);
            helper.assertTrue(inventory != null,
                    "Missing Curios inventory for move test");
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.BOOK, UUID.randomUUID(), "test:book_slot_capacity",
                    1.0D, AttributeModifier.Operation.ADDITION);

            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler bookHandler = handler(wearer, CurioSlotIds.BOOK, helper);
            ICurioStacksHandler resourceHandler =
                    handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseResourceSlots = resourceHandler.getStacks().getSlots();
            ItemStack stack = new ItemStack(ModItems.ENCYCLOPEDIA.get());

            handsHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots + 1,
                    "Equipping in hands did not add the resource slot"
                            + " before the move");

            handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            bookHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots + 1,
                    "Moving the Encyclopedia between slots changed the"
                            + " resource slot count");

            bookHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots,
                    "Unequipping after the move left a residual resource slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void encyclopediaRapidEquipUnequipDoesNotDrift(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-rapid", 1.5D);
        try {
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler resourceHandler =
                    handler(wearer, CurioSlotIds.RESOURCE, helper);
            int baseResourceSlots = resourceHandler.getStacks().getSlots();
            ItemStack stack = new ItemStack(ModItems.ENCYCLOPEDIA.get());

            for (int i = 0; i < 3; i++) {
                handsHandler.getStacks().setStackInSlot(0, stack.copy());
                settleCurioChange(wearer);
                handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                settleCurioChange(wearer);
            }
            helper.assertTrue(resourceHandler.getStacks().getSlots() == baseResourceSlots,
                    "Repeated equip/unequip cycles left a residual resource slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Covers the Shift-tooltip "current bonus" feature's live computation
     * and its independence from anything except the Encyclopedia's own
     * {@code resource} contribution: single/triple books, mixed items,
     * empty/illegal slot contents, equip-state dedup by item type (not by
     * the specific {@code ItemStack} instance viewed), isolation from other
     * registered {@link ResourceSlotProvider}s, absence of side effects,
     * and a real {@link EncyclopediaItem#appendHoverText} call.
     */
    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void oneBookInResourceSlotGivesPlusTwoIntellectOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-one-book", 3.5D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));

            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 2,
                    "One Book did not grant +2 Intellect");
            helper.assertTrue(bonus.get(StatType.STRENGTH) == 0
                            && bonus.get(StatType.AGILITY) == 0
                            && bonus.get(StatType.WILLPOWER) == 0,
                    "One Book granted a bonus to an unrelated stat");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void threeBooksAcrossThreeSlotsGivePlusSixIntellect(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-three-books", 5.5D);
        CapacityBoostProvider capacityBoost =
                new CapacityBoostProvider("test:encyclopedia_tooltip_capacity_a", 3);
        try {
            ResourceSlotProviderRegistry.register(capacityBoost);
            capacityBoost.equipped = true;
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 3,
                    "Setup: capacity did not reach 3 resource slots");
            for (int slot = 0; slot < 3; slot++) {
                resourceHandler.getStacks().setStackInSlot(slot, new ItemStack(Items.BOOK));
            }

            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 6,
                    "Three Books did not grant +6 Intellect, found "
                            + bonus.get(StatType.INTELLECT));
            helper.assertTrue(bonus.get(StatType.STRENGTH) == 0
                            && bonus.get(StatType.AGILITY) == 0
                            && bonus.get(StatType.WILLPOWER) == 0,
                    "Three Books granted a bonus to an unrelated stat");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(capacityBoost.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void twoBooksAndIronIngotGivePlusFourIntellectPlusTwoStrength(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-mixed-a", 7.5D);
        CapacityBoostProvider capacityBoost =
                new CapacityBoostProvider("test:encyclopedia_tooltip_capacity_b", 3);
        try {
            ResourceSlotProviderRegistry.register(capacityBoost);
            capacityBoost.equipped = true;
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(2, new ItemStack(Items.IRON_INGOT));

            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 4,
                    "Two Books did not grant +4 Intellect, found "
                            + bonus.get(StatType.INTELLECT));
            helper.assertTrue(bonus.get(StatType.STRENGTH) == 2,
                    "One Iron Ingot did not grant +2 Strength, found "
                            + bonus.get(StatType.STRENGTH));
            helper.assertTrue(bonus.get(StatType.AGILITY) == 0
                            && bonus.get(StatType.WILLPOWER) == 0,
                    "Mixed contents granted a bonus to an unrelated stat");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(capacityBoost.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void bookRabbitFootAndEctoplasmGiveOnePlusTwoEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-mixed-b", 9.5D);
        CapacityBoostProvider capacityBoost =
                new CapacityBoostProvider("test:encyclopedia_tooltip_capacity_c", 3);
        try {
            ResourceSlotProviderRegistry.register(capacityBoost);
            capacityBoost.equipped = true;
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            resourceHandler.getStacks().setStackInSlot(1, new ItemStack(Items.RABBIT_FOOT));
            resourceHandler.getStacks().setStackInSlot(
                    2, new ItemStack(com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get()));

            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 2, "Book should grant +2 Intellect");
            helper.assertTrue(bonus.get(StatType.AGILITY) == 2,
                    "Rabbit's Foot should grant +2 Agility");
            helper.assertTrue(bonus.get(StatType.WILLPOWER) == 2,
                    "Ectoplasm should grant +2 Willpower");
            helper.assertTrue(bonus.get(StatType.STRENGTH) == 0,
                    "No Iron Ingot present, Strength must stay 0");

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(capacityBoost.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void emptyAndIllegalSlotContentsGiveNoBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-empty-illegal", 11.5D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Setup: Encyclopedia alone should grant exactly 1 resource slot");

            Map<StatType, Integer> emptyBonus = computeCurrentBonus(wearer);
            helper.assertTrue(allZero(emptyBonus), "An empty slot must contribute no bonus");

            // Bypasses ResourceSlotContentPolicy directly (as an
            // out-of-band write would) to confirm an unrecognized item is
            // simply scored 0, never crashing or contributing.
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.DIRT));
            Map<StatType, Integer> illegalBonus = computeCurrentBonus(wearer);
            helper.assertTrue(allZero(illegalBonus),
                    "An illegal/unrecognized item must contribute no bonus");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void twoEncyclopediasWornDoNotDoubleTheComputedBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-dedup", 13.5D);
        try {
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            // The hands slot's base size is 2, so two Encyclopedias fit
            // without any extra capacity modifier.
            handsHandler.getStacks().setStackInSlot(0, new ItemStack(ModItems.ENCYCLOPEDIA.get()));
            handsHandler.getStacks().setStackInSlot(1, new ItemStack(ModItems.ENCYCLOPEDIA.get()));
            settleCurioChange(wearer);

            helper.assertTrue(EncyclopediaService.isWearing(wearer),
                    "Wearing two Encyclopedias should still report equipped");
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            helper.assertTrue(resourceHandler.getStacks().getSlots() == 1,
                    "Two worn Encyclopedias must not double the resource capacity (max, not sum)");

            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 2,
                    "Wearing two Encyclopedias must not double the Book's own +2 Intellect, found "
                            + bonus.get(StatType.INTELLECT));

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * The equip check must key off item <em>type</em>, not the specific
     * {@code ItemStack} a tooltip happens to be hovering: a second,
     * never-equipped Encyclopedia sitting in the main inventory must not
     * make {@link EncyclopediaService#isWearing} report {@code false} while
     * another copy is genuinely worn.
     */
    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void secondUnequippedEncyclopediaInInventoryStillReportsEquippedByType(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-inventory-copy", 15.5D);
        try {
            equipEncyclopedia(wearer);
            ItemStack unequippedCopy = new ItemStack(ModItems.ENCYCLOPEDIA.get());
            wearer.getInventory().add(unequippedCopy);

            helper.assertTrue(EncyclopediaService.isWearing(wearer),
                    "A worn Encyclopedia must report equipped regardless of an"
                            + " unequipped copy sitting in the inventory");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void otherRegisteredProviderContributionNeverLeaksIntoEncyclopediaBonus(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-isolation", 17.5D);
        BookHoggingProvider bookHogger = new BookHoggingProvider("test:book_hogging_provider");
        try {
            ResourceSlotProviderRegistry.register(bookHogger);
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));

            Map<StatType, Integer> bonus = computeCurrentBonus(wearer);
            helper.assertTrue(bonus.get(StatType.INTELLECT) == 2,
                    "Another registered provider's own (much larger) Book score"
                            + " leaked into the Encyclopedia's own tooltip bonus, found "
                            + bonus.get(StatType.INTELLECT));

            helper.succeed();
        } finally {
            ResourceSlotProviderRegistry.unregister(bookHogger.providerId());
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void computingTheBonusNeverMutatesSlotContentsOrCapacity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-bonus-no-side-effects", 19.5D);
        try {
            equipEncyclopedia(wearer);
            ICurioStacksHandler resourceHandler = handler(wearer, CurioSlotIds.RESOURCE, helper);
            resourceHandler.getStacks().setStackInSlot(0, new ItemStack(Items.BOOK));
            int slotsBefore = resourceHandler.getStacks().getSlots();
            ItemStack contentBefore = resourceHandler.getStacks().getStackInSlot(0).copy();

            for (int i = 0; i < 3; i++) {
                EncyclopediaService.isWearing(wearer);
                computeCurrentBonus(wearer);
            }

            helper.assertTrue(resourceHandler.getStacks().getSlots() == slotsBefore,
                    "Reading the tooltip bonus changed the resource slot capacity");
            ItemStack contentAfter = resourceHandler.getStacks().getStackInSlot(0);
            helper.assertTrue(
                    ItemStack.isSameItemSameTags(contentBefore, contentAfter)
                            && contentBefore.getCount() == contentAfter.getCount(),
                    "Reading the tooltip bonus changed the resource slot contents");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * A real {@code appendHoverText} call, on the server side where {@code
     * ShiftTooltipHelper.isShiftDown()} and {@code
     * EncyclopediaService.currentClientBonus()} both safely resolve to
     * their no-client-player defaults (there is no {@code Dist.CLIENT} in a
     * GameTest server), proving the item never crashes when no local
     * client player is available - covering both the equipped and
     * unequipped Shift-hidden tooltip paths.
     */
    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void appendHoverTextNeverCrashesWithoutAClientPlayer(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.ENCYCLOPEDIA.get());
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(!tooltip.isEmpty(), "Encyclopedia tooltip must not be empty");
        helper.succeed();
    }

    private static Map<StatType, Integer> computeCurrentBonus(ServerPlayer wearer) {
        List<ItemStack> contents = DynamicCurioSlotContributionService.slotContents(
                wearer, CurioSlotIds.RESOURCE);
        return EncyclopediaBonusProvider.INSTANCE.computeBonus(contents);
    }

    private static boolean allZero(Map<StatType, Integer> bonus) {
        return bonus.values().stream().allMatch(value -> value == 0);
    }

    private static void equipEncyclopedia(ServerPlayer wearer) {
        ICurioStacksHandler handsHandler =
                CuriosApi.getCuriosInventory(wearer).resolve()
                        .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.HANDS))
                        .orElseThrow();
        handsHandler.getStacks().setStackInSlot(0, new ItemStack(ModItems.ENCYCLOPEDIA.get()));
        settleCurioChange(wearer);
    }

    private static final class CapacityBoostProvider implements ResourceSlotProvider {
        private final String id;
        private final int slotCount;
        private volatile boolean equipped;

        CapacityBoostProvider(String id, int slotCount) {
            this.id = id;
            this.slotCount = slotCount;
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
        public int statBonus(StatType stat, Item item) {
            return 0;
        }
    }

    /** Deliberately scores Books far higher than the Encyclopedia does, to prove isolation. */
    private static final class BookHoggingProvider implements ResourceSlotProvider {
        private final String id;

        BookHoggingProvider(String id) {
            this.id = id;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public int declaredSlotCount() {
            return 0;
        }

        @Override
        public boolean isEquipped(ServerPlayer player) {
            return true;
        }

        @Override
        public int statBonus(StatType stat, Item item) {
            return stat == StatType.INTELLECT && item == Items.BOOK ? 100 : 0;
        }
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        return player;
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
