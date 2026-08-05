package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Set;
import java.util.UUID;

/**
 * Exercises the Encyclopedia item's use of {@link EncyclopediaService} /
 * {@link com.casper.goetyarkham.curios.EncyclopediaSkillSlotContributionService}
 * to grant {@link CurioSlotIds#ENCYCLOPEDIA_SKILL} slot capacity, mirroring
 * {@link BookOfShadowsGameTests}' coverage of the equivalent focus-slot
 * mechanism. It deliberately never re-tests the encyclopedia_skill slot's
 * own content restrictions or stat bonuses - those already have dedicated
 * coverage in {@code EncyclopediaSkillGameTests} and are not re-implemented
 * by this item.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EncyclopediaGameTests {
    /**
     * Isolates this suite from {@code defaultBatch}'s concurrently running
     * tests, matching the established fix for the pre-existing {@code
     * defaultBatch} crash landmine (see {@code EncyclopediaSkillGameTests}).
     */
    private static final String ENCYCLOPEDIA_TEST_BATCH = "goetyarkham:encyclopedia";

    private EncyclopediaGameTests() {
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void encyclopediaGrantsSkillSlotInHandsSlot(GameTestHelper helper) {
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
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);
            int baseSkillSlots = skillHandler.getStacks().getSlots();
            helper.assertTrue(baseSkillSlots == 0,
                    "encyclopedia_skill did not start at base size 0");

            handsHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots + 1,
                    "Equipping in hands did not add exactly one encyclopedia_skill slot");

            // Repeated reconciliation (login/respawn/dimension-change stand-in)
            // must never duplicate the slot.
            EncyclopediaService.reconcile(wearer);
            EncyclopediaService.reconcile(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots + 1,
                    "Repeated reconciliation duplicated the encyclopedia_skill slot (hands)");

            handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots,
                    "Unequipping from hands did not remove the granted encyclopedia_skill slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = ENCYCLOPEDIA_TEST_BATCH)
    public static void encyclopediaGrantsSkillSlotInBookSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "encyclopedia-book", 1.5D);
        try {
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(wearer)
                    .resolve().orElse(null);
            helper.assertTrue(inventory != null,
                    "Missing Curios inventory for book slot test");
            ICurioStacksHandler bookHandler = handler(wearer, CurioSlotIds.BOOK, helper);
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);
            int baseSkillSlots = skillHandler.getStacks().getSlots();

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
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots + 1,
                    "Equipping in the book slot did not add exactly one encyclopedia_skill slot");

            bookHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots,
                    "Unequipping from the book slot did not remove the granted"
                            + " encyclopedia_skill slot");

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
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);
            int baseSkillSlots = skillHandler.getStacks().getSlots();
            ItemStack stack = new ItemStack(ModItems.ENCYCLOPEDIA.get());

            handsHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots + 1,
                    "Equipping in hands did not add the encyclopedia_skill slot"
                            + " before the move");

            // Move the same stack from hands to book in one settle window -
            // must never briefly show two slots or drop to zero.
            handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            bookHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots + 1,
                    "Moving the Encyclopedia between slots changed the"
                            + " encyclopedia_skill slot count");

            bookHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots,
                    "Unequipping after the move left a residual encyclopedia_skill slot");

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
            ICurioStacksHandler skillHandler =
                    handler(wearer, CurioSlotIds.ENCYCLOPEDIA_SKILL, helper);
            int baseSkillSlots = skillHandler.getStacks().getSlots();
            ItemStack stack = new ItemStack(ModItems.ENCYCLOPEDIA.get());

            for (int i = 0; i < 3; i++) {
                handsHandler.getStacks().setStackInSlot(0, stack.copy());
                settleCurioChange(wearer);
                handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                settleCurioChange(wearer);
            }
            helper.assertTrue(skillHandler.getStacks().getSlots() == baseSkillSlots,
                    "Repeated equip/unequip cycles left a residual encyclopedia_skill slot");

            helper.succeed();
        } finally {
            wearer.discard();
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
