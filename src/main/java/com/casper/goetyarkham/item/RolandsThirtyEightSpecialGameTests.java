package com.casper.goetyarkham.item;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RolandsThirtyEightSpecialGameTests {
    private RolandsThirtyEightSpecialGameTests() {
    }

    @GameTest(template = "empty", batch = "goetyarkham:rolands_38_special")
    public static void unwornKillsDoNotAccumulateAndWornBaselineIsCorrect(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "roland-baseline", 1.5D);
        try {
            ICurioStacksHandler hands = handler(wearer, CurioSlotIds.HANDS, helper);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);

            equipPistol(wearer, helper);
            ItemStack pistol = pistolStack(hands, wearer);
            helper.assertTrue(RolandsThirtyEightSpecialItem.getBonusIntellect(pistol) == 0,
                    "Freshly equipped pistol has a non-zero bonus Intellect");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + 1,
                    "Worn pistol did not grant exactly +1 Strength");

            unequipPistol(wearer, helper);
            Zombie victim = zombie(level, wearer, 3.0D);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(
                    victim, wearer.damageSources().playerAttack(wearer)));
            helper.assertTrue(RolandsThirtyEightSpecialItem.getBonusIntellect(pistol) == 0,
                    "A kill while the pistol was unworn accumulated bonus Intellect");
            helper.succeed();
        } finally {
            unequipPistol(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:rolands_38_special")
    public static void killsAccumulateUpToCapAndSurviveTransferAndSerialization(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "roland-kills", 1.5D);
        TestPlayer other = testPlayer(level, "roland-transfer", 4.5D);
        try {
            ICurioStacksHandler hands = handler(wearer, CurioSlotIds.HANDS, helper);
            equipPistol(wearer, helper);
            ItemStack pistol = pistolStack(hands, wearer);

            assertTooltipCurrentIntellect(helper, pistol, 0);

            // Kill 1: direct melee attack, hostile mob.
            Zombie firstVictim = zombie(level, wearer, 3.0D);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(
                    firstVictim, wearer.damageSources().playerAttack(wearer)));
            helper.assertTrue(RolandsThirtyEightSpecialItem.getBonusIntellect(pistol) == 2,
                    "First kill did not raise bonus Intellect from 0 to 2");
            assertTooltipCurrentIntellect(helper, pistol, 2);

            // Kill 2: indirect/projectile attribution, matching TACZ-style
            // bullets - the arrow is the direct entity, the player is the
            // causing entity credited with the kill.
            AbstractArrow arrow = EntityType.ARROW.create(level);
            helper.assertTrue(arrow != null, "Could not create test arrow");
            arrow.setOwner(wearer);
            Zombie secondVictim = zombie(level, wearer, 3.5D);
            DamageSource projectileSource =
                    wearer.damageSources().arrow(arrow, wearer);
            helper.assertTrue(projectileSource.getEntity() == wearer
                            && projectileSource.getDirectEntity() == arrow,
                    "Test setup did not produce an indirect projectile damage source");
            MinecraftForge.EVENT_BUS.post(
                    new LivingDeathEvent(secondVictim, projectileSource));
            helper.assertTrue(RolandsThirtyEightSpecialItem.getBonusIntellect(pistol) == 4,
                    "Indirect/projectile kill did not raise bonus Intellect from 2 to 4");
            assertTooltipCurrentIntellect(helper, pistol, 4);

            // Kill 3: passive mob still counts.
            Cow thirdVictim = EntityType.COW.create(level);
            helper.assertTrue(thirdVictim != null, "Could not create test cow");
            thirdVictim.setPos(wearer.getX() + 3.0D, wearer.getY(), wearer.getZ());
            level.addFreshEntity(thirdVictim);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(
                    thirdVictim, wearer.damageSources().playerAttack(wearer)));
            helper.assertTrue(RolandsThirtyEightSpecialItem.getBonusIntellect(pistol) == 6,
                    "Killing a passive mob did not raise bonus Intellect from 4 to 6");
            assertTooltipCurrentIntellect(helper, pistol, 6);

            // Kill 4: capped at 6, never exceeds the maximum.
            Zombie fourthVictim = zombie(level, wearer, 4.0D);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(
                    fourthVictim, wearer.damageSources().playerAttack(wearer)));
            helper.assertTrue(RolandsThirtyEightSpecialItem.getBonusIntellect(pistol) == 6,
                    "A fourth kill exceeded the +6 bonus Intellect cap");
            // Wearing the pistol auto-equips its signature weakness, Cover
            // Up, which applies its own -6 Intellect - net final Intellect
            // is the pistol's +6 kill bonus offset by that -6.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == RolandsThirtyEightSpecialItem.MAX_BONUS_INTELLECT
                            + CoverUpItem.INTELLECT_PENALTY,
                    "Accumulated kill bonus was not reflected in final Intellect");

            // Unequip/re-equip the exact same stack preserves its accumulated
            // bonus (equipPistol would instead hand out a fresh stack).
            unequipPistol(wearer, helper);
            helper.assertTrue(RolandsThirtyEightSpecialItem.getBonusIntellect(pistol) == 6,
                    "Unequipping the pistol reset its accumulated bonus Intellect");
            hands.getStacks().setStackInSlot(0, pistol);
            settleCurioChange(wearer);
            ItemStack reequipped = pistolStack(
                    handler(wearer, CurioSlotIds.HANDS, helper), wearer);
            helper.assertTrue(
                    RolandsThirtyEightSpecialItem.getBonusIntellect(reequipped) == 6,
                    "Re-equipping the pistol lost its accumulated bonus Intellect");

            // A serialize/deserialize round trip (standing in for relog,
            // death/respawn, and dimension change, all of which persist an
            // ItemStack's own tag through vanilla NBT saving) preserves it.
            ItemStack roundTripped = ItemStack.of(reequipped.save(new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(
                    RolandsThirtyEightSpecialItem.getBonusIntellect(roundTripped) == 6,
                    "An NBT serialize/deserialize round trip lost the accumulated bonus");

            // Transferring the exact stack to another player carries the bonus.
            ICurioStacksHandler otherHands = handler(other, CurioSlotIds.HANDS, helper);
            otherHands.getStacks().setStackInSlot(0, reequipped);
            settleCurioChange(other);
            ItemStack transferred = pistolStack(otherHands, other);
            helper.assertTrue(
                    RolandsThirtyEightSpecialItem.getBonusIntellect(transferred) == 6,
                    "Transferring the pistol to another player lost its bonus Intellect");
            helper.assertTrue(
                    transferred.getItem() instanceof RolandsThirtyEightSpecialItem item
                            && item.getEquipmentStatModifier(
                            StatType.INTELLECT,
                            new SlotContext(CurioSlotIds.HANDS, other, 0, false, true),
                            transferred) == 6,
                    "Transferred pistol did not grant its accumulated Intellect bonus to the new wearer");
            helper.succeed();
        } finally {
            unequipPistol(wearer, helper);
            unequipPistol(other, helper);
            wearer.discard();
            other.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:rolands_38_special")
    public static void conditionalStrengthAndDamageTrackFinalIntellectThreshold(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "roland-threshold", 1.5D);
        try {
            equipPistol(wearer, helper);
            double baseAttackDamage = wearer.getAttribute(Attributes.ATTACK_DAMAGE).getValue();
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);

            setFinalIntellect(wearer, 1);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT) == 1,
                    "Test setup did not produce final Intellect of exactly 1");
            assertNoBonusDamageOrStrength(helper, wearer, baseStrength, baseAttackDamage);

            setFinalIntellect(wearer, 2);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT) == 2,
                    "Test setup did not produce final Intellect of exactly 2");
            assertBonusDamageAndStrength(helper, wearer, baseStrength, baseAttackDamage);

            // 2 -> 1: the conditional bonus must be removed immediately.
            setFinalIntellect(wearer, 1);
            assertNoBonusDamageOrStrength(helper, wearer, baseStrength, baseAttackDamage);

            // 1 -> 2: the conditional bonus must be added immediately.
            setFinalIntellect(wearer, 2);
            assertBonusDamageAndStrength(helper, wearer, baseStrength, baseAttackDamage);

            helper.succeed();
        } finally {
            PlayerStatsService.reset(wearer);
            unequipPistol(wearer, helper);
            wearer.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:rolands_38_special")
    public static void coverUpAppliesIntellectPenaltyAndDrainsSoulOnlyWhileNegative(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer first = testPlayer(level, "cover-up-first", 1.5D);
        TestPlayer second = testPlayer(level, "cover-up-second", 4.5D);
        try {
            first.getInventory().setItem(0, soulTotem(100));
            second.getInventory().setItem(0, soulTotem(3));
            SoulEnergyPoolService.refresh(first);
            SoulEnergyPoolService.refresh(second);

            equipCoverUp(first, helper);
            equipCoverUp(second, helper);

            PlayerStatsService.setBase(first, StatType.INTELLECT, 6);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(first, StatType.INTELLECT) == 0,
                    "Cover Up did not apply exactly -6 Intellect");

            int soulBefore = SoulEnergyPoolService.getCurrentSoul(first);
            CoverUpSoulDrainService.tick(first);
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(first) == soulBefore,
                    "Soul Energy drained while final Intellect was exactly 0");

            PlayerStatsService.setBase(first, StatType.INTELLECT, 5);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(first, StatType.INTELLECT) == -1,
                    "Test setup did not produce final Intellect of exactly -1");
            soulBefore = SoulEnergyPoolService.getCurrentSoul(first);
            CoverUpSoulDrainService.tick(first);
            helper.assertTrue(
                    SoulEnergyPoolService.getCurrentSoul(first) == soulBefore - 10,
                    "Negative final Intellect did not drain exactly 10 Soul Energy");

            // Stops immediately once Cover Up is unequipped.
            unequipCoverUp(first, helper);
            soulBefore = SoulEnergyPoolService.getCurrentSoul(first);
            CoverUpSoulDrainService.tick(first);
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(first) == soulBefore,
                    "Soul Energy drained after Cover Up was unequipped");

            // Never drains below the pool's own floor of 0.
            PlayerStatsService.setBase(second, StatType.INTELLECT, -1);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(second, StatType.INTELLECT) < 0,
                    "Test setup did not produce a negative final Intellect for the second player");
            CoverUpSoulDrainService.tick(second);
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(second) == 0,
                    "Soul Energy dropped below zero");

            // The two players' state never crosses. First already had Cover
            // Up unequipped above, so its base Intellect (5) now applies
            // without the -6 penalty.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(first, StatType.INTELLECT) == 5,
                    "Second player's setup changed the first player's final Intellect");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(first);
            PlayerStatsService.reset(second);
            unequipCoverUp(first, helper);
            unequipCoverUp(second, helper);
            first.discard();
            second.discard();
        }
    }

    @GameTest(template = "empty", batch = "goetyarkham:rolands_38_special")
    public static void signatureWeaknessAutoEquipsLocksAndCleansUpCoverUp(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer first = testPlayer(level, "roland-weakness-first", 1.5D);
        TestPlayer second = testPlayer(level, "roland-weakness-second", 4.5D);
        try {
            ICurioStacksHandler firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            ICurioStacksHandler secondWeakness = handler(second, CurioSlotIds.WEAKNESS, helper);
            int firstBaseSlots = firstWeakness.getStacks().getSlots();
            int secondBaseSlots = secondWeakness.getStacks().getSlots();

            equipPistol(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots() == firstBaseSlots + 1,
                    "Roland's .38 Special did not add exactly one weakness slot");
            helper.assertTrue(countBoundCoverUps(firstWeakness, first) == 1,
                    "Roland's .38 Special did not equip exactly one bound Cover Up");
            helper.assertTrue(handler(second, CurioSlotIds.WEAKNESS, helper)
                            .getStacks().getSlots() == secondBaseSlots,
                    "First player's pistol changed the second player's slots");

            RolandsThirtyEightSpecialService.reconcile(first);
            RolandsThirtyEightSpecialService.reconcile(first);
            RolandsThirtyEightSpecialService.reconcile(first);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(countBoundCoverUps(firstWeakness, first) == 1,
                    "Login/dimension/sync reconciliation duplicated Cover Up");
            helper.assertTrue(firstWeakness.getStacks().getSlots() == firstBaseSlots + 1,
                    "Repeated reconciliation duplicated the weakness slot");

            assertManualRemovalLocked(helper, first, firstWeakness);

            equipPistol(second, helper);
            secondWeakness = handler(second, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(secondWeakness.getStacks().getSlots() == secondBaseSlots + 1
                            && countBoundCoverUps(secondWeakness, second) == 1,
                    "Second wearer did not receive its own bound Cover Up");

            // Non-standard access: write a second pistol directly into
            // another hands slot, bypassing the normal equip-time duplicate
            // guard, and confirm reconcile never creates a second Cover Up.
            ICurioStacksHandler firstHands = handler(first, CurioSlotIds.HANDS, helper);
            firstHands.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.ROLANDS_38_SPECIAL.get()));
            settleCurioChange(first);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(countBoundCoverUps(firstWeakness, first) == 1,
                    "Wearing a second pistol created a second bound Cover Up");
            helper.assertTrue(firstWeakness.getStacks().getSlots() == firstBaseSlots + 1,
                    "Wearing a second pistol changed the granted weakness slot count");
            firstHands.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settleCurioChange(first);

            int inventoryBefore = first.getInventory().countItem(ModItems.COVER_UP.get());
            unequipPistol(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots() == firstBaseSlots,
                    "Removing the pistol did not remove its weakness slot");
            helper.assertTrue(countBoundCoverUps(firstWeakness, first) == 0,
                    "Removing the pistol did not directly delete its bound Cover Up");
            helper.assertTrue(first.getInventory().countItem(ModItems.COVER_UP.get())
                            == inventoryBefore,
                    "Deleted Cover Up entered the player's inventory");
            helper.assertTrue(level.getEntitiesOfClass(
                            ItemEntity.class,
                            first.getBoundingBox().inflate(16.0D),
                            entity -> entity.getItem().is(ModItems.COVER_UP.get())).isEmpty(),
                    "Deleted Cover Up spawned an item entity");
            helper.assertTrue(countBoundCoverUps(
                            handler(second, CurioSlotIds.WEAKNESS, helper), second) == 1,
                    "Removing first player's pistol deleted second player's bound Cover Up");

            equipPistol(first, helper);
            helper.assertTrue(countBoundCoverUps(
                            handler(first, CurioSlotIds.WEAKNESS, helper), first) == 1,
                    "Re-equipping did not regenerate the bound Cover Up");

            // Simulated relog/death/dimension-change restoration.
            TestPlayer restored = testPlayer(level, "roland-weakness-restored", 7.5D);
            try {
                RolandsThirtyEightSpecialService.copyPersistentState(first, restored);
                equipPistol(restored, helper);
                helper.assertTrue(countBoundCoverUps(
                                handler(restored, CurioSlotIds.WEAKNESS, helper),
                                restored) == 1,
                        "Restored player did not retain exactly one bound Cover Up");
            } finally {
                unequipPistol(restored, helper);
                restored.discard();
            }
            helper.succeed();
        } finally {
            unequipPistol(first, helper);
            unequipPistol(second, helper);
            first.discard();
            second.discard();
        }
    }

    /**
     * Sets base Intellect so that final Intellect equals {@code desiredFinal}
     * while the pistol is worn. Wearing the pistol auto-equips Cover Up
     * (-6 Intellect), so base must be offset to compensate; the pistol's own
     * kill bonus is 0 for the tests that call this (no kills performed).
     */
    private static void setFinalIntellect(ServerPlayer wearer, int desiredFinal) {
        PlayerStatsService.setBase(
                wearer, StatType.INTELLECT, desiredFinal - CoverUpItem.INTELLECT_PENALTY);
    }

    /**
     * {@code baseStrength} is captured after the pistol is already worn, so
     * it already includes its fixed +1 Strength (an {@link
     * com.casper.goetyarkham.stats.EquipmentStatModifier} contribution) -
     * only the conditional bonus is added/subtracted here. The Attack Damage
     * assertions check Roland's own fixed-UUID transient modifier directly
     * rather than the entity's aggregate Attack Damage value, since the
     * pre-existing Strength-to-damage formula ({@code
     * StrengthEffects#getAttackDamageBonus}) also legitimately changes the
     * aggregate value once the conditional +2 Strength itself applies -
     * that cascading effect is correct, expected behavior, not something
     * this test is responsible for verifying.
     */
    private static void assertNoBonusDamageOrStrength(
            GameTestHelper helper,
            ServerPlayer wearer,
            int baseStrength,
            double baseAttackDamage) {
        helper.assertTrue(
                PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                        == baseStrength,
                "Below-threshold final Intellect still granted the conditional +2 Strength");
        AttributeInstance attackDamage = wearer.getAttribute(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(
                attackDamage.getModifier(RolandDamageBonusService.ATTACK_DAMAGE_MODIFIER_ID)
                        == null,
                "Below-threshold final Intellect still granted the conditional +1 Attack Damage");
    }

    private static void assertBonusDamageAndStrength(
            GameTestHelper helper,
            ServerPlayer wearer,
            int baseStrength,
            double baseAttackDamage) {
        helper.assertTrue(
                PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                        == baseStrength + RolandConditionalStrengthService.STRENGTH_BONUS,
                "At-threshold final Intellect did not grant the conditional +2 Strength");
        AttributeInstance attackDamage = wearer.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeModifier modifier = attackDamage.getModifier(
                RolandDamageBonusService.ATTACK_DAMAGE_MODIFIER_ID);
        helper.assertTrue(
                modifier != null
                        && modifier.getAmount() == RolandDamageBonusService.DAMAGE_BONUS
                        && modifier.getOperation() == AttributeModifier.Operation.ADDITION,
                "At-threshold final Intellect did not grant the conditional +1 Attack Damage");
    }

    private static void assertTooltipCurrentIntellect(
            GameTestHelper helper, ItemStack pistol, int expected) {
        List<Component> tooltip = new ArrayList<>();
        pistol.getItem().appendHoverText(pistol, null, tooltip, TooltipFlag.NORMAL);
        String expectedLine = "Current bonus Intellect: +" + expected;
        helper.assertTrue(
                tooltip.stream().anyMatch(line -> expectedLine.equals(line.getString())),
                "Tooltip did not show current bonus Intellect of +" + expected);
    }

    private static void assertManualRemovalLocked(
            GameTestHelper helper,
            TestPlayer player,
            ICurioStacksHandler weaknesses) {
        int slot = boundCoverUpSlot(weaknesses, player);
        helper.assertTrue(slot >= 0, "No bound Cover Up to lock-test");
        ItemStack stack = weaknesses.getStacks().getStackInSlot(slot);
        SlotContext context = new SlotContext(
                CurioSlotIds.WEAKNESS, player, slot, false, true);
        ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
        helper.assertTrue(curio != null && !curio.canUnequip(context),
                "Cover Up did not reject manual unequip");
        helper.assertTrue(curio.getDropRule(
                        context, player.damageSources().generic(), 0, false)
                        == ICurio.DropRule.ALWAYS_KEEP,
                "Cover Up is not protected from death drops");
        helper.assertTrue(weaknesses.getStacks()
                        .extractItem(slot, 1, false).isEmpty(),
                "Survival extraction removed the locked Cover Up");
        player.getAbilities().instabuild = true;
        helper.assertTrue(weaknesses.getStacks()
                        .extractItem(slot, 1, false).isEmpty(),
                "Creative extraction removed the locked Cover Up");
        player.getAbilities().instabuild = false;
        helper.assertTrue(countBoundCoverUps(weaknesses, player) == 1,
                "Lock test changed the bound Cover Up count");
    }

    private static int countBoundCoverUps(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        int count = 0;
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (CoverUpItem.isPistolBound(
                    weaknesses.getStacks().getStackInSlot(slot), owner.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static int boundCoverUpSlot(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (CoverUpItem.isPistolBound(
                    weaknesses.getStacks().getStackInSlot(slot), owner.getUUID())) {
                return slot;
            }
        }
        return -1;
    }

    private static ItemStack pistolStack(ICurioStacksHandler hands, ServerPlayer wearer) {
        for (int slot = 0; slot < hands.getStacks().getSlots(); slot++) {
            ItemStack stack = hands.getStacks().getStackInSlot(slot);
            if (stack.is(ModItems.ROLANDS_38_SPECIAL.get())) {
                return stack;
            }
        }
        throw new IllegalStateException(
                "Roland's .38 Special is not worn by " + wearer.getGameProfile().getName());
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

    private static void equipPistol(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler hands = handler(player, CurioSlotIds.HANDS, helper);
        boolean alreadyWorn = false;
        for (int slot = 0; slot < hands.getStacks().getSlots(); slot++) {
            if (hands.getStacks().getStackInSlot(slot).is(
                    ModItems.ROLANDS_38_SPECIAL.get())) {
                alreadyWorn = true;
            }
        }
        if (!alreadyWorn) {
            hands.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.ROLANDS_38_SPECIAL.get()));
            settleCurioChange(player);
        }
    }

    private static void unequipPistol(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler hands = handler(player, CurioSlotIds.HANDS, helper);
        for (int slot = 0; slot < hands.getStacks().getSlots(); slot++) {
            if (hands.getStacks().getStackInSlot(slot).is(
                    ModItems.ROLANDS_38_SPECIAL.get())) {
                hands.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        settleCurioChange(player);
    }

    private static void equipCoverUp(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler weakness = handler(player, CurioSlotIds.WEAKNESS, helper);
        if (!weakness.getStacks().getStackInSlot(0).is(ModItems.COVER_UP.get())) {
            weakness.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.COVER_UP.get()));
            settleCurioChange(player);
        }
    }

    private static void unequipCoverUp(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler weakness = handler(player, CurioSlotIds.WEAKNESS, helper);
        for (int slot = 0; slot < weakness.getStacks().getSlots(); slot++) {
            if (weakness.getStacks().getStackInSlot(slot).is(ModItems.COVER_UP.get())) {
                weakness.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
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
