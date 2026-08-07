package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.MultiEquippableCurio;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
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
import java.util.Set;
import java.util.UUID;

/**
 * Covers {@link CharismaItem}/{@link CharismaService}: registration, the
 * {@link CurioSlotIds#ASSET} equip slot, the dynamically-granted {@link
 * CurioSlotIds#TOKEN} slot per equipped copy (more than one Charisma may be
 * worn at once - see {@link MultiEquippableCurio}), restore-safety across
 * respawn/dimension-change/relogin, and safe evacuation of extra-token-slot
 * contents on shrink. Mirrors {@link RelicHunterGameTests}, which covers the
 * same mechanism targeting the {@code charm} slot instead.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CharismaGameTests {
    private static final String CHARISMA_TEST_BATCH = "goetyarkham:charisma";

    private CharismaGameTests() {
    }

    @GameTest(template = "empty", batch = CHARISMA_TEST_BATCH)
    public static void charismaRegistrationTagAndTooltip(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "charisma");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.CHARISMA.get())),
                "Charisma registry ID mismatch");

        CharismaItem item = ModItems.CHARISMA.get();
        helper.assertTrue(item instanceof MultiEquippableCurio,
                "Charisma must opt out of the single-equip duplicate rule");

        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Charisma must not stack");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(CurioSlotIds.ASSET)),
                "Charisma item tag must expose only the asset slot");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 2,
                "Charisma tooltip line count mismatch");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Charisma when-worn heading is not yellow");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "Charisma effect line is not gray");
        helper.assertTrue(
                "Gain 1 additional Token slot".equals(tooltip.get(1).getString()),
                "Charisma English tooltip text mismatch");

        helper.succeed();
    }

    @GameTest(template = "empty", batch = CHARISMA_TEST_BATCH)
    public static void equippingOneCharismaGrantsOneTokenSlotAndUnequipRestores(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "charisma-single", 110.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            int baseTokenSlots = tokenHandler.getStacks().getSlots();

            // Not equipped: no extra token slot.
            helper.assertTrue(CharismaService.equippedCount(wearer) == 0,
                    "Unequipped Charisma reported a nonzero equipped count");
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots,
                    "An unequipped Charisma must not grant a token slot");

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.CHARISMA.get()));
            settleCurioChange(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots + 1,
                    "Equipping one Charisma did not add exactly one token slot");

            assetHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots,
                    "Unequipping did not restore the original token slot count");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = CHARISMA_TEST_BATCH)
    public static void twoEquippedCharismasGrantTwoTokenSlotsAndPartialUnequipKeepsOne(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "charisma-double", 120.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            int baseTokenSlots = tokenHandler.getStacks().getSlots();

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.CHARISMA.get()));
            assetHandler.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.CHARISMA.get()));
            settleCurioChange(wearer);
            helper.assertTrue(CharismaService.equippedCount(wearer) == 2,
                    "Two equipped Charismas were not both counted");
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots + 2,
                    "Two equipped Charismas did not each grant their own token slot");

            // Removing only one must remove only its own +1.
            assetHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots + 1,
                    "Unequipping one of two Charismas did not leave exactly"
                            + " the other one's token slot");

            assetHandler.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots,
                    "Unequipping the second Charisma left a residual token slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = CHARISMA_TEST_BATCH)
    public static void repeatedReconcileDoesNotDuplicateTokenSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "charisma-repeat", 130.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            int baseTokenSlots = tokenHandler.getStacks().getSlots();

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.CHARISMA.get()));
            assetHandler.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.CHARISMA.get()));
            settleCurioChange(wearer);
            CharismaService.reconcile(wearer);
            CharismaService.reconcile(wearer);
            CharismaService.reconcile(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots + 2,
                    "Repeated confirmed reconciliation duplicated the token slots");

            assetHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            CharismaService.reconcile(wearer);
            CharismaService.reconcile(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots + 1,
                    "Repeated reconciliation after a partial unequip drifted the count");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Simulates the login/respawn/dimension-change restore path directly
     * (the same call {@code CuriosForgeEvents#livingTick} makes once a
     * queued restore is drained): restore reconcile must reproduce the
     * correct capacity for however many Charismas are genuinely equipped,
     * without duplicating or losing modifiers across repeated calls -
     * covering death/respawn and dimension-change round trips, which do not
     * otherwise change a still-equipped Curio's state.
     */
    @GameTest(template = "empty", batch = CHARISMA_TEST_BATCH)
    public static void tokenSlotCountCorrectAfterRespawnAndDimensionChange(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "charisma-restore", 140.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);
            int baseTokenSlots = tokenHandler.getStacks().getSlots();

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.CHARISMA.get()));
            assetHandler.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.CHARISMA.get()));
            settleCurioChange(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots + 2,
                    "Test setup: equipping two Charismas did not grant two slots");

            // Death/respawn stand-in: the player entity's Curios handler
            // still reflects the same equipped items, so a restore reconcile
            // must reproduce the same capacity, not duplicate it.
            CharismaService.reconcileRestore(wearer);
            CharismaService.reconcileRestore(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots + 2,
                    "Respawn restore reconcile duplicated or lost token slots");

            // Dimension-change stand-in: same restore call, same expectation.
            CharismaService.reconcileRestore(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == baseTokenSlots + 2,
                    "Dimension-change restore reconcile duplicated or lost token slots");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    /**
     * Real {@code ICuriosItemHandler#writeTag}/{@code #readTag} round trip
     * through a freshly-constructed player entity, mirroring how Curios
     * itself restores equipped Curios (and their granted slot modifiers)
     * across a relogin or a saved-player-data reload.
     */
    @GameTest(template = "empty", batch = CHARISMA_TEST_BATCH)
    public static void tokenSlotCountCorrectAfterReloginNbtRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer before = testPlayer(level, "charisma-relogin-before", 150.0D);
        TestPlayer after = null;
        try {
            ICurioStacksHandler assetHandlerBefore =
                    handler(before, CurioSlotIds.ASSET, helper);
            assetHandlerBefore.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.CHARISMA.get()));
            assetHandlerBefore.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.CHARISMA.get()));
            settleCurioChange(before);
            ICurioStacksHandler tokenHandlerBefore =
                    handler(before, CurioSlotIds.TOKEN, helper);
            int baseTokenSlots =
                    tokenHandlerBefore.getStacks().getSlots() - 2;
            helper.assertTrue(tokenHandlerBefore.getStacks().getSlots()
                            == baseTokenSlots + 2,
                    "Test setup: two equipped Charismas did not grant two"
                            + " token slots before the round trip");

            net.minecraft.nbt.Tag saved = inventory(before, helper).writeTag();
            before.discard();

            after = testPlayer(level, "charisma-relogin-after", 150.0D);
            ICuriosItemHandler inventoryAfter = inventory(after, helper);
            inventoryAfter.readTag(saved);
            CharismaService.reconcileRestore(after);

            ICurioStacksHandler tokenHandlerAfter =
                    handler(after, CurioSlotIds.TOKEN, helper);
            helper.assertTrue(tokenHandlerAfter.getStacks().getSlots()
                            == baseTokenSlots + 2,
                    "Token slot capacity was not restored to +2 after the"
                            + " relogin NBT round trip");
            helper.assertTrue(CharismaService.equippedCount(after) == 2,
                    "Both Charismas were not restored as equipped after"
                            + " the relogin NBT round trip");

            CharismaService.reconcile(after);
            helper.assertTrue(tokenHandlerAfter.getStacks().getSlots()
                            == baseTokenSlots + 2,
                    "A follow-up confirmed reconcile disturbed the restored"
                            + " token slot count");

            helper.succeed();
        } finally {
            before.discard();
            if (after != null) {
                after.discard();
            }
        }
    }

    @GameTest(template = "empty", batch = CHARISMA_TEST_BATCH)
    public static void charismaEffectsAreIsolatedBetweenPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer playerA = testPlayer(level, "charisma-isolation-a", 160.0D);
        TestPlayer playerB = testPlayer(level, "charisma-isolation-b", 170.0D);
        try {
            ICurioStacksHandler assetHandlerA = handler(playerA, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler tokenHandlerA = handler(playerA, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler tokenHandlerB = handler(playerB, CurioSlotIds.TOKEN, helper);
            int baseTokenSlotsA = tokenHandlerA.getStacks().getSlots();
            int baseTokenSlotsB = tokenHandlerB.getStacks().getSlots();

            assetHandlerA.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.CHARISMA.get()));
            assetHandlerA.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.CHARISMA.get()));
            settleCurioChange(playerA);

            helper.assertTrue(tokenHandlerA.getStacks().getSlots() == baseTokenSlotsA + 2,
                    "Player A's own Charismas did not grant their token slots");
            helper.assertTrue(tokenHandlerB.getStacks().getSlots() == baseTokenSlotsB,
                    "Equipping Player A's Charismas leaked token capacity"
                            + " to Player B");
            helper.assertTrue(CharismaService.equippedCount(playerB) == 0,
                    "Player B was incorrectly counted as wearing Player A's"
                            + " Charismas");

            helper.succeed();
        } finally {
            playerA.discard();
            playerB.discard();
        }
    }

    @GameTest(template = "empty", batch = CHARISMA_TEST_BATCH)
    public static void unequippingCharismaSafelyEvacuatesExtraTokenSlotContents(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "charisma-evacuate", 180.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler tokenHandler = handler(wearer, CurioSlotIds.TOKEN, helper);

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.CHARISMA.get()));
            assetHandler.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.CHARISMA.get()));
            settleCurioChange(wearer);
            int grownSlots = tokenHandler.getStacks().getSlots();

            // Fill both granted (highest-index) token slots.
            int topSlot = grownSlots - 1;
            int secondSlot = grownSlots - 2;
            tokenHandler.getStacks().setStackInSlot(
                    topSlot, new ItemStack(Items.STICK, 3));
            tokenHandler.getStacks().setStackInSlot(
                    secondSlot, new ItemStack(Items.STICK, 5));
            int sticksBefore = wearer.getInventory().countItem(Items.STICK);

            // Unequip one Charisma: only the top slot's contents must be
            // evacuated back to the wearer, never deleted.
            assetHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(tokenHandler.getStacks().getSlots() == grownSlots - 1,
                    "Unequipping one of two Charismas did not shrink the"
                            + " token slot by exactly one");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.STICK) == sticksBefore + 3,
                    "The item in the vacated token slot was not safely"
                            + " returned to the wearer's inventory");
            helper.assertTrue(
                    !tokenHandler.getStacks().getStackInSlot(secondSlot).isEmpty(),
                    "Shrinking evacuated the wrong token slot - the remaining"
                            + " granted slot's contents must be untouched");

            // Unequip the second Charisma: the remaining granted slot's
            // contents must also be safely evacuated, not deleted.
            assetHandler.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(CharismaService.equippedCount(wearer) == 0,
                    "Both Charismas should be unequipped");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.STICK) == sticksBefore + 3 + 5,
                    "The item in the last vacated token slot was not safely"
                            + " returned to the wearer's inventory");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 0.0D);
        return player;
    }

    private static ICuriosItemHandler inventory(ServerPlayer player, GameTestHelper helper) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player)
                .resolve().orElse(null);
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
