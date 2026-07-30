package com.casper.goetyarkham.chaosbag;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GhoulSpawnPositionSearchSelfTest {
    private GhoulSpawnPositionSearchSelfTest() {
    }

    static void run() {
        ordinaryGroundUsesNearbyPosition();
        airbornePlayerFindsGroundBelow();
        highPlayerFindsGroundBelow();
        caveAndInteriorPreferNearbyFloor();
        blockedSpaceIsRejected();
        liquidsAreRejected();
        unloadedColumnsAreNeverInspected();
        noSafePositionReturnsFailure();
        searchStopsAfterOnePosition();
        sampledColumnsStayInRange();
    }

    private static void ordinaryGroundUsesNearbyPosition() {
        BlockPos origin = new BlockPos(10, 65, 10);
        FakeWorld world = new FakeWorld();
        world.supportSpawnAt(origin);

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(column(origin)),
                world);
        assertEquals(origin, result.position().orElseThrow(),
                "ordinary nearby ground");
        assertEquals(
                GhoulSpawnPositionSearch.SearchPhase.NEARBY,
                result.phase().orElseThrow(),
                "ordinary ground phase");
    }

    private static void airbornePlayerFindsGroundBelow() {
        BlockPos origin = new BlockPos(0, 72, 0);
        BlockPos groundSpawn = new BlockPos(0, 65, 0);
        FakeWorld world = new FakeWorld();
        world.supportSpawnAt(groundSpawn);

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(column(origin)),
                world);
        assertEquals(groundSpawn, result.position().orElseThrow(),
                "airborne fallback position");
        assertEquals(
                GhoulSpawnPositionSearch.SearchPhase.DOWNWARD,
                result.phase().orElseThrow(),
                "airborne fallback phase");
    }

    private static void highPlayerFindsGroundBelow() {
        BlockPos origin = new BlockPos(0, 100, 0);
        BlockPos groundSpawn = new BlockPos(0, 65, 0);
        FakeWorld world = new FakeWorld();
        world.supportSpawnAt(groundSpawn);

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(column(origin)),
                world);
        assertEquals(groundSpawn, result.position().orElseThrow(),
                "ground more than ten blocks below");
        assertTrue(
                origin.getY() - result.position().orElseThrow().getY() > 10,
                "high-player setup was not actually above ten blocks");
    }

    private static void caveAndInteriorPreferNearbyFloor() {
        BlockPos origin = new BlockPos(0, 60, 0);
        BlockPos deepFloorSpawn = new BlockPos(0, 21, 0);
        BlockPos nearbyInteriorSpawn = new BlockPos(1, 60, 0);
        FakeWorld world = new FakeWorld();
        world.supportSpawnAt(deepFloorSpawn);
        world.supportSpawnAt(nearbyInteriorSpawn);

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(
                        new GhoulSpawnPositionSearch.Column(0, 0),
                        new GhoulSpawnPositionSearch.Column(1, 0)),
                world);
        assertEquals(
                nearbyInteriorSpawn,
                result.position().orElseThrow(),
                "nearby cave/building floor must beat downward fallback");
        assertEquals(
                GhoulSpawnPositionSearch.SearchPhase.NEARBY,
                result.phase().orElseThrow(),
                "interior floor phase");
    }

    private static void blockedSpaceIsRejected() {
        BlockPos origin = new BlockPos(0, 65, 0);
        BlockPos blocked = origin;
        BlockPos clear = origin.east();
        FakeWorld world = new FakeWorld();
        world.supportSpawnAt(blocked);
        world.supportSpawnAt(clear);
        world.blockedSpawnPositions.add(blocked);

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(column(blocked), column(clear)),
                world);
        assertEquals(clear, result.position().orElseThrow(),
                "blocked spawn space");
        assertTrue(
                result.rejections().getOrDefault(
                        GhoulSpawnPositionSearch.RejectionReason.COLLISION,
                        0) > 0,
                "collision rejection was not recorded");
    }

    private static void liquidsAreRejected() {
        BlockPos origin = new BlockPos(0, 65, 0);
        BlockPos water = origin;
        BlockPos lava = origin.east();
        BlockPos dry = origin.east(2);
        FakeWorld world = new FakeWorld();
        world.supportSpawnAt(water);
        world.supportSpawnAt(lava);
        world.supportSpawnAt(dry);
        world.fluidPositions.add(water);
        world.fluidPositions.add(lava);

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(column(water), column(lava), column(dry)),
                world);
        assertEquals(dry, result.position().orElseThrow(),
                "liquid positions");
        assertTrue(
                result.rejections().getOrDefault(
                        GhoulSpawnPositionSearch.RejectionReason.FLUID,
                        0) >= 2,
                "water and lava were not both rejected");
    }

    private static void unloadedColumnsAreNeverInspected() {
        BlockPos origin = new BlockPos(0, 65, 0);
        BlockPos unloaded = origin;
        BlockPos loaded = origin.east();
        FakeWorld world = new FakeWorld();
        world.supportSpawnAt(unloaded);
        world.supportSpawnAt(loaded);
        world.unloadedColumns.add(column(unloaded));

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(column(unloaded), column(loaded)),
                world);
        assertEquals(loaded, result.position().orElseThrow(),
                "loaded fallback column");
        assertFalse(
                world.evaluatedColumns.contains(column(unloaded)),
                "search inspected an unloaded column");
        assertEquals(
                1,
                result.rejections().getOrDefault(
                        GhoulSpawnPositionSearch.RejectionReason
                                .UNLOADED_COLUMN,
                        0),
                "unloaded-column rejection count");
    }

    private static void noSafePositionReturnsFailure() {
        BlockPos origin = new BlockPos(0, 100, 0);
        FakeWorld world = new FakeWorld();

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(column(origin)),
                world);
        assertTrue(result.position().isEmpty(),
                "unsafe world unexpectedly produced a position");
        assertEquals(
                "no_safe_position_in_loaded_columns",
                result.failureReason(),
                "safe skip reason");
    }

    private static void searchStopsAfterOnePosition() {
        BlockPos origin = new BlockPos(0, 65, 0);
        BlockPos first = origin;
        BlockPos second = origin.east();
        FakeWorld world = new FakeWorld();
        world.supportSpawnAt(first);
        world.supportSpawnAt(second);

        GhoulSpawnPositionSearch.SearchResult result = find(
                origin,
                List.of(column(first), column(second)),
                world);
        assertEquals(first, result.position().orElseThrow(),
                "first safe spawn");
        assertFalse(
                world.evaluatedColumns.contains(column(second)),
                "search continued after finding a safe position");
    }

    private static void sampledColumnsStayInRange() {
        BlockPos origin = new BlockPos(20, 80, -20);
        int[] sequence = {0, 16};
        int[] cursor = {0};
        List<GhoulSpawnPositionSearch.Column> columns =
                GhoulSpawnPositionSearch.sampleColumns(
                        origin,
                        bound -> sequence[(cursor[0]++) % sequence.length]);
        assertEquals(
                GhoulSpawnPositionSearch.HORIZONTAL_ATTEMPTS,
                columns.size(),
                "sample count");
        for (GhoulSpawnPositionSearch.Column column : columns) {
            assertTrue(
                    Math.abs(column.x() - origin.getX())
                            <= GhoulSpawnPositionSearch.HORIZONTAL_RADIUS,
                    "sampled x outside radius");
            assertTrue(
                    Math.abs(column.z() - origin.getZ())
                            <= GhoulSpawnPositionSearch.HORIZONTAL_RADIUS,
                    "sampled z outside radius");
        }
    }

    private static GhoulSpawnPositionSearch.SearchResult find(
            BlockPos origin,
            List<GhoulSpawnPositionSearch.Column> columns,
            FakeWorld world) {
        return GhoulSpawnPositionSearch.find(origin, columns, world);
    }

    private static GhoulSpawnPositionSearch.Column column(BlockPos position) {
        return new GhoulSpawnPositionSearch.Column(
                position.getX(), position.getZ());
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void assertFalse(boolean condition, String label) {
        assertTrue(!condition, label);
    }

    private static void assertEquals(
            Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static final class FakeWorld
            implements GhoulSpawnPositionSearch.WorldView {
        private static final int MIN_Y = -64;
        private static final int MAX_Y = 320;

        private final Set<BlockPos> sturdySupport = new HashSet<>();
        private final Set<BlockPos> fluidPositions = new HashSet<>();
        private final Set<BlockPos> dangerousPositions = new HashSet<>();
        private final Set<BlockPos> blockedSpawnPositions = new HashSet<>();
        private final Set<GhoulSpawnPositionSearch.Column> unloadedColumns =
                new HashSet<>();
        private final Set<GhoulSpawnPositionSearch.Column> evaluatedColumns =
                new HashSet<>();

        private void supportSpawnAt(BlockPos spawnPosition) {
            sturdySupport.add(spawnPosition.below());
        }

        @Override
        public int minBuildHeight() {
            return MIN_Y;
        }

        @Override
        public int maxBuildHeight() {
            return MAX_Y;
        }

        @Override
        public boolean isColumnLoaded(int blockX, int blockZ) {
            return !unloadedColumns.contains(
                    new GhoulSpawnPositionSearch.Column(blockX, blockZ));
        }

        @Override
        public GhoulSpawnPositionSearch.CandidateStatus evaluate(
                BlockPos spawnPosition) {
            evaluatedColumns.add(column(spawnPosition));
            if (!sturdySupport.contains(spawnPosition.below())) {
                return GhoulSpawnPositionSearch.CandidateStatus.UNSUPPORTED;
            }
            if (fluidPositions.contains(spawnPosition)
                    || fluidPositions.contains(spawnPosition.above())) {
                return GhoulSpawnPositionSearch.CandidateStatus.FLUID;
            }
            if (dangerousPositions.contains(spawnPosition.below())
                    || dangerousPositions.contains(spawnPosition)
                    || dangerousPositions.contains(spawnPosition.above())) {
                return GhoulSpawnPositionSearch.CandidateStatus
                        .DANGEROUS_BLOCK;
            }
            if (blockedSpawnPositions.contains(spawnPosition)) {
                return GhoulSpawnPositionSearch.CandidateStatus.COLLISION;
            }
            return GhoulSpawnPositionSearch.CandidateStatus.SAFE;
        }
    }
}
