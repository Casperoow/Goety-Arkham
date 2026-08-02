package com.casper.goetyarkham.illager_treachery;

import com.casper.goetyarkham.command.IllagerTreacheryCommandSelfTest;
import com.casper.goetyarkham.illager_treachery.data.IllagerTreacherySavedData;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterRegistry;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterSettings;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterSnapshot;
import com.casper.goetyarkham.illager_treachery.encounter.IllagerTreacheryEncounter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class IllagerTreacherySelfTest {
    private IllagerTreacherySelfTest() {
    }

    public static void main(String[] args) throws Exception {
        probabilityFormula();
        multiplayerNormalization();
        deterministicProbabilityChecks();
        dailyProgress();
        cooldownDecisionPolicy();
        cooldownAndPersistence();
        playerEligibility();
        playerResolutionPolicy();
        encounterRegistryAndSnapshot();
        extraDrawLimit();
        mergingDeduplicationAndLock();
        EncounterDataSelfTest.run();
        IllagerTreacheryCommandSelfTest.run();
        System.out.println("IllagerTreacherySelfTest: all checks passed");
    }

    private static void probabilityFormula() {
        TreacherySettings settings = TreacherySettings.DEFAULTS;
        assertClose(0.0D, IllagerTreacheryMath.baseProbability(999, settings), 0.0D);
        assertClose(0.075D, IllagerTreacheryMath.baseProbability(1_000, settings), 1.0E-12D);
        assertClose(0.1711D, IllagerTreacheryMath.baseProbability(5_000, settings), 5.0E-5D);
        assertClose(0.2125D, IllagerTreacheryMath.baseProbability(10_000, settings), 5.0E-5D);
        assertClose(0.3086D, IllagerTreacheryMath.baseProbability(50_000, settings), 5.0E-5D);
        assertClose(0.35D, IllagerTreacheryMath.baseProbability(100_000, settings), 1.0E-12D);
        assertClose(0.35D, IllagerTreacheryMath.baseProbability(Integer.MAX_VALUE, settings), 1.0E-12D);
        assertClose(4.98D, IllagerTreacheryMath.expectedValidDaysWithGuarantee(0.075D, 6), 0.01D);
        assertClose(3.95D, IllagerTreacheryMath.expectedValidDaysWithGuarantee(
                IllagerTreacheryMath.baseProbability(5_000, settings), 6), 0.01D);
        assertClose(3.58D, IllagerTreacheryMath.expectedValidDaysWithGuarantee(
                IllagerTreacheryMath.baseProbability(10_000, settings), 6), 0.01D);
        assertClose(2.89D, IllagerTreacheryMath.expectedValidDaysWithGuarantee(
                IllagerTreacheryMath.baseProbability(50_000, settings), 6), 0.01D);
        assertClose(2.64D, IllagerTreacheryMath.expectedValidDaysWithGuarantee(0.35D, 6), 0.01D);

        TreacherySettings broken = TreacherySettings.sanitize(
                true,
                1_000,
                1_000,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                0,
                -1L,
                -1);
        double safe = IllagerTreacheryMath.baseProbability(1_000, broken);
        assertTrue(Double.isFinite(safe), "broken config produced a non-finite probability");
        assertTrue(safe >= 0.0D && safe <= 1.0D, "broken config escaped [0, 1]");
        assertTrue(broken.curveMaximumSoul() > broken.minimumSoul(), "curve maximum was not repaired");
        assertTrue(broken.guaranteedValidDays() == 1, "guarantee lower bound");
        assertTrue(broken.cooldownTicks() == 0L, "cooldown lower bound");
        assertTrue(broken.maximumExtraDraws() == 0, "extra draw lower bound");
    }

    private static void multiplayerNormalization() {
        double base = 0.35D;
        assertClose(base, IllagerTreacheryMath.normalizedProbability(base, 1), 1.0E-12D);
        for (int count : new int[]{2, 5, 20, 100}) {
            double normalized =
                    IllagerTreacheryMath.normalizedProbability(base, count);
            double[] players = new double[count];
            java.util.Arrays.fill(players, normalized);
            assertClose(
                    base,
                    IllagerTreacheryMath.combinedProbability(players),
                    1.0E-12D);
        }
        double low = IllagerTreacheryMath.normalizedProbability(0.075D, 3);
        double high = IllagerTreacheryMath.normalizedProbability(0.35D, 3);
        assertTrue(high > low, "different soul maxima lost their individual probabilities");
    }

    private static void deterministicProbabilityChecks() {
        TreacherySettings settings = TreacherySettings.DEFAULTS;
        SequenceRandom dailyRandom = new SequenceRandom(0.0D, 0.999D);
        ProbabilityDecisionEngine.Outcome<Integer> daily =
                ProbabilityDecisionEngine.rollDaily(
                        java.util.List.of(1_000, 100_000),
                        value -> value,
                        settings,
                        dailyRandom);
        assertTrue(daily.randomDrawn(), "daily random draw was not recorded");
        assertTrue(daily.successes().contains(1_000), "fixed daily success missing");
        assertFalse(daily.successes().contains(100_000), "fixed daily failure missing");

        SequenceRandom ritualRandom = new SequenceRandom(0.074D, 0.351D);
        ProbabilityDecisionEngine.Outcome<Integer> ritual =
                ProbabilityDecisionEngine.rollDirect(
                        java.util.List.of(1_000, 100_000),
                        value -> value,
                        settings,
                        ritualRandom);
        assertTrue(ritual.successes().equals(Set.of(1_000)),
                "ritual did not use unnormalized individual probabilities");
    }

    private static void dailyProgress() {
        DailyProgress progress =
                new DailyProgress(0, DailyProgress.UNINITIALIZED_DAY);
        assertFalse(progress.enterDay(10L), "initial observation must not decide");
        assertTrue(progress.enterDay(11L), "new day was not observed");
        assertFalse(progress.enterDay(11L), "same day repeated");
        progress.recordValidDay();
        assertTrue(progress.validDays() == 1, "valid day did not increment");

        assertTrue(progress.enterDay(15L), "multi-day jump should process once");
        progress.recordValidDay();
        assertTrue(progress.validDays() == 2, "multi-day jump backfilled decisions");
        assertFalse(progress.enterDay(9L), "time rollback repeated a decision");
        assertFalse(progress.enterDay(15L), "rollback recovery repeated high-water day");
        assertTrue(progress.enterDay(16L), "first day above high-water mark was missed");

        for (int day = progress.validDays(); day < 6; day++) {
            progress.recordValidDay();
        }
        assertTrue(progress.validDays() == 6, "six valid day guarantee progress");
        progress.reset();
        assertTrue(progress.validDays() == 0, "successful start did not reset progress");
    }

    private static void cooldownAndPersistence() {
        IllagerTreacherySavedData data = new IllagerTreacherySavedData();
        assertFalse(data.observeMinecraftDay(20L), "first persisted day observation");
        assertTrue(data.observeMinecraftDay(21L), "persisted next day");
        data.recordValidDecisionDay();
        data.recordValidDecisionDay();
        data.restartCooldown(5_000L, 24_000L);
        assertTrue(data.cooldownRemaining(5_100L) == 23_900L, "cooldown remaining");
        data.setEnabledOverride(false);

        ResourceLocation id = id("persisted");
        data.setEncounterEnabled(id, false);
        data.setEncounterWeight(id, 42L);
        CompoundTag serialized = data.save(new CompoundTag());
        IllagerTreacherySavedData restored =
                IllagerTreacherySavedData.load(serialized);
        assertTrue(restored.validDecisionDays() == 2, "valid days were not persisted");
        assertTrue(restored.lastProcessedMinecraftDay() == 21L, "day marker was not persisted");
        assertTrue(restored.cooldownEndGameTick() == 29_000L, "cooldown was not persisted");
        assertTrue(restored.enabledOverride().orElse(true) == false, "enabled override persistence");

        TestEncounter encounter = new TestEncounter(id, true, 1L);
        EncounterSettings effective = restored.effectiveSettings(encounter);
        assertFalse(effective.enabled(), "encounter enabled override persistence");
        assertTrue(effective.weight() == 42L, "encounter weight override persistence");
    }

    private static void cooldownDecisionPolicy() {
        assertTrue(TriggerDecisionPolicy.choose(false, false, false)
                        == TriggerDecisionPolicy.Mode.RANDOM,
                "ordinary source did not enter probability mode");
        assertTrue(TriggerDecisionPolicy.choose(false, false, true)
                        == TriggerDecisionPolicy.Mode.SKIP_COOLDOWN,
                "ordinary source did not respect cooldown");
        assertTrue(TriggerDecisionPolicy.choose(false, true, true)
                        == TriggerDecisionPolicy.Mode.GUARANTEED,
                "sixth-day guarantee did not bypass cooldown");
        assertTrue(TriggerDecisionPolicy.choose(true, false, true)
                        == TriggerDecisionPolicy.Mode.FORCED,
                "forced source did not bypass cooldown");

        IllagerTreacherySavedData data = new IllagerTreacherySavedData();
        data.restartCooldown(1_000L, 24_000L);
        long originalEnd = data.cooldownEndGameTick();
        assertTrue(data.isCoolingDown(1_001L), "cooldown did not begin after draw");
        assertTrue(data.cooldownEndGameTick() == originalEnd,
                "checking cooldown extended it");
        data.restartCooldown(2_000L, 24_000L);
        assertTrue(data.cooldownEndGameTick() == 26_000L,
                "successful start did not restart full cooldown");
    }

    private static void playerEligibility() {
        TreacherySettings settings = TreacherySettings.DEFAULTS;
        PlayerEligibility.PlayerFacts normal =
                new PlayerEligibility.PlayerFacts(true, false, false, true, false, 1_000);
        PlayerEligibility.EligibilityResult result =
                PlayerEligibility.evaluate(normal, settings);
        assertTrue(result.candidate() && result.participant(), "normal candidate");

        assertEligibility(false, false,
                new PlayerEligibility.PlayerFacts(true, true, false, true, false, 100_000),
                settings, "creative");
        assertEligibility(false, false,
                new PlayerEligibility.PlayerFacts(true, false, true, true, false, 100_000),
                settings, "spectator");
        assertEligibility(false, false,
                new PlayerEligibility.PlayerFacts(true, false, false, false, false, 100_000),
                settings, "raid-disabled dimension");
        assertEligibility(false, false,
                new PlayerEligibility.PlayerFacts(true, false, false, true, true, 100_000),
                settings, "peaceful");
        assertEligibility(false, true,
                new PlayerEligibility.PlayerFacts(true, false, false, true, false, 999),
                settings, "low-soul participant");
    }

    private static void encounterRegistryAndSnapshot() {
        EncounterRegistry registry = new EncounterRegistry();
        TestEncounter disabled = new TestEncounter(id("disabled"), true, 10L);
        TestEncounter zero = new TestEncounter(id("zero"), true, 10L);
        TestEncounter light = new TestEncounter(id("light"), true, 1L);
        TestEncounter heavy = new TestEncounter(id("heavy"), true, 3L);
        registry.register(disabled);
        registry.register(zero);
        registry.register(light);
        registry.register(heavy);

        EncounterSnapshot snapshot = registry.snapshot(encounter -> {
            if (encounter.id().equals(disabled.id())) {
                return new EncounterSettings(false, 10L);
            }
            if (encounter.id().equals(zero.id())) {
                return new EncounterSettings(true, 0L);
            }
            return new EncounterSettings(true, encounter.defaultWeight());
        });
        assertTrue(snapshot.size() == 2, "disabled/zero encounters entered pool");

        Random javaRandom = new Random(8_713_331L);
        TreacheryRandom random = javaRandom::nextLong;
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (int draw = 0; draw < 100_000; draw++) {
            ResourceLocation selected = snapshot.draw(random).orElseThrow().id();
            counts.merge(selected, 1, Integer::sum);
            assertFalse(selected.equals(disabled.id()), "disabled encounter drawn");
            assertFalse(selected.equals(zero.id()), "zero-weight encounter drawn");
        }
        double heavyRatio = counts.getOrDefault(heavy.id(), 0) / 100_000.0D;
        assertClose(0.75D, heavyRatio, 0.01D);

        EncounterSnapshot huge = new EncounterSnapshot(java.util.List.of(
                new EncounterSnapshot.Entry(light.id(), Long.MAX_VALUE, light),
                new EncounterSnapshot.Entry(heavy.id(), Long.MAX_VALUE, heavy)));
        assertTrue(huge.draw(random).isPresent(), "large total weight overflowed");

        EncounterSnapshot locked = registry.snapshot(encounter ->
                encounter.id().equals(light.id())
                        ? new EncounterSettings(true, 5L)
                        : new EncounterSettings(false, 0L));
        assertTrue(locked.size() == 1 && locked.entries().get(0).weight() == 5L,
                "snapshot did not lock effective settings");

        boolean duplicateRejected = false;
        try {
            registry.register(new TestEncounter(light.id(), true, 1L));
        } catch (IllegalStateException expected) {
            duplicateRejected = true;
        }
        assertTrue(duplicateRejected, "duplicate encounter id was silently replaced");

        EncounterSnapshot empty =
                registry.snapshot(ignored -> new EncounterSettings(false, 0L));
        assertTrue(empty.isEmpty(), "all-disabled pool should be empty");
    }

    private static void playerResolutionPolicy() {
        assertTrue(PlayerResolutionPolicy.choose(true, false)
                        == PlayerTreacheryResult.DRAWN,
                "eligible non-immune player was not drawn");
        assertTrue(PlayerResolutionPolicy.choose(true, true)
                        == PlayerTreacheryResult.IMMUNE,
                "personal immunity result missing");
        assertTrue(PlayerResolutionPolicy.choose(false, false)
                        == PlayerTreacheryResult.EXCLUDED,
                "invalid participant was not excluded");
        assertTrue(PlayerResolutionPolicy.choose(true, false)
                        == PlayerTreacheryResult.DRAWN,
                "one player's immunity leaked to another player");
    }

    private static void extraDrawLimit() {
        ExtraDrawLimiter limiter = new ExtraDrawLimiter(999);
        int executed = 1;
        for (int request = 1; request <= 999; request++) {
            assertTrue(limiter.request(), "extra request " + request + " rejected early");
            executed++;
        }
        assertFalse(limiter.request(), "1000th extra request was accepted");
        assertTrue(executed == 1_000, "initial draw was counted against extra limit");
    }

    private static void mergingDeduplicationAndLock() {
        UUID ritualPlayer = UUID.randomUUID();
        TriggerAccumulator accumulator = new TriggerAccumulator(77L);
        accumulator.merge(TriggerRequest.daily(false));
        accumulator.merge(TriggerRequest.of(TriggerSource.RITUAL, Set.of(ritualPlayer)));
        accumulator.merge(TriggerRequest.of(TriggerSource.RAID, Set.of()));
        assertTrue(accumulator.sources().equals(EnumSet.of(
                TriggerSource.DAILY, TriggerSource.RITUAL, TriggerSource.RAID)),
                "same-tick sources were not retained");
        assertTrue(accumulator.players(TriggerSource.RITUAL).contains(ritualPlayer),
                "trigger player was not retained");

        ExpiringDeduplicator perInstance = new ExpiringDeduplicator(8, 10L);
        for (String key : java.util.List.of(
                "raid:1", "ritual:1", "illager_assault:1")) {
            assertTrue(perInstance.first(key, 1L), key + " first submission");
            assertFalse(perInstance.first(key, 2L), key + " duplicate submission");
        }

        ExpiringDeduplicator deduplicator = new ExpiringDeduplicator(2, 10L);
        assertTrue(deduplicator.first("raid:1", 1L), "first instance");
        assertFalse(deduplicator.first("raid:1", 2L), "duplicate instance");
        assertTrue(deduplicator.first("ritual:1", 2L), "second instance");
        assertTrue(deduplicator.first("assault:1", 2L), "bounded third instance");
        assertTrue(deduplicator.size() == 2, "dedup cache exceeded bound");
        assertTrue(deduplicator.first("raid:1", 20L), "expired instance remained");

        TreacheryEventLock lock = new TreacheryEventLock();
        assertTrue(lock.beginPreparing(), "could not enter preparing");
        assertFalse(lock.acceptsTrigger(), "preparing accepted recursive trigger");
        lock.beginResolving();
        assertFalse(lock.acceptsTrigger(), "resolving accepted recursive trigger");
        try {
            throw new IllegalStateException("simulated encounter failure");
        } catch (IllegalStateException ignored) {
            // Expected.
        } finally {
            lock.release();
        }
        assertTrue(lock.state() == IllagerTreacheryState.IDLE,
                "exception left event lock stuck");
    }

    private static void assertEligibility(
            boolean candidate,
            boolean participant,
            PlayerEligibility.PlayerFacts facts,
            TreacherySettings settings,
            String message) {
        PlayerEligibility.EligibilityResult result =
                PlayerEligibility.evaluate(facts, settings);
        assertTrue(result.candidate() == candidate, message + " candidate");
        assertTrue(result.participant() == participant, message + " participant");
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("goetyarkham_test", path);
    }

    private static void assertClose(double expected, double actual, double tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private record TestEncounter(
            ResourceLocation id,
            boolean defaultEnabled,
            long defaultWeight) implements IllagerTreacheryEncounter {
        @Override
        public void execute(EncounterExecutionContext context) {
        }
    }

    private static final class SequenceRandom implements TreacheryRandom {
        private final double[] values;
        private int index;

        private SequenceRandom(double... values) {
            this.values = values;
        }

        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public double nextDouble() {
            if (index >= values.length) {
                throw new AssertionError("fixed random sequence exhausted");
            }
            return values[index++];
        }
    }
}
