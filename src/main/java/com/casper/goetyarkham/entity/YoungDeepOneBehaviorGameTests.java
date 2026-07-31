package com.casper.goetyarkham.entity;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YoungDeepOneBehaviorGameTests {
    private static final UUID UUID_ONE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_TWO =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID UUID_THREE =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private YoungDeepOneBehaviorGameTests() {
    }

    @GameTest(template = "young_deep_one_arena")
    public static void targetsLowestFinalStrengthIncludingModifiers(
            GameTestHelper helper
    ) {
        TargetFixture fixture = new TargetFixture(helper);
        TestServerPlayer baseFive = fixture.player(
                UUID_ONE,
                "strength-five",
                new Vec3(3.5D, 1.0D, 2.5D),
                5
        );
        TestServerPlayer finalTwo = fixture.player(
                UUID_TWO,
                "strength-two",
                new Vec3(7.5D, 1.0D, 2.5D),
                5
        );
        TestServerPlayer baseOneFinalEight = fixture.player(
                UUID_THREE,
                "strength-eight",
                new Vec3(5.5D, 1.0D, 2.5D),
                1
        );
        helper.assertTrue(
                PlayerStatsService.setEquipment(
                        finalTwo,
                        StatType.STRENGTH,
                        -2
                ) && PlayerStatsService.setTemporary(
                        finalTwo,
                        StatType.STRENGTH,
                        1
                ) && PlayerStatsService.setDerived(
                        finalTwo,
                        StatType.STRENGTH,
                        -2
                ),
                "Failed to set final-strength modifiers"
        );
        helper.assertTrue(
                PlayerStatsService.setEquipment(
                        baseOneFinalEight,
                        StatType.STRENGTH,
                        7
                ),
                "Failed to set equipment strength"
        );
        helper.assertTrue(
                PlayerStatsService.getFinalValue(
                        finalTwo,
                        StatType.STRENGTH
                ) == 2,
                "Test setup did not produce final strength 2"
        );

        LowestStrengthPlayerTargetGoal goal = fixture.newGoal();
        helper.assertTrue(goal.canUse(), "Target goal found no candidates");
        goal.start();
        helper.assertTrue(
                fixture.deepOne.getTarget() == finalTwo,
                "Target goal did not choose the lowest final strength"
        );
        helper.assertTrue(
                fixture.deepOne.getTarget() != baseFive
                        && fixture.deepOne.getTarget() != baseOneFinalEight,
                "Target goal used base strength instead of final strength"
        );
        fixture.close();
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void targetTiesUseDistanceThenUuid(
            GameTestHelper helper
    ) {
        TargetFixture fixture = new TargetFixture(helper);
        TestServerPlayer farther = fixture.player(
                UUID_THREE,
                "farther",
                new Vec3(8.5D, 1.0D, 2.5D),
                4
        );
        TestServerPlayer nearer = fixture.player(
                UUID_TWO,
                "nearer",
                new Vec3(4.5D, 1.0D, 2.5D),
                4
        );
        LowestStrengthPlayerTargetGoal goal = fixture.newGoal();
        helper.assertTrue(goal.canUse(), "Distance tie test found no target");
        goal.start();
        helper.assertTrue(
                fixture.deepOne.getTarget() == nearer,
                "Equal-strength candidates were not sorted by distance"
        );

        goal.stop();
        fixture.remove(farther);
        fixture.remove(nearer);
        TestServerPlayer laterUuid = fixture.player(
                UUID_TWO,
                "later-uuid",
                new Vec3(4.5D, 1.0D, 2.5D),
                4
        );
        TestServerPlayer earlierUuid = fixture.player(
                UUID_ONE,
                "earlier-uuid",
                new Vec3(0.5D, 1.0D, 2.5D),
                4
        );
        goal = fixture.newGoal();
        helper.assertTrue(goal.canUse(), "UUID tie test found no target");
        goal.start();
        helper.assertTrue(
                fixture.deepOne.distanceToSqr(laterUuid)
                        == fixture.deepOne.distanceToSqr(earlierUuid),
                "UUID tie test players were not equidistant"
        );
        helper.assertTrue(
                fixture.deepOne.getTarget() == earlierUuid,
                "Equal strength and distance did not use stable UUID order"
        );
        fixture.close();
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void targetRemainsLockedUntilInvalid(
            GameTestHelper helper
    ) {
        TargetFixture fixture = new TargetFixture(helper);
        TestServerPlayer locked = fixture.player(
                UUID_TWO,
                "locked",
                new Vec3(4.5D, 1.0D, 2.5D),
                3
        );
        fixture.player(
                UUID_THREE,
                "initially-higher",
                new Vec3(5.5D, 1.0D, 2.5D),
                8
        );
        LowestStrengthPlayerTargetGoal goal = fixture.newGoal();
        helper.assertTrue(goal.canUse(), "Lock test found no initial target");
        goal.start();

        TestServerPlayer newLowest = fixture.player(
                UUID_ONE,
                "new-lowest",
                new Vec3(3.5D, 1.0D, 2.5D),
                1
        );
        PlayerStatsService.setBase(locked, StatType.STRENGTH, 20);
        helper.assertTrue(
                goal.canContinueToUse()
                        && fixture.deepOne.getTarget() == locked,
                "A valid target was replaced after strengths changed"
        );
        helper.getLevel().setBlock(
                BlockPos.containing(fixture.absolute(3.5D, 1.0D, 2.5D)),
                Blocks.STONE.defaultBlockState(),
                3
        );
        fixture.deepOne.getSensing().tick();
        helper.assertTrue(
                goal.canContinueToUse(),
                "A brief loss of line of sight immediately dropped the target"
        );
        helper.getLevel().setBlock(
                BlockPos.containing(fixture.absolute(3.5D, 1.0D, 2.5D)),
                Blocks.AIR.defaultBlockState(),
                3
        );

        locked.setHealth(0.0F);
        helper.assertTrue(
                !goal.canContinueToUse(),
                "Dead target remained valid"
        );
        goal.stop();
        goal = fixture.newGoal();
        helper.assertTrue(goal.canUse(), "Goal did not reacquire after death");
        goal.start();
        helper.assertTrue(
                fixture.deepOne.getTarget() == newLowest,
                "Goal did not reacquire the remaining lowest-strength player"
        );

        newLowest.setPos(fixture.absolute(30.0D, 1.0D, 2.5D));
        helper.assertTrue(
                !goal.canContinueToUse(),
                "Target outside FOLLOW_RANGE remained valid"
        );
        fixture.close();
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void targetFiltersInvalidPlayersAndOtherDimensions(
            GameTestHelper helper
    ) {
        TargetFixture fixture = new TargetFixture(helper);
        fixture.player(
                UUID_ONE,
                "creative",
                new Vec3(3.5D, 1.0D, 2.5D),
                0
        ).creative = true;
        fixture.player(
                UUID_TWO,
                "spectator",
                new Vec3(4.5D, 1.0D, 2.5D),
                0
        ).spectator = true;
        TestServerPlayer survival = fixture.player(
                UUID_THREE,
                "survival",
                new Vec3(5.5D, 1.0D, 2.5D),
                5
        );
        LowestStrengthPlayerTargetGoal goal = fixture.newGoal();
        helper.assertTrue(goal.canUse(), "Eligibility test found no target");
        goal.start();
        helper.assertTrue(
                fixture.deepOne.getTarget() == survival,
                "Creative or spectator player entered target candidates"
        );
        survival.creative = true;
        helper.assertTrue(
                !goal.canContinueToUse(),
                "Current target remained valid after entering creative mode"
        );
        survival.creative = false;

        ServerLevel otherLevel = helper.getLevel().getServer()
                .getLevel(Level.NETHER);
        helper.assertTrue(otherLevel != null, "Nether level is unavailable");
        TestServerPlayer otherDimension = new TestServerPlayer(
                otherLevel,
                UUID.randomUUID(),
                "other-dimension"
        );
        otherDimension.setPos(fixture.deepOne.position());
        fixture.deepOne.setTarget(otherDimension);
        helper.assertTrue(
                !goal.canContinueToUse(),
                "Target in another dimension remained valid"
        );
        fixture.close();
        otherDimension.discard();
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void soulErosionUsesTwentyTickSphereAndIgnoresWalls(
            GameTestHelper helper
    ) {
        AuraFixture fixture = new AuraFixture(helper);
        TestServerPlayer boundary = fixture.soulPlayer(
                "boundary",
                fixture.origin.add(8.0D, 0.0D, 0.0D),
                40
        );
        TestServerPlayer outside = fixture.soulPlayer(
                "outside",
                fixture.origin.add(8.01D, 0.0D, 0.0D),
                40
        );
        TestServerPlayer vertical = fixture.soulPlayer(
                "vertical",
                fixture.origin.add(0.0D, 7.0D, 0.0D),
                40
        );
        TestServerPlayer behindWall = fixture.soulPlayer(
                "behind-wall",
                fixture.origin.add(4.0D, 0.0D, 0.0D),
                40
        );
        helper.getLevel().setBlock(
                BlockPos.containing(fixture.origin.add(2.0D, 0.0D, 0.0D)),
                Blocks.STONE.defaultBlockState(),
                3
        );

        fixture.pulse(19);
        fixture.assertSoul(boundary, 40, "19-tick boundary player");
        fixture.assertSoul(vertical, 40, "19-tick vertical player");
        fixture.pulse(1);
        fixture.assertSoul(boundary, 30, "exactly eight blocks");
        fixture.assertSoul(outside, 40, "outside eight blocks");
        fixture.assertSoul(vertical, 30, "vertical sphere distance");
        fixture.assertSoul(behindWall, 30, "wall-independent erosion");
        fixture.pulse(1);
        fixture.assertSoul(boundary, 30, "single settlement per interval");
        fixture.close();
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void soulErosionStacksAndClampsAtZero(
            GameTestHelper helper
    ) {
        AuraFixture fixture = new AuraFixture(helper);
        TestServerPlayer twoSources = fixture.soulPlayer(
                "two-sources",
                fixture.origin.add(1.0D, 0.0D, 0.0D),
                50
        );
        YoungDeepOneEntity second = fixture.addSource();
        fixture.pulse(fixture.deepOne, 20);
        fixture.pulse(second, 20);
        fixture.assertSoul(twoSources, 30, "two independent sources");

        SoulEnergyPoolService.setSoul(twoSources, 30);
        YoungDeepOneEntity third = fixture.addSource();
        fixture.pulse(fixture.deepOne, 20);
        fixture.pulse(second, 20);
        fixture.pulse(third, 20);
        fixture.assertSoul(twoSources, 0, "three independent sources");
        MobEffectInstance wither = twoSources.getEffect(MobEffects.WITHER);
        helper.assertTrue(
                wither != null
                        && wither.getAmplifier()
                        == YoungDeepOneEntity.SOUL_EROSION_WITHER_AMPLIFIER
                        && wither.getDuration()
                        == YoungDeepOneEntity.SOUL_EROSION_WITHER_TICKS,
                "Zero soul did not receive exactly one second of Wither I"
        );

        TestServerPlayer partialSoul = fixture.soulPlayer(
                "partial-soul",
                fixture.origin.add(2.0D, 0.0D, 0.0D),
                5
        );
        YoungDeepOneEntity partialSource = fixture.addSource();
        fixture.pulse(partialSource, 20);
        fixture.assertSoul(partialSoul, 0, "one-to-nine soul clamp");
        helper.assertTrue(
                partialSoul.getEffect(MobEffects.WITHER) != null,
                "Partial soul reaching zero did not immediately receive Wither"
        );
        fixture.close();
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void soulErosionRequiresContainerAndValidSurvivalPlayer(
            GameTestHelper helper
    ) {
        AuraFixture fixture = new AuraFixture(helper);
        TestServerPlayer noContainer = fixture.player(
                "no-container",
                fixture.origin.add(1.0D, 0.0D, 0.0D)
        );
        TestServerPlayer creative = fixture.soulPlayer(
                "creative-soul",
                fixture.origin.add(2.0D, 0.0D, 0.0D),
                20
        );
        creative.creative = true;
        TestServerPlayer spectator = fixture.soulPlayer(
                "spectator-soul",
                fixture.origin.add(3.0D, 0.0D, 0.0D),
                20
        );
        spectator.spectator = true;
        TestServerPlayer recovery = fixture.soulPlayer(
                "recovery",
                fixture.origin.add(4.0D, 0.0D, 0.0D),
                0
        );

        fixture.pulse(20);
        helper.assertTrue(
                noContainer.getEffect(MobEffects.WITHER) == null,
                "Player without a soul container received Wither"
        );
        fixture.assertSoul(creative, 20, "creative player");
        fixture.assertSoul(spectator, 20, "spectator player");
        helper.assertTrue(
                recovery.getEffect(MobEffects.WITHER) != null,
                "Player already at zero soul did not receive Wither"
        );

        recovery.removeEffect(MobEffects.WITHER);
        fixture.pulse(20);
        helper.assertTrue(
                recovery.getEffect(MobEffects.WITHER) != null,
                "Zero soul in range was not refreshed after twenty ticks"
        );

        recovery.removeEffect(MobEffects.WITHER);
        SoulEnergyPoolService.addSoul(recovery, 20);
        fixture.pulse(20);
        fixture.assertSoul(recovery, 10, "restored soul priority");
        helper.assertTrue(
                recovery.getEffect(MobEffects.WITHER) == null,
                "Positive remaining soul incorrectly refreshed Wither"
        );

        SoulEnergyPoolService.setSoul(recovery, 0);
        recovery.setPos(fixture.origin.add(9.0D, 0.0D, 0.0D));
        fixture.pulse(20);
        helper.assertTrue(
                recovery.getEffect(MobEffects.WITHER) == null,
                "Out-of-range zero-soul player received Wither"
        );
        fixture.close();
        helper.succeed();
    }

    private static final class TargetFixture {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final YoungDeepOneEntity deepOne;
        private final List<TestServerPlayer> players = new ArrayList<>();

        private TargetFixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.origin = helper.absolutePos(BlockPos.ZERO);
            this.deepOne = ModEntities.YOUNG_DEEP_ONE.get().create(this.level);
            helper.assertTrue(this.deepOne != null, "Failed to create entity");
            this.deepOne.setPos(this.absolute(2.5D, 1.0D, 2.5D));
        }

        private TestServerPlayer player(
                UUID uuid,
                String name,
                Vec3 relativePosition,
                int strength
        ) {
            TestServerPlayer player = new TestServerPlayer(
                    this.level,
                    uuid,
                    name
            );
            player.setPos(this.absolute(
                    relativePosition.x,
                    relativePosition.y,
                    relativePosition.z
            ));
            this.level.players().add(player);
            this.players.add(player);
            this.helper.assertTrue(
                    PlayerStatsService.setBase(
                            player,
                            StatType.STRENGTH,
                            strength
                    ).isPresent(),
                    "Player stats capability is unavailable"
            );
            return player;
        }

        private LowestStrengthPlayerTargetGoal newGoal() {
            return new LowestStrengthPlayerTargetGoal(this.deepOne);
        }

        private Vec3 absolute(double x, double y, double z) {
            return new Vec3(
                    this.origin.getX() + x,
                    this.origin.getY() + y,
                    this.origin.getZ() + z
            );
        }

        private void remove(TestServerPlayer player) {
            this.level.players().remove(player);
            this.players.remove(player);
            player.discard();
        }

        private void close() {
            for (TestServerPlayer player : List.copyOf(this.players)) {
                this.remove(player);
            }
            this.deepOne.discard();
        }
    }

    private static final class AuraFixture {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Vec3 origin;
        private final YoungDeepOneEntity deepOne;
        private final List<YoungDeepOneEntity> sources = new ArrayList<>();
        private final List<TestServerPlayer> players = new ArrayList<>();

        private AuraFixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            BlockPos structureOrigin = helper.absolutePos(BlockPos.ZERO);
            this.origin = new Vec3(
                    structureOrigin.getX() + 2.0D,
                    structureOrigin.getY() + 1.0D,
                    structureOrigin.getZ() + 2.0D
            );
            this.deepOne = this.addSource();
        }

        private YoungDeepOneEntity addSource() {
            YoungDeepOneEntity source =
                    ModEntities.YOUNG_DEEP_ONE.get().create(this.level);
            this.helper.assertTrue(source != null, "Failed to create aura source");
            source.setPos(this.origin);
            this.sources.add(source);
            return source;
        }

        private TestServerPlayer player(String name, Vec3 position) {
            TestServerPlayer player = new TestServerPlayer(
                    this.level,
                    UUID.randomUUID(),
                    name
            );
            player.setPos(position);
            this.level.players().add(player);
            this.players.add(player);
            return player;
        }

        private TestServerPlayer soulPlayer(
                String name,
                Vec3 position,
                int soul
        ) {
            TestServerPlayer player = this.player(name, position);
            ItemStack totem = new ItemStack(
                    com.Polarice3.Goety.common.items.ModItems
                            .TOTEM_OF_SOULS.get()
            );
            ((ITotem) totem.getItem()).setTagTick(totem);
            ITotem.setMaxSoulAmount(totem, Math.max(100, soul));
            ITotem.setSoulsamount(totem, soul);
            player.getInventory().setItem(0, totem);
            SoulEnergyPoolService.refresh(player);
            this.helper.assertTrue(
                    SoulEnergyPoolService.hasContainer(player),
                    "Test soul container was not recognized"
            );
            SoulEnergyPoolService.setSoul(player, soul);
            return player;
        }

        private void pulse(int ticks) {
            this.pulse(this.deepOne, ticks);
        }

        private void pulse(YoungDeepOneEntity source, int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                source.tickSoulErosionAura();
            }
        }

        private void assertSoul(
                TestServerPlayer player,
                int expected,
                String label
        ) {
            this.helper.assertTrue(
                    SoulEnergyPoolService.getCurrentSoul(player) == expected,
                    label + ": expected " + expected + ", got "
                            + SoulEnergyPoolService.getCurrentSoul(player)
            );
        }

        private void close() {
            for (TestServerPlayer player : this.players) {
                this.level.players().remove(player);
                player.discard();
            }
            for (YoungDeepOneEntity source : this.sources) {
                source.discard();
            }
        }
    }

    private static final class TestServerPlayer extends ServerPlayer {
        private boolean creative;
        private boolean spectator;

        private TestServerPlayer(
                ServerLevel level,
                UUID uuid,
                String name
        ) {
            super(level.getServer(), level, new GameProfile(uuid, name));
        }

        @Override
        public boolean isCreative() {
            return this.creative;
        }

        @Override
        public boolean isSpectator() {
            return this.spectator;
        }

        @Override
        protected void onEffectAdded(
                MobEffectInstance effect,
                net.minecraft.world.entity.Entity source
        ) {
        }

        @Override
        protected void onEffectUpdated(
                MobEffectInstance effect,
                boolean forced,
                net.minecraft.world.entity.Entity source
        ) {
        }

        @Override
        protected void onEffectRemoved(MobEffectInstance effect) {
        }
    }
}
