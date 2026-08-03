package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.loneliness.LonelinessService;
import com.casper.goetyarkham.sanity.SanityChangeCause;
import com.casper.goetyarkham.sanity.SanityService;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WendyAmuletGameTests {
    private WendyAmuletGameTests() {
    }

    @GameTest(template = "empty")
    public static void wendysAmuletLifecycleIsPlayerOwnedAndIdempotent(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer first = testPlayer(level, "wendy-first", 1.5D);
        TestPlayer second = testPlayer(level, "wendy-second", 4.5D);
        try {
            ICurioStacksHandler firstWeakness = handler(
                    first, CurioSlotIds.WEAKNESS, helper);
            ICurioStacksHandler secondWeakness = handler(
                    second, CurioSlotIds.WEAKNESS, helper);
            int firstBaseSlots = firstWeakness.getStacks().getSlots();
            int secondBaseSlots = secondWeakness.getStacks().getSlots();

            equipAmulet(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots()
                            == firstBaseSlots + 1,
                    "Wendy's Amulet did not add exactly one weakness slot");
            helper.assertTrue(countBoundWeaknesses(firstWeakness, first) == 1,
                    "Wendy's Amulet did not equip exactly one bound Abandoned and Alone");
            helper.assertTrue(handler(second, CurioSlotIds.WEAKNESS, helper)
                            .getStacks().getSlots() == secondBaseSlots,
                    "First player's amulet changed the second player's slots");

            WendysAmuletService.reconcile(first);
            WendysAmuletService.reconcile(first);
            WendysAmuletService.reconcile(first);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(countBoundWeaknesses(firstWeakness, first) == 1,
                    "Login/dimension/sync reconciliation duplicated Abandoned and Alone");
            helper.assertTrue(firstWeakness.getStacks().getSlots()
                            == firstBaseSlots + 1,
                    "Repeated reconciliation duplicated the weakness slot");

            assertManualRemovalLocked(helper, first, firstWeakness);

            equipAmulet(second, helper);
            secondWeakness = handler(second, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(secondWeakness.getStacks().getSlots()
                            == secondBaseSlots + 1
                            && countBoundWeaknesses(secondWeakness, second) == 1,
                    "Second wearer did not receive its own bound weakness");

            LonelinessService.addLoneliness(first);
            LonelinessService.addLoneliness(first);
            helper.assertTrue(LonelinessService.getLoneliness(first) == 2,
                    "Test setup did not raise Loneliness to two");

            int inventoryBefore = first.getInventory().countItem(
                    ModItems.ABANDONED_AND_ALONE.get());
            unequipAmulet(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots()
                            == firstBaseSlots,
                    "Removing the amulet did not remove its weakness slot");
            helper.assertTrue(countBoundWeaknesses(firstWeakness, first) == 0,
                    "Removing the amulet did not directly delete its bound weakness");
            helper.assertTrue(first.getInventory().countItem(
                            ModItems.ABANDONED_AND_ALONE.get()) == inventoryBefore,
                    "Deleted Abandoned and Alone entered the player's inventory");
            helper.assertTrue(level.getEntitiesOfClass(
                            ItemEntity.class,
                            first.getBoundingBox().inflate(16.0D),
                            entity -> entity.getItem().is(
                                    ModItems.ABANDONED_AND_ALONE.get())).isEmpty(),
                    "Deleted Abandoned and Alone spawned an item entity");
            helper.assertTrue(countBoundWeaknesses(
                            handler(second, CurioSlotIds.WEAKNESS, helper),
                            second) == 1,
                    "Removing first amulet deleted second player's bound weakness");
            helper.assertTrue(LonelinessService.getLoneliness(first) == 2,
                    "Unequipping the amulet cleared saved Loneliness");

            equipAmulet(first, helper);
            helper.assertTrue(countBoundWeaknesses(
                            handler(first, CurioSlotIds.WEAKNESS, helper), first) == 1,
                    "Re-equipping did not regenerate the bound weakness");
            helper.assertTrue(LonelinessService.getLoneliness(first) == 2,
                    "Re-equipping the amulet did not keep the previously saved Loneliness");
            helper.succeed();
        } finally {
            unequipAmulet(first, helper);
            unequipAmulet(second, helper);
            first.discard();
            second.discard();
        }
    }

    @GameTest(template = "empty")
    public static void wendysAmuletBlocksNegativeEffectsAndSettlesAtFive(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "wendy-wearer", 1.5D);
        TestPlayer bystander = testPlayer(level, "wendy-bystander", 4.5D);
        try {
            equipAmulet(wearer, helper);
            int startingSanity = resetSanity(wearer);

            applyAndAssertBlocked(helper, wearer, MobEffects.POISON, 1);
            applyAndAssertBlocked(helper, wearer, MobEffects.WITHER, 2);
            applyAndAssertBlocked(helper, wearer, MobEffects.WEAKNESS, 3);
            applyAndAssertBlocked(helper, wearer, MobEffects.MOVEMENT_SLOWDOWN, 4);

            wearer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            helper.assertTrue(wearer.hasEffect(MobEffects.MOVEMENT_SPEED),
                    "A positive effect was incorrectly blocked");
            helper.assertTrue(LonelinessService.getLoneliness(wearer) == 4,
                    "A positive effect changed the Loneliness count");

            helper.assertTrue(SanityService.getCurrentSanity(wearer) == startingSanity,
                    "Sanity changed before the fifth stack settled");
            int maximumBeforeSettle = SanityService.getMaximumSanity(wearer);

            wearer.removeEffect(MobEffects.MOVEMENT_SPEED);
            applyAndAssertBlocked(helper, wearer, MobEffects.CONFUSION, 0);
            helper.assertTrue(SanityService.getCurrentSanity(wearer)
                            == startingSanity - LonelinessService.SANITY_DAMAGE_ON_SETTLE,
                    "Reaching five Loneliness did not remove exactly two sanity");
            helper.assertTrue(SanityService.getMaximumSanity(wearer)
                            == maximumBeforeSettle,
                    "Settlement must not touch the sanity maximum");

            int settledSanity = SanityService.getCurrentSanity(wearer);
            wearer.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));
            helper.assertTrue(!wearer.hasEffect(MobEffects.HUNGER)
                            && LonelinessService.getLoneliness(wearer) == 1
                            && SanityService.getCurrentSanity(wearer) == settledSanity,
                    "Settlement fired more than once for a single crossing");

            int bystanderSanity = resetSanity(bystander);
            bystander.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            helper.assertTrue(bystander.hasEffect(MobEffects.POISON),
                    "A player without the amulet was incorrectly immunized");
            helper.assertTrue(LonelinessService.getLoneliness(bystander) == 0,
                    "A non-wearer accumulated Loneliness");
            helper.assertTrue(SanityService.getCurrentSanity(bystander) == bystanderSanity,
                    "One wearer's settlement damaged another player's sanity");
            helper.succeed();
        } finally {
            unequipAmulet(wearer, helper);
            wearer.discard();
            bystander.discard();
        }
    }

    private static void applyAndAssertBlocked(
            GameTestHelper helper,
            ServerPlayer player,
            net.minecraft.world.effect.MobEffect effect,
            int expectedLoneliness) {
        player.addEffect(new MobEffectInstance(effect, 200, 0));
        helper.assertTrue(!player.hasEffect(effect),
                "Negative effect was not blocked: " + effect);
        helper.assertTrue(LonelinessService.getLoneliness(player) == expectedLoneliness,
                "Loneliness mismatch after blocking " + effect);
    }

    private static void assertManualRemovalLocked(
            GameTestHelper helper,
            TestPlayer player,
            ICurioStacksHandler weaknesses) {
        int slot = boundWeaknessSlot(weaknesses, player);
        helper.assertTrue(slot >= 0, "No bound weakness to lock-test");
        ItemStack stack = weaknesses.getStacks().getStackInSlot(slot);
        SlotContext context = new SlotContext(
                CurioSlotIds.WEAKNESS, player, slot, false, true);
        ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
        helper.assertTrue(curio != null && !curio.canUnequip(context),
                "Abandoned and Alone Curio did not reject manual unequip");
        helper.assertTrue(curio.getDropRule(
                        context, player.damageSources().generic(), 0, false)
                        == ICurio.DropRule.ALWAYS_KEEP,
                "Abandoned and Alone is not protected from death drops");
        helper.assertTrue(weaknesses.getStacks()
                        .extractItem(slot, 1, false).isEmpty(),
                "Survival extraction removed the locked weakness");
        player.getAbilities().instabuild = true;
        helper.assertTrue(weaknesses.getStacks()
                        .extractItem(slot, 1, false).isEmpty(),
                "Creative extraction removed the locked weakness");
        player.getAbilities().instabuild = false;
        helper.assertTrue(countBoundWeaknesses(weaknesses, player) == 1,
                "Lock test changed the bound weakness count");
    }

    private static int resetSanity(ServerPlayer player) {
        int maximum = SanityService.getMaximumSanity(player);
        SanityService.setSanity(player, maximum, SanityChangeCause.COMMAND);
        return SanityService.getCurrentSanity(player);
    }

    private static TestPlayer testPlayer(
            ServerLevel level, String name, double x) {
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
        helper.assertTrue(handler != null,
                "Missing Curios handler: " + slot);
        return handler;
    }

    private static void equipAmulet(
            ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler charm = handler(player, CurioSlotIds.CHARM, helper);
        if (!charm.getStacks().getStackInSlot(0).is(
                ModItems.WENDYS_AMULET.get())) {
            charm.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.WENDYS_AMULET.get()));
            settleCurioChange(player);
        }
    }

    private static void unequipAmulet(
            ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler charm = handler(player, CurioSlotIds.CHARM, helper);
        if (charm.getStacks().getStackInSlot(0).is(
                ModItems.WENDYS_AMULET.get())) {
            charm.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(player);
        }
    }

    private static void settleCurioChange(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static int countBoundWeaknesses(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        int count = 0;
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (AbandonedAndAloneItem.isAmuletBound(
                    weaknesses.getStacks().getStackInSlot(slot),
                    owner.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static int boundWeaknessSlot(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (AbandonedAndAloneItem.isAmuletBound(
                    weaknesses.getStacks().getStackInSlot(slot),
                    owner.getUUID())) {
                return slot;
            }
        }
        return -1;
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        protected void onEffectAdded(
                net.minecraft.world.effect.MobEffectInstance effectInstance,
                net.minecraft.world.entity.Entity source) {
            // Avoid network access for the connection-less GameTest player.
        }

        @Override
        protected void onEffectUpdated(
                net.minecraft.world.effect.MobEffectInstance effectInstance,
                boolean forced,
                net.minecraft.world.entity.Entity source) {
            // Avoid network access for the connection-less GameTest player.
        }

        @Override
        protected void onEffectRemoved(
                net.minecraft.world.effect.MobEffectInstance effectInstance) {
            // Avoid network access for the connection-less GameTest player.
        }
    }
}
