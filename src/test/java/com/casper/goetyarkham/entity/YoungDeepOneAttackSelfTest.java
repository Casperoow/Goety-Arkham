package com.casper.goetyarkham.entity;

import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class YoungDeepOneAttackSelfTest {
    private YoungDeepOneAttackSelfTest() {
    }

    public static void main(String[] args) {
        hitOccursOnlyAtTheConfiguredFrame();
        failedHitIsNotRetriedDuringTheSameSwing();
        cooldownPreventsImmediateRestart();
        boundingBoxReachUsesEdgeDistance();
        targetRankingUsesStrengthDistanceAndUuid();
        System.out.println("YoungDeepOneAttackSelfTest: all checks passed");
    }

    private static void hitOccursOnlyAtTheConfiguredFrame() {
        SwipeAttackSequence sequence = new SwipeAttackSequence();
        AtomicInteger eligibilityChecks = new AtomicInteger();
        int damageEvents = 0;

        sequence.start(100);
        for (int tick = 1; tick <= SwipeAttackSequence.ANIMATION_TICKS; tick++) {
            boolean damage = sequence.tick(() -> {
                eligibilityChecks.incrementAndGet();
                return true;
            });
            if (tick < SwipeAttackSequence.HIT_TICK) {
                assertFalse(damage, "damage before the hit frame");
            }
            if (damage) {
                damageEvents++;
                assertEquals(
                        SwipeAttackSequence.HIT_TICK,
                        tick,
                        "damage tick"
                );
            }
        }

        assertEquals(1, damageEvents, "damage events in one swipe");
        assertEquals(1, eligibilityChecks.get(), "hit eligibility checks");
        assertFalse(sequence.isAttacking(), "animation completed");
    }

    private static void failedHitIsNotRetriedDuringTheSameSwing() {
        SwipeAttackSequence sequence = new SwipeAttackSequence();
        AtomicInteger eligibilityChecks = new AtomicInteger();
        int damageEvents = 0;

        sequence.start(200);
        for (int tick = 1; tick <= SwipeAttackSequence.ANIMATION_TICKS; tick++) {
            boolean damage = sequence.tick(() -> {
                eligibilityChecks.incrementAndGet();
                return false;
            });
            if (damage) {
                damageEvents++;
            }
        }

        assertEquals(0, damageEvents, "blocked or out-of-range damage events");
        assertEquals(
                1,
                eligibilityChecks.get(),
                "failed hit was not retried later"
        );
    }

    private static void cooldownPreventsImmediateRestart() {
        SwipeAttackSequence sequence = new SwipeAttackSequence();
        int startTick = 300;
        sequence.start(startTick);

        for (int tick = 0; tick < SwipeAttackSequence.ANIMATION_TICKS; tick++) {
            sequence.tick(() -> false);
        }

        assertFalse(
                sequence.canStart(startTick + SwipeAttackSequence.COOLDOWN_TICKS - 1),
                "cooldown final blocked tick"
        );
        assertTrue(
                sequence.canStart(startTick + SwipeAttackSequence.COOLDOWN_TICKS),
                "cooldown release tick"
        );
    }

    private static void boundingBoxReachUsesEdgeDistance() {
        AABB attacker = new AABB(0.0D, 0.0D, 0.0D, 1.25D, 0.9D, 1.25D);
        AABB overlappingTarget =
                new AABB(1.0D, 0.0D, 0.25D, 1.6D, 1.8D, 0.85D);
        AABB targetAtReach =
                new AABB(2.25D, 0.0D, 0.25D, 2.85D, 1.8D, 0.85D);
        AABB targetOutsideReach =
                new AABB(2.26D, 0.0D, 0.25D, 2.86D, 1.8D, 0.85D);

        assertTrue(
                YoungDeepOneMeleeAttackGoal.isWithinAttackReach(
                        attacker,
                        overlappingTarget
                ),
                "overlapping target"
        );
        assertTrue(
                YoungDeepOneMeleeAttackGoal.isWithinAttackReach(
                        attacker,
                        targetAtReach
                ),
                "target at one-block edge reach"
        );
        assertFalse(
                YoungDeepOneMeleeAttackGoal.isWithinAttackReach(
                        attacker,
                        targetOutsideReach
                ),
                "target outside one-block edge reach"
        );
    }

    private static void targetRankingUsesStrengthDistanceAndUuid() {
        Candidate highStrength = new Candidate(
                "high",
                8,
                1.0D,
                UUID.fromString("00000000-0000-0000-0000-000000000003")
        );
        Candidate lowStrength = new Candidate(
                "low",
                2,
                20.0D,
                UUID.fromString("00000000-0000-0000-0000-000000000004")
        );
        Candidate selected = select(List.of(highStrength, lowStrength));
        assertEquals("low", selected.name(), "lowest final strength");

        Candidate farther = new Candidate(
                "farther",
                5,
                9.0D,
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );
        Candidate nearer = new Candidate(
                "nearer",
                5,
                4.0D,
                UUID.fromString("00000000-0000-0000-0000-000000000003")
        );
        selected = select(List.of(farther, nearer));
        assertEquals("nearer", selected.name(), "nearest strength tie");

        Candidate laterUuid = new Candidate(
                "laterUuid",
                5,
                4.0D,
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );
        Candidate earlierUuid = new Candidate(
                "earlierUuid",
                5,
                4.0D,
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
        selected = select(List.of(laterUuid, earlierUuid));
        assertEquals("earlierUuid", selected.name(), "stable UUID tie");
    }

    private static Candidate select(List<Candidate> candidates) {
        return LowestStrengthPlayerTargetGoal.selectBest(
                candidates,
                Candidate::finalStrength,
                Candidate::distanceSquared,
                Candidate::uuid
        ).orElseThrow();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(
                    message + ": expected " + expected + ", got " + actual
            );
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + ": expected " + expected + ", got " + actual
            );
        }
    }

    private record Candidate(
            String name,
            int finalStrength,
            double distanceSquared,
            UUID uuid
    ) {
    }
}
