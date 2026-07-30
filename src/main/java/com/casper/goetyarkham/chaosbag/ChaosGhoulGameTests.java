package com.casper.goetyarkham.chaosbag;

import com.casper.goetyarkham.GoetyArkham;
import com.lion.graveyard.entities.GhoulEntity;
import com.lion.graveyard.init.TGEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.stream.StreamSupport;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChaosGhoulGameTests {
    private static final int PLAYER_HEIGHT_ABOVE_FLOOR = 12;

    private ChaosGhoulGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ghoulSpawnsOnGroundBelowAirbornePlayer(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floorCenter = helper.absolutePos(BlockPos.ZERO);
        clearColumnAndBuildFloor(level, floorCenter);

        BlockPos airbornePlayerOrigin =
                floorCenter.above(PLAYER_HEIGHT_ABOVE_FLOOR);
        BlockPos expectedSpawn = floorCenter.above();
        boolean spawned = ChaosGhoulService.spawnNearby(
                level,
                airbornePlayerOrigin,
                List.of(new GhoulSpawnPositionSearch.Column(
                        floorCenter.getX(), floorCenter.getZ())));
        helper.assertTrue(spawned, "Ghoul was not spawned below an airborne player");

        List<GhoulEntity> ghouls = level.getEntitiesOfClass(
                GhoulEntity.class,
                new AABB(floorCenter).inflate(16.0D));
        helper.assertTrue(
                ghouls.size() == 1,
                "One spawn request created " + ghouls.size() + " ghouls");
        GhoulEntity ghoul = ghouls.get(0);
        helper.assertTrue(
                ghoul.getType() == TGEntities.GHOUL.get(),
                "Spawned entity was not graveyard:ghoul");
        helper.assertTrue(
                ghoul.blockPosition().equals(expectedSpawn),
                "Ghoul did not land on the expected ground: "
                        + ghoul.blockPosition() + " instead of " + expectedSpawn);
        helper.assertTrue(
                level.getBlockState(ghoul.blockPosition().below())
                        .isFaceSturdy(
                                level,
                                ghoul.blockPosition().below(),
                                Direction.UP),
                "Ghoul did not have sturdy support");
        helper.assertTrue(
                level.getFluidState(ghoul.blockPosition()).isEmpty()
                        && level.getFluidState(
                                ghoul.blockPosition().above()).isEmpty(),
                "Ghoul spawned in a fluid");
        helper.assertTrue(
                StreamSupport.stream(
                                level.getBlockCollisions(
                                                ghoul,
                                                ghoul.getBoundingBox())
                                        .spliterator(),
                                false)
                        .allMatch(shape -> shape.isEmpty()),
                "Ghoul bounding box intersected a block");
        helper.succeed();
    }

    private static void clearColumnAndBuildFloor(
            ServerLevel level, BlockPos floorCenter) {
        for (int y = 1; y <= PLAYER_HEIGHT_ABOVE_FLOOR + 2; y++) {
            level.setBlock(
                    floorCenter.above(y),
                    Blocks.AIR.defaultBlockState(),
                    3);
        }
        level.setBlock(
                floorCenter,
                Blocks.STONE.defaultBlockState(),
                3);
    }
}
