package com.casper.goetyarkham.illager_treachery.encounter.formal;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class YoungDeepOneSpawnPositionSearchSelfTest {
    private static final Vec3 ORIGIN = new Vec3(10.25D, 64.0D, -3.75D);

    private YoungDeepOneSpawnPositionSearchSelfTest() {
    }

    public static void run() {
        fallbackOffsetsCoverTheCompleteSphereInStableOrder();
        safePlayerPositionWinsImmediately();
        nearestDeterministicFallbackWins();
        searchInspectsEveryCandidateWhenNecessary();
        entityOverlapIsOnlyASecondaryFallback();
        fullyBlockedSphereReturnsFailure();
    }

    private static void fallbackOffsetsCoverTheCompleteSphereInStableOrder() {
        int expected = 0;
        for (int x = -8; x <= 8; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -8; z <= 8; z++) {
                    int squared = x * x + y * y + z * z;
                    if (squared > 0 && squared <= 64) {
                        expected++;
                    }
                }
            }
        }

        Set<YoungDeepOneSpawnPositionSearch.Offset> unique = new HashSet<>();
        int previousDistance = -1;
        for (YoungDeepOneSpawnPositionSearch.Offset offset
                : YoungDeepOneSpawnPositionSearch.fallbackOffsets()) {
            assertTrue(offset.distanceSquared() <= 64, "offset outside sphere");
            assertTrue(
                    offset.distanceSquared() >= previousDistance,
                    "offset order is not nearest-first"
            );
            assertTrue(unique.add(offset), "duplicate fallback offset");
            previousDistance = offset.distanceSquared();
        }
        assertEquals(expected, unique.size(), "complete three-dimensional sphere");
    }

    private static void safePlayerPositionWinsImmediately() {
        FakeWorld world = new FakeWorld();
        world.statuses.put(
                ORIGIN,
                YoungDeepOneSpawnPositionSearch.CandidateStatus.SAFE
        );
        YoungDeepOneSpawnPositionSearch.SearchResult result =
                YoungDeepOneSpawnPositionSearch.find(ORIGIN, world);
        assertEquals(ORIGIN, result.position().orElseThrow(), "player position");
        assertEquals(
                YoungDeepOneSpawnPositionSearch.SearchPhase.PLAYER_POSITION,
                result.phase().orElseThrow(),
                "player-position phase"
        );
        assertEquals(1, result.checkedPositions(), "player-position checks");
    }

    private static void nearestDeterministicFallbackWins() {
        FakeWorld world = new FakeWorld();
        Vec3 negativeX = ORIGIN.add(-1.0D, 0.0D, 0.0D);
        Vec3 positiveX = ORIGIN.add(1.0D, 0.0D, 0.0D);
        world.statuses.put(
                negativeX,
                YoungDeepOneSpawnPositionSearch.CandidateStatus.SAFE
        );
        world.statuses.put(
                positiveX,
                YoungDeepOneSpawnPositionSearch.CandidateStatus.SAFE
        );
        YoungDeepOneSpawnPositionSearch.SearchResult result =
                YoungDeepOneSpawnPositionSearch.find(ORIGIN, world);
        assertEquals(
                negativeX,
                result.position().orElseThrow(),
                "fixed same-distance order"
        );
    }

    private static void searchInspectsEveryCandidateWhenNecessary() {
        FakeWorld world = new FakeWorld();
        YoungDeepOneSpawnPositionSearch.Offset last =
                YoungDeepOneSpawnPositionSearch.fallbackOffsets().get(
                        YoungDeepOneSpawnPositionSearch.fallbackOffsets().size()
                                - 1
                );
        Vec3 finalCandidate = ORIGIN.add(last.dx(), last.dy(), last.dz());
        world.statuses.put(
                finalCandidate,
                YoungDeepOneSpawnPositionSearch.CandidateStatus.SAFE
        );
        YoungDeepOneSpawnPositionSearch.SearchResult result =
                YoungDeepOneSpawnPositionSearch.find(ORIGIN, world);
        assertEquals(
                finalCandidate,
                result.position().orElseThrow(),
                "last safe candidate"
        );
        assertEquals(
                YoungDeepOneSpawnPositionSearch.fallbackOffsets().size() + 1,
                result.checkedPositions(),
                "complete fallback search"
        );
    }

    private static void entityOverlapIsOnlyASecondaryFallback() {
        FakeWorld world = new FakeWorld();
        world.statuses.put(
                ORIGIN,
                YoungDeepOneSpawnPositionSearch.CandidateStatus.ENTITY_OVERLAP
        );
        Vec3 clear = ORIGIN.add(2.0D, 0.0D, 0.0D);
        world.statuses.put(
                clear,
                YoungDeepOneSpawnPositionSearch.CandidateStatus.SAFE
        );
        YoungDeepOneSpawnPositionSearch.SearchResult result =
                YoungDeepOneSpawnPositionSearch.find(ORIGIN, world);
        assertEquals(clear, result.position().orElseThrow(), "clear fallback");
        assertTrue(!result.entityOverlap(), "clear result marked overlapping");

        FakeWorld overlapOnly = new FakeWorld();
        overlapOnly.statuses.put(
                ORIGIN,
                YoungDeepOneSpawnPositionSearch.CandidateStatus.ENTITY_OVERLAP
        );
        result = YoungDeepOneSpawnPositionSearch.find(ORIGIN, overlapOnly);
        assertEquals(
                ORIGIN,
                result.position().orElseThrow(),
                "overlap-only fallback"
        );
        assertTrue(result.entityOverlap(), "overlap fallback was not marked");
    }

    private static void fullyBlockedSphereReturnsFailure() {
        FakeWorld world = new FakeWorld();
        YoungDeepOneSpawnPositionSearch.SearchResult result =
                YoungDeepOneSpawnPositionSearch.find(ORIGIN, world);
        assertTrue(result.position().isEmpty(), "blocked sphere spawned entity");
        assertEquals(
                YoungDeepOneSpawnPositionSearch.fallbackOffsets().size() + 1,
                result.checkedPositions(),
                "blocked sphere was not searched completely"
        );
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String label
    ) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual
            );
        }
    }

    private static final class FakeWorld
            implements YoungDeepOneSpawnPositionSearch.WorldView {
        private final Map<Vec3, YoungDeepOneSpawnPositionSearch.CandidateStatus>
                statuses = new HashMap<>();

        @Override
        public YoungDeepOneSpawnPositionSearch.CandidateStatus evaluate(
                Vec3 position,
                boolean allowTriggerPlayerOverlap
        ) {
            return this.statuses.getOrDefault(
                    position,
                    YoungDeepOneSpawnPositionSearch.CandidateStatus.COLLISION
            );
        }
    }
}
