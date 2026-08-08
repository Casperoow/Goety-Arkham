package com.casper.goetyarkham.item;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OnTheLamGameTests {
    private OnTheLamGameTests() {
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void equipGrantsTrueInvisibility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-equip", 1.5D);
        try {
            helper.assertTrue(!OnTheLamService.hasTrueInvisibility(wearer),
                    "Unworn player started True Invisible");
            equipOnTheLam(wearer, helper);
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "Equipping On the Lam did not grant True Invisibility");
            helper.assertTrue(wearer.hasEffect(MobEffects.INVISIBILITY),
                    "True Invisibility did not include vanilla Invisibility");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void unequipRemovesTrueInvisibilityWithoutCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-unequip", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "Test setup did not grant True Invisibility");

            unequipOnTheLam(wearer, helper);
            helper.assertTrue(!OnTheLamService.hasTrueInvisibility(wearer),
                    "Unequipping did not immediately remove True Invisibility");
            helper.assertTrue(
                    !wearer.getCooldowns().isOnCooldown(ModItems.ON_THE_LAM.get()),
                    "Plain unequip incorrectly started the cooldown");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void dealingDamageBreaksInvisibility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-deal-damage", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "Test setup did not grant True Invisibility");

            Zombie victim = zombie(level, wearer, 3.0D);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(
                    victim, wearer.damageSources().playerAttack(wearer), 5.0F));
            helper.assertTrue(!OnTheLamService.hasTrueInvisibility(wearer),
                    "Dealing damage did not break True Invisibility");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void takingDamageBreaksInvisibility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-take-damage", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "Test setup did not grant True Invisibility");

            Zombie attacker = zombie(level, wearer, 3.0D);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(
                    wearer, wearer.damageSources().mobAttack(attacker), 3.0F));
            helper.assertTrue(!OnTheLamService.hasTrueInvisibility(wearer),
                    "Taking damage did not break True Invisibility");

            // A fully-zero-amount hit must never break stealth.
            equipOnTheLam(wearer, helper);
            wearer.getCooldowns().removeCooldown(ModItems.ON_THE_LAM.get());
            settleCurioChange(wearer);
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "Test setup did not re-establish True Invisibility");
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(
                    wearer, wearer.damageSources().mobAttack(attacker), 0.0F));
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "A zero-amount hurt event incorrectly broke True Invisibility");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void damageStartsExact200TickCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-cooldown-exact", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            Zombie victim = zombie(level, wearer, 3.0D);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(
                    victim, wearer.damageSources().playerAttack(wearer), 5.0F));
            helper.assertTrue(
                    wearer.getCooldowns().isOnCooldown(ModItems.ON_THE_LAM.get()),
                    "Breaking stealth did not start the cooldown");

            for (int tick = 0; tick < OnTheLamService.COOLDOWN_TICKS - 1; tick++) {
                wearer.getCooldowns().tick();
            }
            helper.assertTrue(
                    wearer.getCooldowns().isOnCooldown(ModItems.ON_THE_LAM.get()),
                    "Cooldown ended before exactly 200 ticks");

            wearer.getCooldowns().tick();
            helper.assertTrue(
                    !wearer.getCooldowns().isOnCooldown(ModItems.ON_THE_LAM.get()),
                    "Cooldown did not end at exactly 200 ticks");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void cooldownPreventsImmediateReactivation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-cooldown-block", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            Zombie victim = zombie(level, wearer, 3.0D);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(
                    victim, wearer.damageSources().playerAttack(wearer), 5.0F));
            helper.assertTrue(!OnTheLamService.hasTrueInvisibility(wearer),
                    "Test setup did not break True Invisibility");

            // Still equipped, still on cooldown: repeated ticks must not
            // reinstate True Invisibility.
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(!OnTheLamService.hasTrueInvisibility(wearer),
                    "True Invisibility returned while still on cooldown");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void invisibilityReturnsAfterCooldownWhileStillEquipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-cooldown-recover", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            Zombie victim = zombie(level, wearer, 3.0D);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(
                    victim, wearer.damageSources().playerAttack(wearer), 5.0F));
            helper.assertTrue(!OnTheLamService.hasTrueInvisibility(wearer),
                    "Test setup did not break True Invisibility");

            for (int tick = 0; tick < OnTheLamService.COOLDOWN_TICKS; tick++) {
                wearer.getCooldowns().tick();
            }
            helper.assertTrue(
                    !wearer.getCooldowns().isOnCooldown(ModItems.ON_THE_LAM.get()),
                    "Test setup did not let the cooldown fully elapse");

            settleCurioChange(wearer);
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "True Invisibility did not automatically return once the cooldown ended"
                            + " while still equipped");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void reEquipCannotBypassCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-reequip-bypass", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            Zombie victim = zombie(level, wearer, 3.0D);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(
                    victim, wearer.damageSources().playerAttack(wearer), 5.0F));
            helper.assertTrue(
                    wearer.getCooldowns().isOnCooldown(ModItems.ON_THE_LAM.get()),
                    "Test setup did not start the cooldown");

            unequipOnTheLam(wearer, helper);
            helper.assertTrue(
                    wearer.getCooldowns().isOnCooldown(ModItems.ON_THE_LAM.get()),
                    "Unequipping cleared an in-progress cooldown");

            equipOnTheLam(wearer, helper);
            helper.assertTrue(!OnTheLamService.hasTrueInvisibility(wearer),
                    "Re-equipping while on cooldown bypassed the cooldown");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void enteringInvisibilityClearsExistingMobTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-clear-target", 1.5D);
        try {
            Zombie hunter = zombie(level, wearer, 3.0D);
            hunter.setTarget(wearer);
            helper.assertTrue(hunter.getTarget() == wearer,
                    "Test setup did not lock the zombie onto the player");

            equipOnTheLam(wearer, helper);
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "Test setup did not grant True Invisibility");
            helper.assertTrue(hunter.getTarget() == null,
                    "Entering True Invisibility did not clear an already-locked target");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void mobCannotAcquireTrueInvisiblePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-no-acquire", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            helper.assertTrue(OnTheLamService.hasTrueInvisibility(wearer),
                    "Test setup did not grant True Invisibility");

            Zombie hunter = zombie(level, wearer, 3.0D);
            helper.assertTrue(hunter.getTarget() == null,
                    "Test setup zombie already had a target");
            hunter.setTarget(wearer);
            helper.assertTrue(hunter.getTarget() != wearer,
                    "A hostile mob acquired a True-Invisible player as its target");

            // Sanity: a visible player remains a valid target through the
            // same code path once True Invisibility ends.
            unequipOnTheLam(wearer, helper);
            hunter.setTarget(wearer);
            helper.assertTrue(hunter.getTarget() == wearer,
                    "A visible player could no longer be targeted at all");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void equippingOnTheLamCreatesExactlyOneHospitalDebts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer first = testPlayer(level, "otl-weakness-first", 1.5D);
        TestPlayer second = testPlayer(level, "otl-weakness-second", 4.5D);
        try {
            ICurioStacksHandler firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            ICurioStacksHandler secondWeakness = handler(second, CurioSlotIds.WEAKNESS, helper);
            int firstBaseSlots = firstWeakness.getStacks().getSlots();
            int secondBaseSlots = secondWeakness.getStacks().getSlots();

            equipOnTheLam(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots() == firstBaseSlots + 1,
                    "On the Lam did not add exactly one weakness slot");
            helper.assertTrue(countBoundHospitalDebts(firstWeakness, first) == 1,
                    "On the Lam did not equip exactly one bound Hospital Debts");
            helper.assertTrue(handler(second, CurioSlotIds.WEAKNESS, helper)
                            .getStacks().getSlots() == secondBaseSlots,
                    "First player's item changed the second player's slots");
            helper.succeed();
        } finally {
            unequipOnTheLam(first, helper);
            unequipOnTheLam(second, helper);
            first.discard();
            second.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void repeatedTicksDoNotDuplicateWeakness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-weakness-idempotent", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            ICurioStacksHandler weakness = handler(wearer, CurioSlotIds.WEAKNESS, helper);
            int baseSlots = weakness.getStacks().getSlots();

            OnTheLamService.reconcile(wearer);
            OnTheLamService.reconcile(wearer);
            OnTheLamService.reconcile(wearer);
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            settleCurioChange(wearer);

            weakness = handler(wearer, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(countBoundHospitalDebts(weakness, wearer) == 1,
                    "Repeated reconciliation/ticks duplicated Hospital Debts");
            helper.assertTrue(weakness.getStacks().getSlots() == baseSlots,
                    "Repeated reconciliation/ticks duplicated the weakness slot");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void removingOnTheLamRemovesManagedHospitalDebts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-weakness-remove", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            ICurioStacksHandler weakness = handler(wearer, CurioSlotIds.WEAKNESS, helper);
            int baseSlots = weakness.getStacks().getSlots() - 1;

            int inventoryBefore = wearer.getInventory().countItem(ModItems.HOSPITAL_DEBTS.get());
            unequipOnTheLam(wearer, helper);
            weakness = handler(wearer, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(weakness.getStacks().getSlots() == baseSlots,
                    "Removing On the Lam did not remove its weakness slot");
            helper.assertTrue(countBoundHospitalDebts(weakness, wearer) == 0,
                    "Removing On the Lam did not directly delete its bound Hospital Debts");
            helper.assertTrue(
                    wearer.getInventory().countItem(ModItems.HOSPITAL_DEBTS.get())
                            == inventoryBefore,
                    "Deleted Hospital Debts entered the player's inventory");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void otherWeaknessesAreNotRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-weakness-preserve", 1.5D);
        try {
            ICurioStacksHandler weakness = handler(wearer, CurioSlotIds.WEAKNESS, helper);
            // Represents a weakness the player already owns from another
            // source, occupying the base weakness slot before On the Lam
            // ever grants its own extra slot.
            weakness.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.ABANDONED_AND_ALONE.get()));

            equipOnTheLam(wearer, helper);
            weakness = handler(wearer, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(
                    weakness.getStacks().getStackInSlot(0)
                            .is(ModItems.ABANDONED_AND_ALONE.get()),
                    "Equipping On the Lam disturbed a pre-existing weakness");

            unequipOnTheLam(wearer, helper);
            weakness = handler(wearer, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(
                    weakness.getStacks().getStackInSlot(0)
                            .is(ModItems.ABANDONED_AND_ALONE.get()),
                    "Removing On the Lam deleted an unrelated pre-existing weakness");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void hospitalDebtsReducesMaximumSoulBy60Percent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-soul-percent", 1.5D);
        try {
            wearer.getInventory().setItem(0, soulTotem(500));
            SoulEnergyPoolService.refresh(wearer);
            int maxBefore = SoulEnergyPoolService.getMaximumSoul(wearer);
            helper.assertTrue(maxBefore > 0,
                    "Test setup did not produce a positive maximum soul energy");
            int currentBefore = SoulEnergyPoolService.getCurrentSoul(wearer);

            equipOnTheLam(wearer, helper);
            int expectedMax = Math.round(maxBefore * 0.4F);
            int maxAfter = SoulEnergyPoolService.getMaximumSoul(wearer);
            helper.assertTrue(maxAfter == expectedMax,
                    "Hospital Debts did not reduce maximum soul energy to exactly 40% of "
                            + maxBefore + " (expected " + expectedMax + ", got " + maxAfter + ")");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(wearer) <= maxAfter,
                    "Current soul energy exceeded the reduced maximum after equipping");

            unequipOnTheLam(wearer, helper);
            helper.assertTrue(SoulEnergyPoolService.getMaximumSoul(wearer) == maxBefore,
                    "Removing Hospital Debts did not restore the original maximum soul energy");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void relogOrDimensionLifecycleDoesNotDuplicateWeakness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer original = testPlayer(level, "otl-weakness-original", 1.5D);
        TestPlayer restored = testPlayer(level, "otl-weakness-restored", 7.5D);
        try {
            equipOnTheLam(original, helper);
            helper.assertTrue(countBoundHospitalDebts(
                            handler(original, CurioSlotIds.WEAKNESS, helper), original) == 1,
                    "Test setup did not equip exactly one bound Hospital Debts");

            // Simulated relog/death/dimension-change restoration.
            OnTheLamService.copyPersistentState(original, restored);
            equipOnTheLam(restored, helper);
            helper.assertTrue(countBoundHospitalDebts(
                            handler(restored, CurioSlotIds.WEAKNESS, helper), restored) == 1,
                    "Restored player did not retain exactly one bound Hospital Debts");

            // Repeated reconciliation after restoration still never duplicates.
            OnTheLamService.reconcile(restored);
            OnTheLamService.reconcile(restored);
            helper.assertTrue(countBoundHospitalDebts(
                            handler(restored, CurioSlotIds.WEAKNESS, helper), restored) == 1,
                    "Reconciling after restoration duplicated Hospital Debts");
            helper.succeed();
        } finally {
            unequipOnTheLam(original, helper);
            unequipOnTheLam(restored, helper);
            original.discard();
            restored.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:on_the_lam")
    public static void hospitalDebtsCannotBeManuallyUnequipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "otl-weakness-locked", 1.5D);
        try {
            equipOnTheLam(wearer, helper);
            ICurioStacksHandler weakness = handler(wearer, CurioSlotIds.WEAKNESS, helper);
            int slot = boundHospitalDebtsSlot(weakness, wearer);
            helper.assertTrue(slot >= 0, "No bound Hospital Debts to lock-test");

            ItemStack stack = weakness.getStacks().getStackInSlot(slot);
            SlotContext context = new SlotContext(
                    CurioSlotIds.WEAKNESS, wearer, slot, false, true);
            ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
            helper.assertTrue(curio != null && !curio.canUnequip(context),
                    "Hospital Debts did not reject manual unequip");
            helper.assertTrue(curio.getDropRule(
                            context, wearer.damageSources().generic(), 0, false)
                            == ICurio.DropRule.ALWAYS_KEEP,
                    "Hospital Debts is not protected from death drops");
            helper.assertTrue(weakness.getStacks()
                            .extractItem(slot, 1, false).isEmpty(),
                    "Survival extraction removed the locked Hospital Debts");
            helper.succeed();
        } finally {
            unequipOnTheLam(wearer, helper);
            wearer.discard();
        }
    }

    private static int countBoundHospitalDebts(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        int count = 0;
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (HospitalDebtsItem.isOnTheLamBound(
                    weaknesses.getStacks().getStackInSlot(slot), owner.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static int boundHospitalDebtsSlot(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (HospitalDebtsItem.isOnTheLamBound(
                    weaknesses.getStacks().getStackInSlot(slot), owner.getUUID())) {
                return slot;
            }
        }
        return -1;
    }

    private static ItemStack soulTotem(int souls) {
        ItemStack stack = new ItemStack(
                com.Polarice3.Goety.common.items.ModItems.TOTEM_OF_SOULS.get());
        ((ITotem) stack.getItem()).setTagTick(stack);
        ITotem.setSoulsamount(stack, souls);
        return stack;
    }

    private static Zombie zombie(ServerLevel level, ServerPlayer near, double offset) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.setPos(near.getX() + offset, near.getY(), near.getZ());
        level.addFreshEntity(zombie);
        return zombie;
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

    private static void equipOnTheLam(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler asset = handler(player, CurioSlotIds.ASSET, helper);
        boolean alreadyWorn = false;
        for (int slot = 0; slot < asset.getStacks().getSlots(); slot++) {
            if (asset.getStacks().getStackInSlot(slot).is(ModItems.ON_THE_LAM.get())) {
                alreadyWorn = true;
            }
        }
        if (!alreadyWorn) {
            asset.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.ON_THE_LAM.get()));
        }
        settleCurioChange(player);
    }

    private static void unequipOnTheLam(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler asset = handler(player, CurioSlotIds.ASSET, helper);
        for (int slot = 0; slot < asset.getStacks().getSlots(); slot++) {
            if (asset.getStacks().getStackInSlot(slot).is(ModItems.ON_THE_LAM.get())) {
                asset.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        settleCurioChange(player);
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
