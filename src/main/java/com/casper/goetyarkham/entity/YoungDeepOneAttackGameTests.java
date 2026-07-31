package com.casper.goetyarkham.entity;

import com.mojang.authlib.GameProfile;
import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YoungDeepOneAttackGameTests {
    private static final double TEST_Z = 2.5D;
    private static final float EXPECTED_DAMAGE = 5.0F;

    private YoungDeepOneAttackGameTests() {
    }

    @GameTest(template = "young_deep_one_arena")
    public static void swipeDamagesOnceAtHitFrameAndRespectsCooldown(
            GameTestHelper helper) {
        AttackFixture fixture = createFixture(helper, 1.375D, 3.175D);
        startSwipe(fixture);
        tickGoal(fixture.goal(), SwipeAttackSequence.HIT_TICK - 1);
        helper.assertTrue(
                fixture.player().damageCalls() == 0,
                "Swipe caused damage before its configured hit tick"
        );

        fixture.goal().tick();
        helper.assertTrue(
                fixture.player().damageCalls() == 1
                        && fixture.player().lastIncomingDamage()
                        == EXPECTED_DAMAGE,
                "Swipe did not apply the current attack-damage attribute at the hit tick"
        );
        Vec3 firstKnockback = fixture.player().getDeltaMovement();
        Vec3 awayFromAttacker = fixture.player().position()
                .subtract(fixture.deepOne().position())
                .multiply(1.0D, 0.0D, 1.0D)
                .normalize();
        helper.assertTrue(
                firstKnockback.horizontalDistanceSqr() > 1.0E-4D
                        && firstKnockback.dot(awayFromAttacker) > 0.0D,
                "Swipe did not apply one standard knockback impulse away from "
                        + "the attacker; movement=" + firstKnockback
        );

        tickGoal(
                fixture.goal(),
                SwipeAttackSequence.ANIMATION_TICKS
                        - SwipeAttackSequence.HIT_TICK
        );
        helper.assertTrue(
                fixture.player().damageCalls() == 1,
                "One swipe applied damage more than once"
        );
        helper.assertTrue(
                fixture.player().getDeltaMovement().equals(firstKnockback),
                "One swipe applied knockback more than once"
        );
        helper.assertTrue(
                !fixture.deepOne().isSwipeAttacking(),
                "Swipe state remained active after the animation duration"
        );

        fixture.goal().tick();
        helper.assertTrue(
                !fixture.deepOne().isSwipeAttacking(),
                "Swipe restarted immediately during its cooldown"
        );
        fixture.deepOne().tickCount =
                SwipeAttackSequence.COOLDOWN_TICKS - 1;
        fixture.goal().tick();
        helper.assertTrue(
                !fixture.deepOne().isSwipeAttacking(),
                "Swipe restarted one tick before its cooldown ended"
        );
        fixture.deepOne().tickCount = SwipeAttackSequence.COOLDOWN_TICKS;
        fixture.goal().tick();
        helper.assertTrue(
                fixture.deepOne().isSwipeAttacking(),
                "Swipe did not become available when its cooldown ended"
        );

        cleanupFixture(fixture);
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void swipeMissesWhenTargetLeavesReach(
            GameTestHelper helper) {
        AttackFixture fixture = createFixture(helper, 1.375D, 3.175D);
        startSwipe(fixture);
        tickGoal(fixture.goal(), SwipeAttackSequence.HIT_TICK - 1);
        moveTestPlayer(helper, fixture.player(), 6.0D);
        fixture.goal().tick();
        tickGoal(
                fixture.goal(),
                SwipeAttackSequence.ANIMATION_TICKS
                        - SwipeAttackSequence.HIT_TICK
        );

        helper.assertTrue(
                fixture.player().damageCalls() == 0,
                "Swipe damaged a target that left melee reach before the hit tick"
        );
        assertNoHorizontalKnockback(
                helper,
                fixture.player(),
                "Out-of-range swipe applied knockback"
        );
        cleanupFixture(fixture);
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void swipeCannotDamageThroughSolidWall(
            GameTestHelper helper) {
        AttackFixture fixture = createFixture(helper, 1.375D, 3.3D);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos wallBase = origin.offset(2, 1, 2);
        helper.getLevel().setBlock(
                wallBase,
                Blocks.STONE.defaultBlockState(),
                3
        );
        helper.getLevel().setBlock(
                wallBase.above(),
                Blocks.STONE.defaultBlockState(),
                3
        );
        startSwipe(fixture);
        tickGoal(fixture.goal(), SwipeAttackSequence.HIT_TICK);

        helper.assertTrue(
                fixture.player().damageCalls() == 0,
                "Swipe damaged its target through a solid wall"
        );
        assertNoHorizontalKnockback(
                helper,
                fixture.player(),
                "Blocked swipe applied knockback"
        );
        cleanupFixture(fixture);
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void swipeRespectsTargetKnockbackResistance(
            GameTestHelper helper
    ) {
        AttackFixture fixture = createFixture(helper, 1.375D, 3.175D);
        fixture.player().getAttribute(Attributes.KNOCKBACK_RESISTANCE)
                .setBaseValue(1.0D);
        startSwipe(fixture);
        tickGoal(fixture.goal(), SwipeAttackSequence.HIT_TICK);

        helper.assertTrue(
                fixture.player().damageCalls() == 1
                        && fixture.player().lastIncomingDamage()
                        == EXPECTED_DAMAGE,
                "Knockback resistance incorrectly prevented swipe damage"
        );
        assertNoHorizontalKnockback(
                helper,
                fixture.player(),
                "Knockback resistance was not applied by standard melee logic"
        );
        cleanupFixture(fixture);
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void swipeStillDamagesOnceUnderwater(
            GameTestHelper helper) {
        AttackFixture fixture = createFixture(helper, 1.375D, 3.175D);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = 1; x <= 4; x++) {
            for (int y = 1; y <= 2; y++) {
                helper.getLevel().setBlock(
                        origin.offset(x, y, 2),
                        Blocks.WATER.defaultBlockState(),
                        3
                );
            }
        }
        fixture.deepOne().tick();
        helper.assertTrue(
                fixture.deepOne().isInWater(),
                "Young deep one was not submerged for the water attack test"
        );
        startSwipe(fixture);
        tickGoal(fixture.goal(), SwipeAttackSequence.ANIMATION_TICKS);

        helper.assertTrue(
                fixture.player().damageCalls() == 1
                        && fixture.player().lastIncomingDamage()
                        == EXPECTED_DAMAGE,
                "Underwater swipe did not apply exactly one attack-damage hit"
        );
        cleanupFixture(fixture);
        helper.succeed();
    }

    private static AttackFixture createFixture(
            GameTestHelper helper,
            double attackerRelativeX,
            double playerRelativeX
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        prepareFloor(level, origin);

        YoungDeepOneEntity deepOne =
                ModEntities.YOUNG_DEEP_ONE.get().create(level);
        helper.assertTrue(deepOne != null, "Failed to create young deep one");
        Vec3 attackerPosition = relativePosition(
                origin,
                attackerRelativeX,
                TEST_Z
        );
        deepOne.setPos(attackerPosition);
        deepOne.setYRot(-90.0F);
        deepOne.setYHeadRot(-90.0F);
        deepOne.yBodyRot = -90.0F;
        deepOne.setNoAi(true);
        helper.assertTrue(
                level.addFreshEntity(deepOne),
                "Failed to add young deep one to the GameTest level"
        );

        SurvivalTestPlayer player = new SurvivalTestPlayer(level);
        moveTestPlayer(helper, player, playerRelativeX);
        player.setHealth(player.getMaxHealth());
        deepOne.setTarget(player);

        return new AttackFixture(
                deepOne,
                player,
                new YoungDeepOneMeleeAttackGoal(deepOne)
        );
    }

    private static void startSwipe(AttackFixture fixture) {
        fixture.goal().start();
        fixture.goal().tick();
        if (!fixture.deepOne().isSwipeAttacking()) {
            throw new AssertionError("Swipe did not start with a target in reach");
        }
    }

    private static void tickGoal(
            YoungDeepOneMeleeAttackGoal goal,
            int ticks
    ) {
        for (int tick = 0; tick < ticks; tick++) {
            goal.tick();
        }
    }

    private static void moveTestPlayer(
            GameTestHelper helper,
            Player player,
            double relativeX
    ) {
        player.setPos(relativePosition(
                helper.absolutePos(BlockPos.ZERO),
                relativeX,
                TEST_Z
        ));
    }

    private static Vec3 relativePosition(
            BlockPos origin,
            double relativeX,
            double relativeZ
    ) {
        return new Vec3(
                origin.getX() + relativeX,
                origin.getY() + 1.0D,
                origin.getZ() + relativeZ
        );
    }

    private static void prepareFloor(ServerLevel level, BlockPos origin) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 5; z++) {
                BlockPos floor = origin.offset(x, 0, z);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                for (int y = 1; y <= 3; y++) {
                    level.setBlock(
                            floor.above(y),
                            Blocks.AIR.defaultBlockState(),
                            3
                    );
                }
            }
        }
    }

    private static void cleanupFixture(AttackFixture fixture) {
        fixture.deepOne().discard();
    }

    private static void assertNoHorizontalKnockback(
            GameTestHelper helper,
            Player player,
            String message
    ) {
        helper.assertTrue(
                player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-8D,
                message
        );
    }

    private record AttackFixture(
            YoungDeepOneEntity deepOne,
            SurvivalTestPlayer player,
            YoungDeepOneMeleeAttackGoal goal
    ) {
    }

    private static final class SurvivalTestPlayer extends Player {
        private int damageCalls;
        private float lastIncomingDamage;

        private SurvivalTestPlayer(ServerLevel level) {
            super(
                    level,
                    BlockPos.ZERO,
                    0.0F,
                    new GameProfile(
                            UUID.randomUUID(),
                            "young-deep-one-target"
                    )
            );
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            this.damageCalls++;
            this.lastIncomingDamage = amount;
            return super.hurt(source, amount);
        }

        private int damageCalls() {
            return this.damageCalls;
        }

        private float lastIncomingDamage() {
            return this.lastIncomingDamage;
        }
    }
}
