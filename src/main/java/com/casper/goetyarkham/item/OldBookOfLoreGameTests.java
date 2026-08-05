package com.casper.goetyarkham.item;

import com.Polarice3.Goety.utils.SEHelper;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.UUID;

/**
 * Covers {@link OldBookOfLoreService}'s cooldown redirect, exercised through
 * the real production entry point - {@code SEHelper.addCooldown} - so every
 * test also verifies {@code OldBookOfLoreCooldownMixin} is actually wired
 * up, not just that the service logic is correct in isolation.
 *
 * <p>{@link com.Polarice3.Goety.common.items.ModItems#VEXING_FOCUS} stands in
 * for "a focus" throughout: it is a real Goety {@code MagicFocus}, which
 * implements {@code IFocus}, exactly like every actual castable focus.</p>
 *
 * <p>Goety itself only ever calls {@code SEHelper.addCooldown} after a spell
 * cast has already fully succeeded (confirmed by decompiling {@code
 * DarkWand.MagicResults}/{@code canCastTouch}: the call sits inside the
 * post-{@code SpellResult} branch in every case, never on a
 * cancelled/failed/insufficient-resource path). Because this feature hooks
 * that exact call, "cancelled or failed casts must never trigger the
 * redirect" is guaranteed structurally by Goety's own code, not by anything
 * in this addon - there is no separate failure path here to test.</p>
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OldBookOfLoreGameTests {
    private OldBookOfLoreGameTests() {
    }

    private static Item focus() {
        return com.Polarice3.Goety.common.items.ModItems.VEXING_FOCUS.get();
    }

    @GameTest(template = "empty")
    public static void oldBookOfLoreRegistrationAndTags(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "old_book_of_lore");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.OLD_BOOK_OF_LORE.get())),
                "Old Book of Lore registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.OLD_BOOK_OF_LORE.get());
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Old Book of Lore must not stack");
        helper.succeed();
    }

    /** Equipped in {@code hands}, not on cooldown: the focus never cools down. */
    @GameTest(template = "empty")
    public static void redirectsFocusCooldownWhenEquippedInHands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-hands");
        try {
            handler(player, CurioSlotIds.HANDS, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));

            SEHelper.addCooldown(player, focus(), 200);

            helper.assertTrue(
                    !SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "The focus still entered its own cooldown while Old Book of"
                            + " Lore was equipped and off cooldown");
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Old Book of Lore did not enter cooldown in its place");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /** Equipped in {@code book} instead of {@code hands}: same behaviour. */
    @GameTest(template = "empty")
    public static void redirectsFocusCooldownWhenEquippedInBook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-book-slot");
        try {
            ensureBookSlot(player, helper);
            handler(player, CurioSlotIds.BOOK, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));

            SEHelper.addCooldown(player, focus(), 137);

            helper.assertTrue(
                    !SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "The focus still entered its own cooldown while Old Book of"
                            + " Lore was equipped in the book slot");
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Old Book of Lore did not enter cooldown from the book slot");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /**
     * The redirected cooldown must last exactly as long as the value Goety
     * handed to {@code addCooldown} - i.e. the fully modified final duration,
     * whatever it happens to be (here a non-round number standing in for a
     * cooldown-multiplier-adjusted result), never a value this addon
     * recomputes itself.
     */
    @GameTest(template = "empty")
    public static void redirectedCooldownMatchesTheExactFinalDuration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-exact-duration");
        try {
            handler(player, CurioSlotIds.HANDS, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));

            int duration = 47;
            SEHelper.addCooldown(player, focus(), duration);

            for (int tick = 0; tick < duration - 1; tick++) {
                player.getCooldowns().tick();
            }
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Old Book of Lore's cooldown expired before the focus's"
                            + " final computed duration elapsed");
            player.getCooldowns().tick();
            helper.assertTrue(
                    !player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Old Book of Lore's cooldown outlasted the focus's final"
                            + " computed duration");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /** A cooldown of 0 ticks must never start a cooldown on either item. */
    @GameTest(template = "empty")
    public static void zeroDurationNeverTriggersEitherCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-zero-duration");
        try {
            handler(player, CurioSlotIds.HANDS, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));

            SEHelper.addCooldown(player, focus(), 0);

            helper.assertTrue(
                    !SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "A zero-duration cooldown call left the focus on cooldown");
            helper.assertTrue(
                    !player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "A zero-duration cooldown call put Old Book of Lore on cooldown");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /** Not equipped at all: the focus cools down exactly as if the book did not exist. */
    @GameTest(template = "empty")
    public static void focusCooldownIsUnaffectedWhenNotEquipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-unequipped");
        try {
            SEHelper.addCooldown(player, focus(), 200);

            helper.assertTrue(
                    SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "The focus did not enter its normal cooldown while Old Book"
                            + " of Lore was not equipped");
            helper.assertTrue(
                    !player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "An unequipped Old Book of Lore still entered cooldown");

            // Main inventory only (not a Curios slot) must not count as equipped either.
            player.getInventory().add(new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));
            SEHelper.addCooldown(player, focus(), 200);
            helper.assertTrue(
                    !player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Old Book of Lore sitting in the main inventory triggered the redirect");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /**
     * While Old Book of Lore is itself on cooldown, the redirect must not
     * fire - the focus takes its normal cooldown instead, and the book's own
     * remaining cooldown must not be refreshed by the attempt.
     */
    @GameTest(template = "empty")
    public static void noRedirectWhileOldBookOfLoreIsOnCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-self-cooldown");
        try {
            handler(player, CurioSlotIds.HANDS, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));

            SEHelper.addCooldown(player, focus(), 200);
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "First redirect did not start Old Book of Lore's cooldown");

            player.getCooldowns().tick();
            player.getCooldowns().tick();
            float percentAfterPartialDecay = player.getCooldowns()
                    .getCooldownPercent(ModItems.OLD_BOOK_OF_LORE.get(), 0.0F);

            // A second focus cast attempted while the book is still cooling down.
            SEHelper.addCooldown(player, focus(), 200);
            helper.assertTrue(
                    SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "The focus did not enter its normal cooldown while Old Book"
                            + " of Lore was itself on cooldown");
            helper.assertTrue(
                    Math.abs(player.getCooldowns().getCooldownPercent(
                            ModItems.OLD_BOOK_OF_LORE.get(), 0.0F) - percentAfterPartialDecay)
                            < 0.001F,
                    "A blocked redirect attempt refreshed Old Book of Lore's own cooldown");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /** Once the book's cooldown fully expires, the redirect can fire again. */
    @GameTest(template = "empty")
    public static void redirectResumesAfterOldBookOfLoreCooldownExpires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-cooldown-expiry");
        try {
            handler(player, CurioSlotIds.HANDS, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));

            SEHelper.addCooldown(player, focus(), 10);
            for (int tick = 0; tick < 10; tick++) {
                player.getCooldowns().tick();
            }
            helper.assertTrue(
                    !player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Old Book of Lore's cooldown did not fully expire");

            SEHelper.addCooldown(player, focus(), 50);
            helper.assertTrue(
                    !SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "The redirect did not resume once Old Book of Lore's"
                            + " cooldown had fully expired");
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Old Book of Lore did not re-enter cooldown on the next cast");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /**
     * Two copies worn simultaneously (hands and book) must still be treated
     * as a single instance of the effect: one redirect per cast, and both
     * copies share the same underlying vanilla item cooldown (keyed by item
     * type), so wearing a second copy can never bypass the first copy's
     * cooldown.
     */
    @GameTest(template = "empty")
    public static void multipleWornCopiesShareOneCooldownAndNeverStack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-multiple-copies");
        try {
            ensureBookSlot(player, helper);
            handler(player, CurioSlotIds.HANDS, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));
            handler(player, CurioSlotIds.BOOK, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));

            SEHelper.addCooldown(player, focus(), 200);
            helper.assertTrue(
                    !SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "Wearing two copies did not redirect the first cast");
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Wearing two copies did not start the shared cooldown");

            // Removing the hands copy (one of two owned copies) must not
            // reset or bypass the shared cooldown still tracked by item type.
            handler(player, CurioSlotIds.HANDS, helper)
                    .getStacks().setStackInSlot(0, ItemStack.EMPTY);

            SEHelper.addCooldown(player, focus(), 200);
            helper.assertTrue(
                    SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "A second copy allowed the redirect to fire again while the"
                            + " shared cooldown from the first copy was still active");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /**
     * Unequipping Old Book of Lore mid-cooldown must not pause, reset, or
     * clear its cooldown; re-equipping while still on cooldown must not let
     * the redirect fire, but re-equipping after it naturally expires must.
     */
    @GameTest(template = "empty")
    public static void cooldownSurvivesUnequipAndReequip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "old-book-lore-unequip-reequip");
        try {
            ItemStack stack = new ItemStack(ModItems.OLD_BOOK_OF_LORE.get());
            ICurioStacksHandler hands = handler(player, CurioSlotIds.HANDS, helper);
            hands.getStacks().setStackInSlot(0, stack);

            SEHelper.addCooldown(player, focus(), 20);
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Redirect did not start the cooldown before unequipping");

            // Fully unequip (remove from every Curios slot) while the
            // cooldown is still ticking down.
            hands.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            player.getCooldowns().tick();
            player.getCooldowns().tick();
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Unequipping cleared or paused Old Book of Lore's cooldown");

            // Re-equip while the cooldown is still active: must not trigger.
            // The focus takes its normal Goety cooldown here instead.
            hands.getStacks().setStackInSlot(0, stack);
            SEHelper.addCooldown(player, focus(), 20);
            helper.assertTrue(
                    SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "Re-equipping while still on cooldown let the redirect fire");

            // Let both the book's remaining cooldown and the focus's own
            // just-started real cooldown from the previous step run out.
            for (int tick = 0; tick < 20; tick++) {
                player.getCooldowns().tick();
                SEHelper.getFocusCoolDown(player).tick(player, level);
            }
            helper.assertTrue(
                    !player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "Cooldown never fully expired after re-equipping");
            SEHelper.addCooldown(player, focus(), 20);
            helper.assertTrue(
                    !SEHelper.isOnCooldown(player, new ItemStack(focus())),
                    "Re-equipping after the cooldown expired did not restore the redirect");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /** Each player's redirect and cooldown state must be fully independent. */
    @GameTest(template = "empty")
    public static void eachPlayersCooldownIsIndependent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "old-book-lore-multiplayer-wearer");
        TestPlayer other = testPlayer(level, "old-book-lore-multiplayer-other");
        try {
            handler(wearer, CurioSlotIds.HANDS, helper)
                    .getStacks().setStackInSlot(0, new ItemStack(ModItems.OLD_BOOK_OF_LORE.get()));
            // "other" deliberately never equips Old Book of Lore.

            SEHelper.addCooldown(wearer, focus(), 200);
            SEHelper.addCooldown(other, focus(), 200);

            helper.assertTrue(
                    !SEHelper.isOnCooldown(wearer, new ItemStack(focus()))
                            && wearer.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "The equipped player's cast was not redirected");
            helper.assertTrue(
                    SEHelper.isOnCooldown(other, new ItemStack(focus()))
                            && !other.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get()),
                    "The unequipped player's cast was affected by another"
                            + " player's Old Book of Lore");

            helper.succeed();
        } finally {
            discard(wearer);
            discard(other);
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

    /**
     * The {@code book} slot's base size is 0 (see {@link CurioSlotIds}); it
     * only ever gains capacity from another item's dynamic slot modifier.
     * Tests grant one slot directly, mirroring {@code
     * MedicalTextsGameTests.ensureBookSlot}.
     */
    private static void ensureBookSlot(ServerPlayer player, GameTestHelper helper) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player)
                .resolve()
                .orElse(null);
        helper.assertTrue(inventory != null, "Missing Curios inventory");
        inventory.addPermanentSlotModifier(
                CurioSlotIds.BOOK,
                UUID.randomUUID(),
                "goetyarkham:old_book_of_lore_test_book_slot",
                1.0D,
                AttributeModifier.Operation.ADDITION);
        // getSlots() applies a pending Curios resize synchronously.
        inventory.getStacksHandler(CurioSlotIds.BOOK)
                .ifPresent(ICurioStacksHandler::getSlots);
    }

    /**
     * Deliberately never registered in {@code level.players()} - see {@code
     * MedicalTextsGameTests.TestPlayer}'s documentation for why a
     * connectionless stand-in left in that list can crash the server.
     */
    private static TestPlayer testPlayer(ServerLevel level, String name) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(0.0D, 1.0D, 0.0D);
        return player;
    }

    private static void discard(TestPlayer player) {
        player.discard();
    }

    /**
     * Extends Forge's {@link FakePlayer} rather than a raw {@code
     * ServerPlayer}: unlike the connectionless stand-ins used elsewhere in
     * this addon's other GameTests, these tests deliberately exercise
     * scenarios where the redirect does *not* fire and Goety's own real
     * {@code FocusCooldown.addCooldown} runs - which unconditionally sends a
     * network packet ({@code ModNetwork.sendTo}) - so a connection that
     * safely no-ops sent packets is required. {@link FakePlayer} wires
     * exactly that (a dummy {@code Connection} plus a no-op packet
     * listener), which also makes vanilla's own cooldown-sync packets (used
     * by {@code player.getCooldowns()} for Old Book of Lore's own cooldown)
     * safe without any extra overrides.
     */
    private static final class TestPlayer extends FakePlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
        }
    }
}
