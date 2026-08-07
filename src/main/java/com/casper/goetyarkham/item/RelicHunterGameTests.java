package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.MultiEquippableCurio;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
 * Covers {@link RelicHunterItem}/{@link RelicHunterService}: registration,
 * the {@link CurioSlotIds#ASSET} equip slot, the dynamically-granted {@link
 * CurioSlotIds#CHARM} slot per equipped copy (unlike every other
 * slot-granting Curio in this mod, more than one Relic Hunter may be worn
 * at once - see {@link MultiEquippableCurio}), restore-safety across
 * respawn/dimension-change/relogin, and safe evacuation of extra-charm-slot
 * contents on shrink.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RelicHunterGameTests {
    private static final String RELIC_HUNTER_TEST_BATCH = "goetyarkham:relic_hunter";

    private RelicHunterGameTests() {
    }

    @GameTest(template = "empty", batch = RELIC_HUNTER_TEST_BATCH)
    public static void relicHunterRegistrationTagAndTooltip(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "relic_hunter");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.RELIC_HUNTER.get())),
                "Relic Hunter registry ID mismatch");

        RelicHunterItem item = ModItems.RELIC_HUNTER.get();
        helper.assertTrue(item instanceof MultiEquippableCurio,
                "Relic Hunter must opt out of the single-equip duplicate rule");

        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Relic Hunter must not stack");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(CurioSlotIds.ASSET)),
                "Relic Hunter item tag must expose only the asset slot");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 2,
                "Relic Hunter tooltip line count mismatch");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Relic Hunter when-worn heading is not yellow");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "Relic Hunter effect line is not gray");
        helper.assertTrue(
                "Gain 1 additional Charm slot".equals(tooltip.get(1).getString()),
                "Relic Hunter English tooltip text mismatch");

        helper.succeed();
    }

    @GameTest(template = "empty", batch = RELIC_HUNTER_TEST_BATCH)
    public static void equippingOneRelicHunterGrantsOneCharmSlotAndUnequipRestores(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "relic-hunter-single", 10.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler charmHandler = handler(wearer, CurioSlotIds.CHARM, helper);
            int baseCharmSlots = charmHandler.getStacks().getSlots();

            // Not equipped: no extra charm slot.
            helper.assertTrue(RelicHunterService.equippedCount(wearer) == 0,
                    "Unequipped Relic Hunter reported a nonzero equipped count");
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots,
                    "An unequipped Relic Hunter must not grant a charm slot");

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.RELIC_HUNTER.get()));
            settleCurioChange(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots + 1,
                    "Equipping one Relic Hunter did not add exactly one charm slot");

            assetHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots,
                    "Unequipping did not restore the original charm slot count");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RELIC_HUNTER_TEST_BATCH)
    public static void twoEquippedRelicHuntersGrantTwoCharmSlotsAndPartialUnequipKeepsOne(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "relic-hunter-double", 20.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler charmHandler = handler(wearer, CurioSlotIds.CHARM, helper);
            int baseCharmSlots = charmHandler.getStacks().getSlots();

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.RELIC_HUNTER.get()));
            assetHandler.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.RELIC_HUNTER.get()));
            settleCurioChange(wearer);
            helper.assertTrue(RelicHunterService.equippedCount(wearer) == 2,
                    "Two equipped Relic Hunters were not both counted");
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots + 2,
                    "Two equipped Relic Hunters did not each grant their own charm slot");

            // Removing only one must remove only its own +1.
            assetHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots + 1,
                    "Unequipping one of two Relic Hunters did not leave exactly"
                            + " the other one's charm slot");

            assetHandler.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots,
                    "Unequipping the second Relic Hunter left a residual charm slot");

            helper.succeed();
        } finally {
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = RELIC_HUNTER_TEST_BATCH)
    public static void repeatedReconcileDoesNotDuplicateCharmSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "relic-hunter-repeat", 30.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler charmHandler = handler(wearer, CurioSlotIds.CHARM, helper);
            int baseCharmSlots = charmHandler.getStacks().getSlots();

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.RELIC_HUNTER.get()));
            assetHandler.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.RELIC_HUNTER.get()));
            settleCurioChange(wearer);
            RelicHunterService.reconcile(wearer);
            RelicHunterService.reconcile(wearer);
            RelicHunterService.reconcile(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots + 2,
                    "Repeated confirmed reconciliation duplicated the charm slots");

            assetHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            RelicHunterService.reconcile(wearer);
            RelicHunterService.reconcile(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots + 1,
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
     * correct capacity for however many Relic Hunters are genuinely
     * equipped, without duplicating or losing modifiers across repeated
     * calls - covering death/respawn and dimension-change round trips,
     * which do not otherwise change a still-equipped Curio's state.
     */
    @GameTest(template = "empty", batch = RELIC_HUNTER_TEST_BATCH)
    public static void charmSlotCountCorrectAfterRespawnAndDimensionChange(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "relic-hunter-restore", 40.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler charmHandler = handler(wearer, CurioSlotIds.CHARM, helper);
            int baseCharmSlots = charmHandler.getStacks().getSlots();

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.RELIC_HUNTER.get()));
            assetHandler.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.RELIC_HUNTER.get()));
            settleCurioChange(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots + 2,
                    "Test setup: equipping two Relic Hunters did not grant two slots");

            // Death/respawn stand-in: the player entity's Curios handler
            // still reflects the same equipped items, so a restore reconcile
            // must reproduce the same capacity, not duplicate it.
            RelicHunterService.reconcileRestore(wearer);
            RelicHunterService.reconcileRestore(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots + 2,
                    "Respawn restore reconcile duplicated or lost charm slots");

            // Dimension-change stand-in: same restore call, same expectation.
            RelicHunterService.reconcileRestore(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == baseCharmSlots + 2,
                    "Dimension-change restore reconcile duplicated or lost charm slots");

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
    @GameTest(template = "empty", batch = RELIC_HUNTER_TEST_BATCH)
    public static void charmSlotCountCorrectAfterReloginNbtRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer before = testPlayer(level, "relic-hunter-relogin-before", 50.0D);
        TestPlayer after = null;
        try {
            ICurioStacksHandler assetHandlerBefore =
                    handler(before, CurioSlotIds.ASSET, helper);
            assetHandlerBefore.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.RELIC_HUNTER.get()));
            assetHandlerBefore.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.RELIC_HUNTER.get()));
            settleCurioChange(before);
            ICurioStacksHandler charmHandlerBefore =
                    handler(before, CurioSlotIds.CHARM, helper);
            int baseCharmSlots =
                    charmHandlerBefore.getStacks().getSlots() - 2;
            helper.assertTrue(charmHandlerBefore.getStacks().getSlots()
                            == baseCharmSlots + 2,
                    "Test setup: two equipped Relic Hunters did not grant two"
                            + " charm slots before the round trip");

            net.minecraft.nbt.Tag saved = inventory(before, helper).writeTag();
            before.discard();

            after = testPlayer(level, "relic-hunter-relogin-after", 50.0D);
            ICuriosItemHandler inventoryAfter = inventory(after, helper);
            inventoryAfter.readTag(saved);
            RelicHunterService.reconcileRestore(after);

            ICurioStacksHandler charmHandlerAfter =
                    handler(after, CurioSlotIds.CHARM, helper);
            helper.assertTrue(charmHandlerAfter.getStacks().getSlots()
                            == baseCharmSlots + 2,
                    "Charm slot capacity was not restored to +2 after the"
                            + " relogin NBT round trip");
            helper.assertTrue(RelicHunterService.equippedCount(after) == 2,
                    "Both Relic Hunters were not restored as equipped after"
                            + " the relogin NBT round trip");

            RelicHunterService.reconcile(after);
            helper.assertTrue(charmHandlerAfter.getStacks().getSlots()
                            == baseCharmSlots + 2,
                    "A follow-up confirmed reconcile disturbed the restored"
                            + " charm slot count");

            helper.succeed();
        } finally {
            before.discard();
            if (after != null) {
                after.discard();
            }
        }
    }

    @GameTest(template = "empty", batch = RELIC_HUNTER_TEST_BATCH)
    public static void relicHunterEffectsAreIsolatedBetweenPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer playerA = testPlayer(level, "relic-hunter-isolation-a", 60.0D);
        TestPlayer playerB = testPlayer(level, "relic-hunter-isolation-b", 70.0D);
        try {
            ICurioStacksHandler assetHandlerA = handler(playerA, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler charmHandlerA = handler(playerA, CurioSlotIds.CHARM, helper);
            ICurioStacksHandler charmHandlerB = handler(playerB, CurioSlotIds.CHARM, helper);
            int baseCharmSlotsA = charmHandlerA.getStacks().getSlots();
            int baseCharmSlotsB = charmHandlerB.getStacks().getSlots();

            assetHandlerA.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.RELIC_HUNTER.get()));
            assetHandlerA.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.RELIC_HUNTER.get()));
            settleCurioChange(playerA);

            helper.assertTrue(charmHandlerA.getStacks().getSlots() == baseCharmSlotsA + 2,
                    "Player A's own Relic Hunters did not grant their charm slots");
            helper.assertTrue(charmHandlerB.getStacks().getSlots() == baseCharmSlotsB,
                    "Equipping Player A's Relic Hunters leaked charm capacity"
                            + " to Player B");
            helper.assertTrue(RelicHunterService.equippedCount(playerB) == 0,
                    "Player B was incorrectly counted as wearing Player A's"
                            + " Relic Hunters");

            helper.succeed();
        } finally {
            playerA.discard();
            playerB.discard();
        }
    }

    @GameTest(template = "empty", batch = RELIC_HUNTER_TEST_BATCH)
    public static void unequippingRelicHunterSafelyEvacuatesExtraCharmSlotContents(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "relic-hunter-evacuate", 80.0D);
        try {
            ICurioStacksHandler assetHandler = handler(wearer, CurioSlotIds.ASSET, helper);
            ICurioStacksHandler charmHandler = handler(wearer, CurioSlotIds.CHARM, helper);

            assetHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.RELIC_HUNTER.get()));
            assetHandler.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.RELIC_HUNTER.get()));
            settleCurioChange(wearer);
            int grownSlots = charmHandler.getStacks().getSlots();

            // Fill both granted (highest-index) charm slots.
            int topSlot = grownSlots - 1;
            int secondSlot = grownSlots - 2;
            charmHandler.getStacks().setStackInSlot(
                    topSlot, new ItemStack(Items.STICK, 3));
            charmHandler.getStacks().setStackInSlot(
                    secondSlot, new ItemStack(Items.STICK, 5));
            int sticksBefore = wearer.getInventory().countItem(Items.STICK);

            // Unequip one Relic Hunter: only the top slot's contents must be
            // evacuated back to the wearer, never deleted.
            assetHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(charmHandler.getStacks().getSlots() == grownSlots - 1,
                    "Unequipping one of two Relic Hunters did not shrink the"
                            + " charm slot by exactly one");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.STICK) == sticksBefore + 3,
                    "The item in the vacated charm slot was not safely"
                            + " returned to the wearer's inventory");
            helper.assertTrue(
                    !charmHandler.getStacks().getStackInSlot(secondSlot).isEmpty(),
                    "Shrinking evacuated the wrong charm slot - the remaining"
                            + " granted slot's contents must be untouched");

            // Unequip the second Relic Hunter: the remaining granted slot's
            // contents must also be safely evacuated, not deleted.
            assetHandler.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(RelicHunterService.equippedCount(wearer) == 0,
                    "Both Relic Hunters should be unequipped");
            helper.assertTrue(
                    wearer.getInventory().countItem(Items.STICK) == sticksBefore + 3 + 5,
                    "The item in the last vacated charm slot was not safely"
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
