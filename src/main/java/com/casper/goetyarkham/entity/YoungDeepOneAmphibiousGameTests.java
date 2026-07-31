package com.casper.goetyarkham.entity;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YoungDeepOneAmphibiousGameTests {
    private static final double POSITION_TOLERANCE = 0.45D;

    private YoungDeepOneAmphibiousGameTests() {
    }

    @GameTest(template = "young_deep_one_arena")
    public static void registrationAttributesAndSpawnEgg(
            GameTestHelper helper
    ) {
        helper.assertTrue(
                ModEntities.YOUNG_DEEP_ONE.get().getCategory()
                        == MobCategory.MONSTER,
                "Young deep one entity type is not registered as MONSTER"
        );

        YoungDeepOneEntity deepOne = createDeepOne(
                helper,
                relativePosition(helper, 2.5D, 1.0D, 2.5D)
        );
        helper.assertTrue(
                deepOne instanceof Monster,
                "Young deep one is not implemented as a Monster"
        );
        assertAttribute(helper, deepOne, Attributes.MAX_HEALTH, 30.0D);
        assertAttribute(helper, deepOne, Attributes.ATTACK_DAMAGE, 5.0D);
        assertAttribute(helper, deepOne, Attributes.MOVEMENT_SPEED, 0.20D);
        assertAttribute(helper, deepOne, Attributes.FOLLOW_RANGE, 16.0D);
        helper.assertTrue(
                deepOne.canBreatheUnderwater(),
                "Young deep one does not report native underwater breathing"
        );
        helper.assertTrue(
                deepOne.getNavigation() instanceof AmphibiousPathNavigation,
                "Young deep one is not using amphibious navigation"
        );
        helper.assertTrue(
                deepOne.getMoveControl() instanceof SmoothSwimmingMoveControl,
                "Young deep one is not using three-dimensional swimming control"
        );

        ForgeSpawnEggItem egg = ModItems.YOUNG_DEEP_ONE_SPAWN_EGG.get();
        helper.assertTrue(
                egg.getType(null) == ModEntities.YOUNG_DEEP_ONE.get(),
                "Spawn egg is not bound to the young deep one entity type"
        );
        helper.assertTrue(
                egg.getColor(0) == 0x354B43,
                "Spawn egg base color is incorrect"
        );
        helper.assertTrue(
                egg.getColor(1) == 0xF2D94E,
                "Spawn egg spot color is incorrect"
        );

        deepOne.discard();
        verifySpawnEggUse(helper, egg);
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena", timeoutTicks = 260)
    public static void submergedEntityDoesNotDrown(GameTestHelper helper) {
        prepareWaterVolume(helper);
        YoungDeepOneEntity deepOne = createDeepOne(
                helper,
                relativePosition(helper, 2.5D, 2.0D, 2.5D)
        );
        deepOne.setNoAi(true);
        int initialAir = deepOne.getAirSupply();
        float initialHealth = deepOne.getHealth();

        helper.runAfterDelay(220, () -> {
            helper.assertTrue(
                    deepOne.isAlive() && deepOne.getHealth() == initialHealth,
                    "Submerged young deep one took drowning damage"
            );
            helper.assertTrue(
                    deepOne.getAirSupply() == initialAir,
                    "Submerged young deep one consumed oxygen"
            );
            deepOne.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "young_deep_one_arena", timeoutTicks = 100)
    public static void swimsUpTowardHigherTarget(GameTestHelper helper) {
        verifyAquaticMovement(
                helper,
                relativePosition(helper, 2.5D, 1.25D, 2.5D),
                relativePosition(helper, 2.5D, 4.25D, 2.5D),
                Axis.Y,
                true,
                "Young deep one did not swim upward"
        );
    }

    @GameTest(template = "young_deep_one_arena", timeoutTicks = 100)
    public static void swimsDownTowardLowerTarget(GameTestHelper helper) {
        verifyAquaticMovement(
                helper,
                relativePosition(helper, 2.5D, 4.25D, 2.5D),
                relativePosition(helper, 2.5D, 1.25D, 2.5D),
                Axis.Y,
                false,
                "Young deep one did not swim downward"
        );
    }

    @GameTest(template = "young_deep_one_arena", timeoutTicks = 100)
    public static void swimsHorizontallyTowardTarget(GameTestHelper helper) {
        verifyAquaticMovement(
                helper,
                relativePosition(helper, 1.25D, 2.25D, 2.5D),
                relativePosition(helper, 5.75D, 2.25D, 2.5D),
                Axis.X,
                true,
                "Young deep one did not swim horizontally"
        );
    }

    @GameTest(template = "young_deep_one_arena", timeoutTicks = 150)
    public static void amphibiousPathEntersWater(GameTestHelper helper) {
        prepareShoreCourse(helper);
        Vec3 start = relativePosition(helper, 1.25D, 1.0D, 2.5D);
        Vec3 target = relativePosition(helper, 6.25D, 2.0D, 2.5D);
        YoungDeepOneEntity deepOne = createDeepOne(helper, start);

        helper.assertTrue(
                deepOne.getNavigation().moveTo(
                        target.x,
                        target.y,
                        target.z,
                        1.0D
                ),
                "Amphibious navigation did not create a land-to-water path"
        );
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(
                    deepOne.getX() > absoluteX(helper, 4.0D)
                            && deepOne.isInWater(),
                    "Young deep one did not enter water from land"
            );
            deepOne.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "young_deep_one_arena", timeoutTicks = 170)
    public static void amphibiousPathReturnsToLand(GameTestHelper helper) {
        prepareShoreCourse(helper);
        Vec3 start = relativePosition(helper, 6.25D, 2.0D, 2.5D);
        Vec3 target = relativePosition(helper, 1.25D, 1.0D, 2.5D);
        YoungDeepOneEntity deepOne = createDeepOne(helper, start);

        helper.assertTrue(
                deepOne.getNavigation().moveTo(
                        target.x,
                        target.y,
                        target.z,
                        1.0D
                ),
                "Amphibious navigation did not create a water-to-land path"
        );
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(
                    deepOne.getX() < absoluteX(helper, 3.5D)
                            && !deepOne.isInWater(),
                    "Young deep one did not return to land from water"
            );
            deepOne.discard();
            helper.succeed();
        });
    }

    private static void verifyAquaticMovement(
            GameTestHelper helper,
            Vec3 start,
            Vec3 target,
            Axis axis,
            boolean positiveDirection,
            String failureMessage
    ) {
        prepareWaterVolume(helper);
        YoungDeepOneEntity deepOne = createDeepOne(helper, start);
        double startCoordinate = axis.coordinate(start);

        helper.assertTrue(
                deepOne.getNavigation().moveTo(
                        target.x,
                        target.y,
                        target.z,
                        1.0D
                ),
                "Amphibious navigation did not create an underwater path"
        );
        helper.runAfterDelay(70, () -> {
            double movement = axis.coordinate(deepOne.position())
                    - startCoordinate;
            boolean movedTowardTarget = positiveDirection
                    ? movement > POSITION_TOLERANCE
                    : movement < -POSITION_TOLERANCE;
            helper.assertTrue(movedTowardTarget, failureMessage);
            deepOne.discard();
            helper.succeed();
        });
    }

    private static YoungDeepOneEntity createDeepOne(
            GameTestHelper helper,
            Vec3 position
    ) {
        ServerLevel level = helper.getLevel();
        YoungDeepOneEntity deepOne =
                ModEntities.YOUNG_DEEP_ONE.get().create(level);
        helper.assertTrue(deepOne != null, "Failed to create young deep one");
        deepOne.setPos(position);
        deepOne.setPersistenceRequired();
        helper.assertTrue(
                level.addFreshEntity(deepOne),
                "Failed to add young deep one to the GameTest level"
        );
        return deepOne;
    }

    private static void verifySpawnEggUse(
            GameTestHelper helper,
            ForgeSpawnEggItem egg
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos clicked = helper.absolutePos(new BlockPos(5, 1, 2));
        level.setBlock(clicked, Blocks.STONE.defaultBlockState(), 3);
        ItemStack stack = new ItemStack(egg);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(clicked.above()),
                Direction.UP,
                clicked,
                false
        );
        UseOnContext context = new UseOnContext(
                level,
                null,
                InteractionHand.MAIN_HAND,
                stack,
                hit
        );
        InteractionResult result = egg.useOn(context);
        List<YoungDeepOneEntity> spawned = level.getEntitiesOfClass(
                YoungDeepOneEntity.class,
                new AABB(clicked).inflate(2.0D)
        );
        helper.assertTrue(
                result.consumesAction() && spawned.size() == 1,
                "Using the spawn egg did not create exactly one young deep one"
        );
        spawned.forEach(YoungDeepOneEntity::discard);
    }

    private static void prepareWaterVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 5; z++) {
                level.setBlock(
                        origin.offset(x, 0, z),
                        Blocks.STONE.defaultBlockState(),
                        3
                );
                for (int y = 1; y <= 5; y++) {
                    level.setBlock(
                            origin.offset(x, y, z),
                            Blocks.WATER.defaultBlockState(),
                            3
                    );
                }
            }
        }
    }

    private static void prepareShoreCourse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 5; z++) {
                level.setBlock(
                        origin.offset(x, 0, z),
                        Blocks.STONE.defaultBlockState(),
                        3
                );
                for (int y = 1; y <= 4; y++) {
                    level.setBlock(
                            origin.offset(x, y, z),
                            x >= 4
                                    ? Blocks.WATER.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState(),
                            3
                    );
                }
            }
        }
    }

    private static Vec3 relativePosition(
            GameTestHelper helper,
            double x,
            double y,
            double z
    ) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new Vec3(
                origin.getX() + x,
                origin.getY() + y,
                origin.getZ() + z
        );
    }

    private static double absoluteX(GameTestHelper helper, double x) {
        return helper.absolutePos(BlockPos.ZERO).getX() + x;
    }

    private static void assertAttribute(
            GameTestHelper helper,
            YoungDeepOneEntity deepOne,
            net.minecraft.world.entity.ai.attributes.Attribute attribute,
            double expected
    ) {
        double actual = deepOne.getAttributeValue(attribute);
        helper.assertTrue(
                Math.abs(actual - expected) < 1.0E-6D,
                "Unexpected " + attribute.getDescriptionId()
                        + ": expected " + expected + ", got " + actual
        );
    }

    private enum Axis {
        X {
            @Override
            double coordinate(Vec3 position) {
                return position.x;
            }
        },
        Y {
            @Override
            double coordinate(Vec3 position) {
                return position.y;
            }
        };

        abstract double coordinate(Vec3 position);
    }
}
