package com.casper.goetyarkham.entity;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.item.KnifeDurabilityService;
import com.casper.goetyarkham.item.KnifeItem;
import com.casper.goetyarkham.item.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Covers {@link ThrownKnifeEntity}'s impact/settlement behavior by calling
 * its protected {@code onHitEntity}/{@code onHitBlock}/{@code tick} hooks
 * directly (this test class lives in the same package specifically so those
 * calls compile without reflection) rather than trying to simulate real
 * flight physics tick-by-tick. {@link com.casper.goetyarkham.item.KnifeGameTests}
 * covers everything up to the entity being spawned.
 *
 * <p>Drop assertions count Knife {@link ItemEntity}s level-wide via {@link
 * #knifeDropCount(ServerLevel)} (a non-spatial {@code ServerLevel.getEntities}
 * query, not an {@code AABB} proximity search) and compare a before/after
 * delta. An {@code AABB}-bounded search proved unreliable here for entities
 * placed far from the GameTest structure's own loaded/ticking chunk region,
 * and a delta is also immune to leftover drops earlier test methods left
 * elsewhere in the shared world.</p>
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThrownKnifeGameTests {
    private static final String BATCH = "goetyarkham:thrown_knife";

    private ThrownKnifeGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void hitEntityDealsProjectileDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = testPlayer(level, "thrown-knife-damage", 0.0D);
        try {
            // A Pig (0 armor) so the exact damage dealt isn't also mixed
            // with an unrelated armor-reduction formula.
            Pig target = pig(level, owner, 3.0D);
            float healthBefore = target.getHealth();
            ThrownKnifeEntity knife = knife(level, owner, plainStack());
            knife.onHitEntity(new EntityHitResult(target, target.position()));
            helper.assertTrue(target.getHealth() < healthBefore,
                    "Hitting an entity did not deal damage");
            helper.assertTrue(
                    healthBefore - target.getHealth() == KnifeItem.THROWN_BASE_DAMAGE,
                    "Thrown-knife damage must equal the flat base damage (no velocity scaling),"
                            + " expected " + KnifeItem.THROWN_BASE_DAMAGE
                            + " found " + (healthBefore - target.getHealth()));
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void shieldBlocksTheHitButStillCountsAsAHit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = testPlayer(level, "thrown-knife-shield-owner", 4.0D);
        // A blocking Mob rather than a TestPlayer: a raw, connection-less
        // ServerPlayer being hurt makes vanilla's LivingEntity.hurt try to
        // send a hurt-animation packet through a null connection and throw -
        // a known GameTest-environment limitation unrelated to this feature
        // (see gametest_testplayer_gotchas), not something a target-side mob
        // runs into.
        TestBlockerMob blocker = new TestBlockerMob(level);
        try {
            blocker.setPos(owner.getX() + 5.0D, owner.getY(), owner.getZ());
            level.addFreshEntity(blocker);
            blocker.beginBlockingWithShield();
            helper.assertTrue(blocker.isBlocking(), "Test setup: blocker is not in a blocking state");
            float healthBefore = blocker.getHealth();
            int dropsBefore = knifeDropCount(level);

            ThrownKnifeEntity knife = knife(level, owner, plainStack());
            // Position the knife a few blocks away and have the blocker face
            // it directly, so isDamageSourceBlocked's frontal-hit check
            // (view vector vs. source-to-target vector) is unambiguous.
            knife.setPos(blocker.getX() + 3.0D, blocker.getY(), blocker.getZ());
            blocker.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    new Vec3(knife.getX(), knife.getY(), knife.getZ()));
            knife.onHitEntity(new EntityHitResult(blocker, blocker.position()));

            helper.assertTrue(blocker.getHealth() == healthBefore,
                    "A frontal shield block should have prevented all damage");
            // Even though the hit was blocked, the physical collision still
            // occurred, so it must still settle exactly once (drop produced).
            helper.assertTrue(knifeDropCount(level) == dropsBefore + 1,
                    "A blocked-but-physical hit must still settle into exactly one drop");
            helper.succeed();
        } finally {
            owner.discard();
            blocker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void projectileProtectionReducesDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = testPlayer(level, "thrown-knife-protection-owner", 7.0D);
        try {
            Zombie unprotected = zombie(level, owner, 3.0D);
            Zombie protectedZombie = zombie(level, owner, -3.0D);

            ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
            armor.enchant(Enchantments.PROJECTILE_PROTECTION, 4);
            protectedZombie.setItemSlot(EquipmentSlot.CHEST, armor);
            protectedZombie.getAttributes().addTransientAttributeModifiers(
                    armor.getAttributeModifiers(EquipmentSlot.CHEST));

            float unprotectedHealthBefore = unprotected.getHealth();
            float protectedHealthBefore = protectedZombie.getHealth();

            knife(level, owner, plainStack())
                    .onHitEntity(new EntityHitResult(unprotected, unprotected.position()));
            knife(level, owner, plainStack())
                    .onHitEntity(new EntityHitResult(protectedZombie, protectedZombie.position()));

            float unprotectedDamage = unprotectedHealthBefore - unprotected.getHealth();
            float protectedDamage = protectedHealthBefore - protectedZombie.getHealth();
            helper.assertTrue(unprotectedDamage > 0.0F, "Test setup: unprotected zombie took no damage");
            helper.assertTrue(protectedDamage < unprotectedDamage,
                    "Projectile Protection did not reduce thrown-knife damage: unprotected="
                            + unprotectedDamage + " protected=" + protectedDamage);
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void entityHitConsumesDurabilityExactlyOnceEvenIfCallbackFiresTwice(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = testPlayer(level, "thrown-knife-once", 10.0D);
        try {
            Zombie target = zombie(level, owner, 3.0D);
            ThrownKnifeEntity knife = knife(level, owner, plainStack());
            EntityHitResult hit = new EntityHitResult(target, target.position());
            int dropsBefore = knifeDropCount(level);

            knife.onHitEntity(hit);
            helper.assertTrue(knife.isRemoved(), "Entity hit must discard the thrown knife");
            helper.assertTrue(knifeDropCount(level) == dropsBefore + 1,
                    "Expected exactly one new drop after the first hit");

            // A stray duplicate callback (same-tick double collision, etc.)
            // must be a no-op thanks to the settled guard.
            knife.onHitEntity(hit);
            knife.onHitBlock(new BlockHitResult(
                    target.position(), Direction.UP, target.blockPosition(), false));
            helper.assertTrue(knifeDropCount(level) == dropsBefore + 1,
                    "Duplicate hit callbacks must not produce a second drop");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void lastDurabilityPointBreakingProducesNoDrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = testPlayer(level, "thrown-knife-break", 13.0D);
        try {
            Zombie target = zombie(level, owner, 3.0D);
            ItemStack almostBroken = new ItemStack(ModItems.KNIFE.get());
            almostBroken.setDamageValue(almostBroken.getMaxDamage() - 1);
            ThrownKnifeEntity knife = knife(level, owner, almostBroken);
            int dropsBefore = knifeDropCount(level);

            knife.onHitEntity(new EntityHitResult(target, target.position()));

            helper.assertTrue(knifeDropCount(level) == dropsBefore,
                    "A knife that broke on this exact hit must not produce a pickup-able drop");
            helper.assertTrue(knife.isRemoved(), "The thrown knife entity must still be removed");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void blockHitDropsWithoutConsumingDurability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = testPlayer(level, "thrown-knife-block", 16.0D);
        try {
            ItemStack damaged = new ItemStack(ModItems.KNIFE.get());
            damaged.setDamageValue(80);
            ThrownKnifeEntity knife = knife(level, owner, damaged);
            BlockPos pos = new BlockPos((int) owner.getX() + 2, 1, 2);
            knife.setPos(pos.getX() + 0.5D, 1.5D, 2.5D);
            int dropsBefore = knifeDropCount(level);

            knife.onHitBlock(new BlockHitResult(
                    new Vec3(pos.getX() + 0.5D, 1.5D, 2.0D), Direction.NORTH, pos, false));

            helper.assertTrue(knife.isRemoved(), "Block hit must discard the thrown knife");
            List<? extends ItemEntity> newDrops = newKnifeDrops(level, dropsBefore);
            helper.assertTrue(newDrops.size() == 1, "Block hit must produce exactly one drop, found "
                    + newDrops.size());
            helper.assertTrue(newDrops.get(0).getItem().getDamageValue() == 80,
                    "Block hit must not change the knife's pre-throw durability damage");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void timeoutConvertsToDropWithoutConsumingDurability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = testPlayer(level, "thrown-knife-timeout", 19.0D);
        try {
            ItemStack damaged = new ItemStack(ModItems.KNIFE.get());
            damaged.setDamageValue(12);
            ThrownKnifeEntity knife = knife(level, owner, damaged);
            knife.setPos(owner.getX() + 4.0D, 1.5D, 2.5D);
            level.addFreshEntity(knife);
            forceTickCount(knife, 500);
            int dropsBefore = knifeDropCount(level);

            knife.tick();

            helper.assertTrue(knife.isRemoved(), "A timed-out thrown knife must be removed");
            List<? extends ItemEntity> newDrops = newKnifeDrops(level, dropsBefore);
            helper.assertTrue(newDrops.size() == 1, "Timeout must produce exactly one drop, found "
                    + newDrops.size());
            helper.assertTrue(newDrops.get(0).getItem().getDamageValue() == 12,
                    "Timeout must not consume any durability");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void saveAndReloadPreservesTheCarriedItemStack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = testPlayer(level, "thrown-knife-save", 22.0D);
        try {
            ItemStack original = new ItemStack(ModItems.KNIFE.get());
            original.setDamageValue(64);
            original.enchant(Enchantments.MENDING, 1);
            original.setHoverName(Component.literal("Persisted Knife"));
            ThrownKnifeEntity knife = knife(level, owner, original);
            knife.setCreativeThrow(true);

            CompoundTag saved = new CompoundTag();
            knife.addAdditionalSaveData(saved);

            ThrownKnifeEntity reloaded = new ThrownKnifeEntity(ModEntities.THROWN_KNIFE.get(), level);
            reloaded.readAdditionalSaveData(saved);

            ItemStack reloadedStack = reloaded.getKnifeItem();
            helper.assertTrue(reloadedStack.getDamageValue() == 64,
                    "Reloaded thrown knife lost its durability damage");
            helper.assertTrue(reloadedStack.getEnchantmentLevel(Enchantments.MENDING) == 1,
                    "Reloaded thrown knife lost its Mending enchantment");
            helper.assertTrue(reloadedStack.getHoverName().getString().equals("Persisted Knife"),
                    "Reloaded thrown knife lost its custom name");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    /**
     * The Unbreaking roll is genuinely random, so instead of asserting on a
     * real coin flip, this drives {@link KnifeDurabilityService} (the exact
     * method the entity itself calls) with a {@link RandomSource} whose
     * {@code nextInt} is pinned to a fixed value - deterministically forcing
     * "always ignore this durability point" and "always apply it".
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void unbreakingParticipatesInTheDurabilityRollDeterministically(
            GameTestHelper helper) {
        ItemStack unenchanted = new ItemStack(ModItems.KNIFE.get());
        boolean brokeUnenchanted = KnifeDurabilityService.damageOnEntityHit(
                unenchanted, fixedNextInt(0), null);
        helper.assertTrue(!brokeUnenchanted && unenchanted.getDamageValue() == 1,
                "An unenchanted knife must always take the durability point");

        ItemStack unbreaking = new ItemStack(ModItems.KNIFE.get());
        unbreaking.enchant(Enchantments.UNBREAKING, 1);
        boolean brokeIgnored = KnifeDurabilityService.damageOnEntityHit(
                unbreaking, fixedNextInt(1), null);
        helper.assertTrue(!brokeIgnored && unbreaking.getDamageValue() == 0,
                "With Unbreaking I and a roll forced to 'ignore', durability must not change");

        ItemStack unbreakingApplied = new ItemStack(ModItems.KNIFE.get());
        unbreakingApplied.enchant(Enchantments.UNBREAKING, 1);
        boolean brokeApplied = KnifeDurabilityService.damageOnEntityHit(
                unbreakingApplied, fixedNextInt(0), null);
        helper.assertTrue(!brokeApplied && unbreakingApplied.getDamageValue() == 1,
                "With Unbreaking I and a roll forced to 'apply', durability must still be consumed");
        helper.succeed();
    }

    private static RandomSource fixedNextInt(int forcedResult) {
        RandomSource delegate = RandomSource.create(0L);
        return new RandomSource() {
            @Override
            public RandomSource fork() {
                return delegate.fork();
            }

            @Override
            public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
                return delegate.forkPositional();
            }

            @Override
            public void setSeed(long seed) {
                delegate.setSeed(seed);
            }

            @Override
            public int nextInt() {
                return delegate.nextInt();
            }

            @Override
            public int nextInt(int bound) {
                return forcedResult;
            }

            @Override
            public long nextLong() {
                return delegate.nextLong();
            }

            @Override
            public boolean nextBoolean() {
                return delegate.nextBoolean();
            }

            @Override
            public float nextFloat() {
                return delegate.nextFloat();
            }

            @Override
            public double nextDouble() {
                return delegate.nextDouble();
            }

            @Override
            public double nextGaussian() {
                return delegate.nextGaussian();
            }
        };
    }

    private static void forceTickCount(ThrownKnifeEntity knife, int ticks) {
        knife.tickCount = ticks;
    }

    private static ThrownKnifeEntity knife(ServerLevel level, TestPlayer owner, ItemStack stack) {
        return new ThrownKnifeEntity(level, owner, stack);
    }

    private static ItemStack plainStack() {
        return new ItemStack(ModItems.KNIFE.get());
    }

    private static Zombie zombie(ServerLevel level, ServerPlayer near, double offset) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.setPos(near.getX() + offset, near.getY(), near.getZ());
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static Pig pig(ServerLevel level, ServerPlayer near, double offset) {
        Pig pig = EntityType.PIG.create(level);
        pig.setPos(near.getX() + offset, near.getY(), near.getZ());
        level.addFreshEntity(pig);
        return pig;
    }

    /**
     * Level-wide (not proximity-bounded) count of Knife {@link ItemEntity}s,
     * used for before/after deltas instead of an {@code AABB} search.
     */
    private static int knifeDropCount(ServerLevel level) {
        return level.<ItemEntity>getEntities(
                EntityTypeTest.forClass(ItemEntity.class),
                e -> e.getItem().is(ModItems.KNIFE.get())).size();
    }

    private static List<? extends ItemEntity> newKnifeDrops(ServerLevel level, int countBefore) {
        List<? extends ItemEntity> all = level.<ItemEntity>getEntities(
                EntityTypeTest.forClass(ItemEntity.class),
                e -> e.getItem().is(ModItems.KNIFE.get()));
        return all.subList(Math.min(countBefore, all.size()), all.size());
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        return player;
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

    private static final class TestBlockerMob extends Zombie {
        private TestBlockerMob(ServerLevel level) {
            super(level);
        }

        void beginBlockingWithShield() {
            ItemStack shield = new ItemStack(Items.SHIELD);
            this.setItemInHand(InteractionHand.MAIN_HAND, shield);
            this.startUsingItem(InteractionHand.MAIN_HAND);
            this.useItemRemaining -= 5;
        }
    }
}
