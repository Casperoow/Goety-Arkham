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
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.UUID;

/**
 * Note: unlike {@link ArcaneInitiatesTokenGameTests}, this suite's {@link
 * TestPlayer} is deliberately never added to {@code level.players()} - Curios
 * reads/writes work fine without it, and adding an untracked stand-in there
 * risks a {@code ChunkMap} broadcast NPE on unrelated nearby world activity.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BookOfShadowsGameTests {
    private BookOfShadowsGameTests() {
    }

    @GameTest(template = "empty")
    public static void bookOfShadowsGrantsFocusSlotInHandsSlot(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "book_of_shadows");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.BOOK_OF_SHADOWS.get())),
                "Book of Shadows registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.BOOK_OF_SHADOWS.get());
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Book of Shadows must not stack");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(
                        java.util.Set.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK)),
                "Book of Shadows item tag must expose exactly the hands and book slots");

        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "book-of-shadows-hands", 1.5D);
        try {
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler focusHandler = handler(wearer, CurioSlotIds.FOCUS, helper);
            int baseFocusSlots = focusHandler.getStacks().getSlots();

            handsHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Equipping in hands did not add exactly one focus slot");

            // Repeated reconciliation (login/respawn/dimension-change stand-in)
            // must never duplicate the slot.
            BookOfShadowsService.reconcile(wearer);
            BookOfShadowsService.reconcile(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Repeated reconciliation duplicated the focus slot (hands)");

            handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Unequipping from hands did not remove the granted focus slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void bookOfShadowsGrantsFocusSlotInBookSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "book-of-shadows-book", 1.5D);
        try {
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(wearer)
                    .resolve().orElse(null);
            helper.assertTrue(inventory != null,
                    "Missing Curios inventory for book slot test");
            ICurioStacksHandler bookHandler = handler(wearer, CurioSlotIds.BOOK, helper);
            ICurioStacksHandler focusHandler = handler(wearer, CurioSlotIds.FOCUS, helper);
            int baseFocusSlots = focusHandler.getStacks().getSlots();

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
                    0, new ItemStack(ModItems.BOOK_OF_SHADOWS.get()));
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Equipping in the book slot did not add exactly one focus slot");

            bookHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Unequipping from the book slot did not remove the granted focus slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void bookOfShadowsSafelyRecallsFocusOnUnequip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "book-of-shadows-recall", 1.5D);
        try {
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler focusHandler = handler(wearer, CurioSlotIds.FOCUS, helper);
            int baseFocusSlots = focusHandler.getStacks().getSlots();

            handsHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.BOOK_OF_SHADOWS.get()));
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Equipping did not add the extra focus slot for the recall test");

            // The extra slot is always the highest-numbered one after a grow.
            int extraSlot = focusHandler.getStacks().getSlots() - 1;
            ItemStack placed = new ItemStack(Items.STICK, 2);
            focusHandler.getStacks().setStackInSlot(extraSlot, placed);
            helper.assertTrue(!focusHandler.getStacks()
                            .getStackInSlot(extraSlot).isEmpty(),
                    "Test setup failed to place an item in the extra focus slot");

            int inventoryBefore = wearer.getInventory().countItem(Items.STICK);
            handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            // The unequip shrink queues the orphaned stack via Curios' own
            // loseInvalidStack/handleInvalidStacks recall path, which only
            // hands it to the wearer's inventory on Curios' own next tick
            // pass; settle twice to cross both boundaries deterministically.
            settleCurioChange(wearer);
            settleCurioChange(wearer);

            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Unequipping did not remove the granted focus slot in the recall test");
            helper.assertTrue(wearer.getInventory().countItem(Items.STICK)
                            == inventoryBefore + 2,
                    "The focus item in the vacated slot was not safely recalled"
                            + " to the wearer's inventory instead of being deleted");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void bookOfShadowsStacksWithArcaneInitiatesToken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "book-of-shadows-stacking", 1.5D);
        try {
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler focusHandler = handler(wearer, CurioSlotIds.FOCUS, helper);
            int baseFocusSlots = focusHandler.getStacks().getSlots();

            handsHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.BOOK_OF_SHADOWS.get()));
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Book of Shadows did not grant its own focus slot");

            tokenHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.ARCANE_INITIATES_TOKEN.get()));
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 2,
                    "Arcane Initiate's Token did not additively stack with"
                            + " the Book of Shadows");

            // Removing only the book must remove only its own +1, leaving
            // the token's slot untouched.
            handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Unequipping the Book of Shadows removed more than its"
                            + " own focus slot");

            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Unequipping the token left a residual focus slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void bookOfShadowsRapidEquipUnequipDoesNotDrift(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "book-of-shadows-rapid", 1.5D);
        try {
            ICurioStacksHandler handsHandler = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler focusHandler = handler(wearer, CurioSlotIds.FOCUS, helper);
            int baseFocusSlots = focusHandler.getStacks().getSlots();
            ItemStack stack = new ItemStack(ModItems.BOOK_OF_SHADOWS.get());

            for (int i = 0; i < 3; i++) {
                handsHandler.getStacks().setStackInSlot(0, stack.copy());
                settleCurioChange(wearer);
                handsHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                settleCurioChange(wearer);
            }
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Repeated equip/unequip cycles left a residual focus slot");

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
