package com.casper.goetyarkham.illager_treachery.encounter.formal;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.chaosbag.ChaosBagLevel;
import com.casper.goetyarkham.chaosbag.ChaosBagSnapshot;
import com.casper.goetyarkham.entity.YoungDeepOneEntity;
import com.casper.goetyarkham.illager_treachery.TreacheryContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YoungDeepOneEncounterGameTests {
    private YoungDeepOneEncounterGameTests() {
    }

    @GameTest(template = "young_deep_one_arena")
    public static void encounterSpawnsExactlyOneAtSafePlayerPosition(
            GameTestHelper helper
    ) throws Exception {
        ServerLevel level = helper.getLevel();
        prepareFlatArena(helper);
        Vec3 playerPosition = relative(helper, 6.5D, 1.0D, 5.5D);
        EncounterTestPlayer player = new EncounterTestPlayer(
                level,
                playerPosition
        );
        AtomicInteger extraDraws = new AtomicInteger();

        new YoungDeepOneEncounter().execute(new EncounterExecutionContext(
                treacheryContext(),
                player,
                () -> {
                    extraDraws.incrementAndGet();
                    return true;
                }
        ));

        List<YoungDeepOneEntity> spawned = spawned(level, playerPosition);
        helper.assertTrue(spawned.size() == 1, "Encounter did not spawn exactly one entity");
        helper.assertTrue(
                spawned.get(0).position().distanceToSqr(playerPosition)
                        < 1.0E-8D,
                "Safe player position was not used as the first candidate"
        );
        helper.assertTrue(
                player.messageKeys.equals(List.of(
                        "encounter.goetyarkham.young_deep_one.description"
                )),
                "Encounter description was not sent exactly once"
        );
        helper.assertTrue(
                extraDraws.get() == 0,
                "Encounter unexpectedly requested a chaos-bag extra draw"
        );
        cleanup(spawned, player);
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void encounterUsesNearestSafeFallbackWhenPlayerIsBlocked(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        prepareFlatArena(helper);
        Vec3 playerPosition = relative(helper, 6.5D, 1.0D, 5.5D);
        level.setBlock(
                BlockPos.containing(playerPosition),
                Blocks.STONE.defaultBlockState(),
                3
        );
        EncounterTestPlayer player = new EncounterTestPlayer(
                level,
                playerPosition
        );

        helper.assertTrue(
                YoungDeepOneEncounter.spawnFor(player),
                "Encounter failed despite a safe fallback within eight blocks"
        );
        List<YoungDeepOneEntity> spawned = spawned(level, playerPosition);
        helper.assertTrue(spawned.size() == 1, "Fallback spawned the wrong entity count");
        YoungDeepOneEntity deepOne = spawned.get(0);
        helper.assertTrue(
                deepOne.position().distanceToSqr(playerPosition) > 0.0D
                        && deepOne.position().distanceToSqr(playerPosition)
                        <= YoungDeepOneSpawnPositionSearch.SEARCH_RADIUS_SQUARED,
                "Fallback position was not inside the three-dimensional radius"
        );
        helper.assertTrue(
                !level.getBlockCollisions(deepOne, deepOne.getBoundingBox())
                        .iterator().hasNext(),
                "Fallback entity intersects a solid block"
        );
        cleanup(spawned, player);
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void encounterAllowsSafeWaterPosition(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        clearArena(helper);
        Vec3 playerPosition = relative(helper, 6.5D, 2.0D, 5.5D);
        BlockPos center = BlockPos.containing(playerPosition);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 0; y++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(
                            center.offset(x, y, z),
                            Blocks.WATER.defaultBlockState(),
                            3
                    );
                }
            }
        }
        EncounterTestPlayer player = new EncounterTestPlayer(
                level,
                playerPosition
        );

        helper.assertTrue(
                YoungDeepOneEncounter.spawnFor(player),
                "Encounter rejected a safe water spawn"
        );
        List<YoungDeepOneEntity> spawned = spawned(level, playerPosition);
        helper.assertTrue(spawned.size() == 1, "Water spawn count was not one");
        helper.assertTrue(
                spawned.get(0).position().distanceToSqr(playerPosition)
                        < 1.0E-8D,
                "Safe player water position was not preferred"
        );
        cleanup(spawned, player);
        helper.succeed();
    }

    @GameTest(template = "young_deep_one_arena")
    public static void encounterRejectsLavaAndUsesDryFallback(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        prepareFlatArena(helper);
        Vec3 playerPosition = relative(helper, 6.5D, 1.0D, 5.5D);
        BlockPos center = BlockPos.containing(playerPosition);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(
                        center.offset(x, 0, z),
                        Blocks.LAVA.defaultBlockState(),
                        3
                );
            }
        }
        EncounterTestPlayer player = new EncounterTestPlayer(
                level,
                playerPosition
        );

        helper.assertTrue(
                YoungDeepOneEncounter.spawnFor(player),
                "Encounter failed to find a dry fallback around lava"
        );
        List<YoungDeepOneEntity> spawned = spawned(level, playerPosition);
        helper.assertTrue(spawned.size() == 1, "Lava fallback count was not one");
        YoungDeepOneEntity deepOne = spawned.get(0);
        helper.assertTrue(
                !level.getFluidState(deepOne.blockPosition())
                        .is(net.minecraft.tags.FluidTags.LAVA),
                "Encounter spawned the entity in lava"
        );
        cleanup(spawned, player);
        helper.succeed();
    }

    private static TreacheryContext treacheryContext() {
        return new TreacheryContext(
                UUID.randomUUID(),
                0L,
                Set.of(),
                Map.of(),
                Set.of(),
                Set.of(),
                true,
                new ChaosBagSnapshot(
                        ChaosBagLevel.NORMAL,
                        ChaosBagLevel.NORMAL.baseTokens()
                )
        );
    }

    private static List<YoungDeepOneEntity> spawned(
            ServerLevel level,
            Vec3 center
    ) {
        return level.getEntitiesOfClass(
                YoungDeepOneEntity.class,
                new AABB(center, center).inflate(10.0D)
        );
    }

    private static void prepareFlatArena(GameTestHelper helper) {
        clearArena(helper);
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = -3; x <= 15; x++) {
            for (int z = -3; z <= 13; z++) {
                level.setBlock(
                        origin.offset(x, 0, z),
                        Blocks.STONE.defaultBlockState(),
                        3
                );
            }
        }
    }

    private static void clearArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = -3; x <= 15; x++) {
            for (int y = 0; y <= 10; y++) {
                for (int z = -3; z <= 13; z++) {
                    level.setBlock(
                            origin.offset(x, y, z),
                            Blocks.AIR.defaultBlockState(),
                            3
                    );
                }
            }
        }
    }

    private static Vec3 relative(
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

    private static void cleanup(
            List<YoungDeepOneEntity> spawned,
            EncounterTestPlayer player
    ) {
        spawned.forEach(YoungDeepOneEntity::discard);
        player.discard();
    }

    private static final class EncounterTestPlayer extends ServerPlayer {
        private final List<String> messageKeys = new ArrayList<>();

        private EncounterTestPlayer(ServerLevel level, Vec3 position) {
            super(
                    level.getServer(),
                    level,
                    new GameProfile(UUID.randomUUID(), "encounter-target")
            );
            this.setPos(position);
        }

        @Override
        public void sendSystemMessage(Component message) {
            if (message.getContents() instanceof TranslatableContents contents) {
                this.messageKeys.add(contents.getKey());
            } else {
                this.messageKeys.add(message.getString());
            }
        }
    }
}
