package com.casper.goetyarkham.item;

import com.Polarice3.Goety.api.entities.IOwned;
import com.Polarice3.Goety.api.items.magic.ITotem;
import com.Polarice3.Goety.common.entities.ModEntityType;
import com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent;
import com.Polarice3.Goety.utils.ModDamageSource;
import com.Polarice3.Goety.utils.SEHelper;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.SanityChangeCause;
import com.casper.goetyarkham.sanity.SanityService;
import com.casper.goetyarkham.soul.FocusCastSoulSpendTracker;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HyperboreaGameTests {
    private HyperboreaGameTests() {
    }

    @GameTest(template = "empty")
    public static void boundWeaknessLifecycleIsPlayerOwnedAndIdempotent(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer first = testPlayer(level, "hyperborea-first", 1.5D);
        TestPlayer second = testPlayer(level, "hyperborea-second", 4.5D);
        try {
            ICurioStacksHandler firstWeakness = handler(
                    first, CurioSlotIds.WEAKNESS, helper);
            ICurioStacksHandler secondWeakness = handler(
                    second, CurioSlotIds.WEAKNESS, helper);
            int firstBaseSlots = firstWeakness.getStacks().getSlots();
            int secondBaseSlots = secondWeakness.getStacks().getSlots();
            int firstInitialSanity = resetSanity(first);
            int secondInitialSanity = resetSanity(second);

            equipHeirloom(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots()
                            == firstBaseSlots + 1,
                    "Heirloom did not add exactly one weakness slot");
            helper.assertTrue(countBoundMemories(firstWeakness, first) == 1,
                    "Heirloom did not equip exactly one bound Dark Memory");
            helper.assertTrue(SanityService.getCurrentSanity(first)
                            == firstInitialSanity - DarkMemoryItem.SANITY_DAMAGE,
                    "First Dark Memory activation did not remove exactly 2 sanity");
            helper.assertTrue(handler(second, CurioSlotIds.WEAKNESS, helper)
                            .getStacks().getSlots() == secondBaseSlots,
                    "First player's Heirloom changed the second player's slots");
            helper.assertTrue(SanityService.getCurrentSanity(second)
                            == secondInitialSanity,
                    "First player's Dark Memory damaged the second player's sanity");

            int settledSanity = SanityService.getCurrentSanity(first);
            HeirloomOfHyperboreaService.reconcile(first);
            HeirloomOfHyperboreaService.reconcile(first);
            HeirloomOfHyperboreaService.reconcile(first);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(countBoundMemories(firstWeakness, first) == 1,
                    "Login/dimension/sync reconciliation duplicated Dark Memory");
            helper.assertTrue(SanityService.getCurrentSanity(first)
                            == settledSanity,
                    "Reconciliation replayed Dark Memory activation");

            assertManualRemovalLocked(helper, first, firstWeakness);

            // A second equip in the same server tick remains player-owned. Both
            // manager submissions are left to the existing same-tick merger.
            equipHeirloom(second, helper);
            secondWeakness = handler(second, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(secondWeakness.getStacks().getSlots()
                            == secondBaseSlots + 1
                            && countBoundMemories(secondWeakness, second) == 1,
                    "Second same-tick wearer did not receive its own bound weakness");
            helper.assertTrue(SanityService.getCurrentSanity(second)
                            == secondInitialSanity - DarkMemoryItem.SANITY_DAMAGE,
                    "Second same-tick wearer did not lose exactly 2 sanity");
            helper.assertTrue(SanityService.getCurrentSanity(first)
                            == settledSanity,
                    "Second wearer's activation damaged the first wearer's sanity");

            int inventoryBefore = first.getInventory().countItem(
                    ModItems.DARK_MEMORY.get());
            unequipHeirloom(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots()
                            == firstBaseSlots,
                    "Removing Heirloom did not remove its weakness slot");
            helper.assertTrue(countBoundMemories(firstWeakness, first) == 0,
                    "Removing Heirloom did not directly delete its Dark Memory");
            helper.assertTrue(first.getInventory().countItem(
                            ModItems.DARK_MEMORY.get()) == inventoryBefore,
                    "Deleted Dark Memory entered the player's inventory");
            helper.assertTrue(level.getEntitiesOfClass(
                            ItemEntity.class,
                            first.getBoundingBox().inflate(16.0D),
                            entity -> entity.getItem().is(
                                    ModItems.DARK_MEMORY.get())).isEmpty(),
                    "Deleted Dark Memory spawned an item entity");
            helper.assertTrue(countBoundMemories(
                            handler(second, CurioSlotIds.WEAKNESS, helper),
                            second) == 1,
                    "Removing first Heirloom deleted second player's Dark Memory");

            equipHeirloom(first, helper);
            helper.assertTrue(SanityService.getCurrentSanity(first)
                            == settledSanity - DarkMemoryItem.SANITY_DAMAGE,
                    "A genuine re-equip did not reactivate Dark Memory once");

            TestPlayer restored = testPlayer(
                    level, "hyperborea-restored", 7.5D);
            try {
                int restoredSanity = resetSanity(restored);
                HeirloomOfHyperboreaService.copyPersistentState(first, restored);
                equipHeirloom(restored, helper);
                helper.assertTrue(SanityService.getCurrentSanity(restored)
                                == restoredSanity,
                        "Death/login state restoration replayed activation");
                helper.assertTrue(countBoundMemories(handler(
                                restored, CurioSlotIds.WEAKNESS, helper),
                                restored) == 1,
                        "Death/login restoration did not retain one bound memory");
            } finally {
                unequipHeirloom(restored, helper);
                restored.discard();
            }
            helper.succeed();
        } finally {
            unequipHeirloom(first, helper);
            unequipHeirloom(second, helper);
            first.discard();
            second.discard();
        }
    }

    @GameTest(template = "empty")
    public static void focusSettlementUsesActualSpendAndOneDamageEvent(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer caster = testPlayer(level, "hyperborea-caster", 1.5D);
        TestPlayer otherPlayer = testPlayer(level, "hyperborea-other", 3.5D);
        HurtCapture capture = new HurtCapture();
        MinecraftForge.EVENT_BUS.register(capture);
        try {
            equipHeirloom(caster, helper);
            Zombie target = zombie(level, caster, 3.0D, 100.0F, helper);
            Zombie outside = zombie(level, caster, 11.0D, 100.0F, helper);
            Zombie allied = zombie(level, caster, 4.0D, 100.0F, helper);
            var team = level.getScoreboard().addPlayerTeam(
                    "hyperborea_allies_" + UUID.randomUUID().toString().substring(0, 8));
            level.getScoreboard().addPlayerToTeam(caster.getScoreboardName(), team);
            level.getScoreboard().addPlayerToTeam(allied.getScoreboardName(), team);

            Wolf wolf = EntityType.WOLF.create(level);
            helper.assertTrue(wolf != null, "Could not create tame wolf");
            wolf.tame(caster);
            wolf.setPos(caster.getX() + 4.5D, caster.getY(), caster.getZ());
            level.addFreshEntity(wolf);

            Mob servant = ModEntityType.ZOMBIE_SERVANT.get().create(level);
            helper.assertTrue(servant instanceof IOwned,
                    "Goety zombie servant does not expose IOwned");
            ((IOwned) servant).setTrueOwner(caster);
            servant.setPos(caster.getX() + 5.0D, caster.getY(), caster.getZ());
            level.addFreshEntity(servant);

            WitherBoss boss = EntityType.WITHER.create(level);
            helper.assertTrue(boss != null, "Could not create Wither boss");
            boss.setInvulnerableTicks(0);
            boss.setPos(caster.getX() + 6.0D, caster.getY(), caster.getZ());
            level.addFreshEntity(boss);

            caster.getInventory().setItem(0, soulTotem(100));
            SoulEnergyPoolService.refresh(caster);
            float otherHealth = otherPlayer.getHealth();
            float outsideHealth = outside.getHealth();
            float alliedHealth = allied.getHealth();
            float wolfHealth = wolf.getHealth();
            float servantHealth = servant.getHealth();
            float bossHealth = boss.getHealth();

            capture.watch(target);
            FocusCastSoulSpendTracker.begin(caster);
            SEHelper.decreaseSouls(caster, 4);
            FocusCastSoulSpendTracker.begin(caster);
            SEHelper.decreaseSouls(caster, 6);
            helper.assertTrue(FocusCastSoulSpendTracker.finish(caster) == 6
                            && capture.events == 0,
                    "Nested focus settlement emitted damage before the cast ended");
            int spent = FocusCastSoulSpendTracker.finish(caster);
            helper.assertTrue(spent == 10,
                    "Multi-step focus settlement did not aggregate actual spend to 10");
            helper.assertTrue(capture.events == 1 && capture.lastAmount == 10.0F,
                    "10 spent souls did not create one 10-damage hurt event");
            helper.assertTrue(capture.lastSource != null
                            && capture.lastSource.is(ModDamageSource.MAGIC_BOLT)
                            && capture.lastSource.getEntity() == caster
                            && capture.lastSource.getDirectEntity() == caster,
                    "Heirloom damage source lost Goety magic type or caster attribution");
            helper.assertTrue(outside.getHealth() == outsideHealth,
                    "Enemy outside the configured radius was damaged");
            helper.assertTrue(otherPlayer.getHealth() == otherHealth,
                    "Another player was damaged");
            helper.assertTrue(allied.getHealth() == alliedHealth,
                    "Allied enemy was damaged");
            helper.assertTrue(wolf.getHealth() == wolfHealth,
                    "Tamed animal was damaged");
            helper.assertTrue(servant.getHealth() == servantHealth,
                    "Owned Goety servant was damaged");
            helper.assertTrue(boss.getHealth() < bossHealth,
                    "Boss was incorrectly excluded from Heirloom damage");

            target.invulnerableTime = 0;
            target.setHealth(100.0F);
            capture.watch(target);
            SoulEnergyPoolService.setSoul(caster, 100);
            capture.forceNextSoulLoss(caster, 24);
            FocusCastSoulSpendTracker.begin(caster);
            SEHelper.decreaseSouls(caster, 40);
            spent = FocusCastSoulSpendTracker.finish(caster);
            helper.assertTrue(spent == 24,
                    "40 raw souls at 40% discount did not settle as 24 actual souls");
            helper.assertTrue(capture.events == 1 && capture.lastAmount == 24.0F,
                    "Discounted cast did not create one 24-damage hurt event");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(caster) == 76,
                    "Unified soul pool did not pay the discounted 24 souls");

            target.invulnerableTime = 0;
            target.setHealth(100.0F);
            capture.watch(target);
            int beforeNonFocus = SoulEnergyPoolService.getCurrentSoul(caster);
            SEHelper.decreaseSouls(caster, 5);
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(caster)
                            < beforeNonFocus,
                    "Non-focus setup did not actually remove soul energy");
            helper.assertTrue(capture.events == 0 && target.getHealth() == 100.0F,
                    "Non-focus soul removal triggered Heirloom damage");

            FocusCastSoulSpendTracker.begin(caster);
            spent = FocusCastSoulSpendTracker.finish(caster);
            helper.assertTrue(spent == 0 && capture.events == 0,
                    "Zero-cost focus settlement triggered damage");
            helper.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(capture);
            unequipHeirloom(caster, helper);
            caster.discard();
            otherPlayer.discard();
        }
    }

    private static void assertManualRemovalLocked(
            GameTestHelper helper,
            TestPlayer player,
            ICurioStacksHandler weaknesses) {
        int slot = boundMemorySlot(weaknesses, player);
        helper.assertTrue(slot >= 0, "No bound Dark Memory to lock-test");
        ItemStack stack = weaknesses.getStacks().getStackInSlot(slot);
        SlotContext context = new SlotContext(
                CurioSlotIds.WEAKNESS, player, slot, false, true);
        ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
        helper.assertTrue(curio != null && !curio.canUnequip(context),
                "Dark Memory Curio did not reject manual unequip");
        helper.assertTrue(curio.getDropRule(
                        context, player.damageSources().generic(), 0, false)
                        == ICurio.DropRule.ALWAYS_KEEP,
                "Dark Memory is not protected from death drops");
        helper.assertTrue(weaknesses.getStacks()
                        .extractItem(slot, 1, false).isEmpty(),
                "Survival extraction removed locked Dark Memory");
        player.getAbilities().instabuild = true;
        helper.assertTrue(weaknesses.getStacks()
                        .extractItem(slot, 1, false).isEmpty(),
                "Creative extraction removed locked Dark Memory");
        player.getAbilities().instabuild = false;
        helper.assertTrue(countBoundMemories(weaknesses, player) == 1,
                "Lock test changed the bound Dark Memory count");
    }

    private static TestPlayer testPlayer(
            ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        return player;
    }

    private static int resetSanity(ServerPlayer player) {
        int maximum = SanityService.getMaximumSanity(player);
        SanityService.setSanity(player, maximum, SanityChangeCause.COMMAND);
        return SanityService.getCurrentSanity(player);
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

    private static void equipHeirloom(
            ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler necklace = handler(
                player, CurioSlotIds.NECKLACE, helper);
        if (!necklace.getStacks().getStackInSlot(0).is(
                ModItems.HEIRLOOM_OF_HYPERBOREA.get())) {
            necklace.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.HEIRLOOM_OF_HYPERBOREA.get()));
            settleCurioChange(player);
        }
    }

    private static void unequipHeirloom(
            ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler necklace = handler(
                player, CurioSlotIds.NECKLACE, helper);
        if (necklace.getStacks().getStackInSlot(0).is(
                ModItems.HEIRLOOM_OF_HYPERBOREA.get())) {
            necklace.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(player);
        }
    }

    private static void settleCurioChange(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static int countBoundMemories(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        int count = 0;
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (DarkMemoryItem.isHeirloomBound(
                    weaknesses.getStacks().getStackInSlot(slot),
                    owner.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static int boundMemorySlot(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (DarkMemoryItem.isHeirloomBound(
                    weaknesses.getStacks().getStackInSlot(slot),
                    owner.getUUID())) {
                return slot;
            }
        }
        return -1;
    }

    private static Zombie zombie(
            ServerLevel level,
            ServerPlayer caster,
            double offset,
            float health,
            GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(zombie != null, "Could not create test zombie");
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        zombie.setHealth(health);
        zombie.setPos(caster.getX() + offset, caster.getY(), caster.getZ());
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static ItemStack soulTotem(int souls) {
        ItemStack stack = new ItemStack(
                com.Polarice3.Goety.common.items.ModItems.TOTEM_OF_SOULS.get());
        ((ITotem) stack.getItem()).setTagTick(stack);
        ITotem.setSoulsamount(stack, souls);
        return stack;
    }

    private static final class HurtCapture {
        private Zombie watched;
        private int events;
        private float lastAmount;
        private DamageSource lastSource;
        private ServerPlayer soulLossPlayer;
        private int forcedSoulLoss = -1;

        private void watch(Zombie watched) {
            this.watched = watched;
            this.events = 0;
            this.lastAmount = 0.0F;
            this.lastSource = null;
        }

        private void forceNextSoulLoss(
                ServerPlayer player, int actualLoss) {
            soulLossPlayer = player;
            forcedSoulLoss = actualLoss;
        }

        @SubscribeEvent
        public void onHurt(LivingHurtEvent event) {
            if (event.getEntity() == watched) {
                events++;
                lastAmount = event.getAmount();
                lastSource = event.getSource();
            }
        }

        @SubscribeEvent
        public void onSoulLoss(ChangeSoulEnergyEvent.Loss event) {
            if (event.getEntity() == soulLossPlayer && forcedSoulLoss >= 0) {
                event.setSoulChange(forcedSoulLoss);
                forcedSoulLoss = -1;
                soulLossPlayer = null;
            }
        }
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), name));
        }
    }
}
