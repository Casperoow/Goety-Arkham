package com.casper.goetyarkham.chaosbag;

import com.casper.goetyarkham.effect.DreamsOfRlyehEffect;
import com.casper.goetyarkham.effect.DreamsOfRlyehEffectService;
import com.casper.goetyarkham.illager_treachery.encounter.formal.DreamsOfRlyehEncounter;
import com.casper.goetyarkham.illager_treachery.encounter.formal.FormalEncounterMetadata;
import com.casper.goetyarkham.illager_treachery.encounter.formal.TheYellowSignEncounter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ChaosBagSelfTest {
    private static final UUID PLAYER =
            UUID.fromString("e7a64136-917b-4110-8612-17f0d7309884");
    private static final ResourceLocation SOURCE =
            id("self_test");

    private ChaosBagSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        defaultAndBaseConfigurations();
        independentInstancesAndChecks();
        cultistExtraDrawsAndExhaustion();
        overrides();
        modifierAndConsequenceDetails();
        orderedMutationsAndPersistence();
        snapshotIsolation();
        ghoulAndTabletRules();
        GhoulSpawnPositionSearchSelfTest.run();
        effectAndFormalEncounterDefinitions();
        languageAndGhoulTagData();
        System.out.println("ChaosBagSelfTest: all checks passed");
    }

    private static void defaultAndBaseConfigurations() {
        ChaosBagState state = new ChaosBagState();
        assertEquals(ChaosBagLevel.NORMAL, state.level(), "new-world default level");

        assertTokens(
                ChaosBagLevel.EASY,
                1, 1, 0, 0, 0, -1, -1, -1, -2, -2);
        assertTokens(
                ChaosBagLevel.NORMAL,
                1, 0, 0, -1, -1, -1, -2, -2, -3, -4);
        assertTokens(
                ChaosBagLevel.HARD,
                0, 0, 0, -1, -1, -2, -2, -3, -3, -4, -5);
        assertTokens(
                ChaosBagLevel.EXPERT,
                0, -1, -1, -2, -2, -3, -3, -4, -4, -5, -6, -8);
    }

    private static void independentInstancesAndChecks() {
        ChaosBagSnapshot duplicateZeros = new ChaosBagSnapshot(
                ChaosBagLevel.NORMAL,
                List.of(
                        ChaosToken.number(0),
                        ChaosToken.number(0),
                        ChaosToken.number(-1)));
        ChaosCheckResult first = ChaosCheckEngine.resolve(
                request(duplicateZeros)
                        .forcedTokens(List.of(
                                ChaosToken.number(0),
                                ChaosToken.number(0)))
                        .initialDrawCount(2)
                        .build());
        assertEquals(2, first.draws().size(), "two duplicate token instances drawn");
        assertTrue(
                first.draws().get(0).instanceId()
                        != first.draws().get(1).instanceId(),
                "duplicate definitions did not remain separate instances");

        boolean rejectedThird = false;
        try {
            ChaosCheckEngine.resolve(request(duplicateZeros)
                    .forcedTokens(List.of(
                            ChaosToken.number(0),
                            ChaosToken.number(0),
                            ChaosToken.number(0)))
                    .initialDrawCount(3)
                    .build());
        } catch (IllegalArgumentException expected) {
            rejectedThird = true;
        }
        assertTrue(rejectedThird, "already removed concrete instances were redrawn");

        ChaosCheckResult playerA = ChaosCheckEngine.resolve(
                request(duplicateZeros)
                        .forcedTokens(List.of(ChaosToken.number(-1)))
                        .build());
        ChaosCheckResult playerB = ChaosCheckEngine.resolve(
                request(duplicateZeros)
                        .forcedTokens(List.of(ChaosToken.number(-1)))
                        .build());
        assertEquals(
                playerA.draws().get(0).token(),
                playerB.draws().get(0).token(),
                "different checks consumed shared token state");
    }

    private static void cultistExtraDrawsAndExhaustion() {
        ChaosBagSnapshot chain = new ChaosBagSnapshot(
                ChaosBagLevel.HARD,
                List.of(
                        ChaosToken.CULTIST,
                        ChaosToken.CULTIST,
                        ChaosToken.CULTIST,
                        ChaosToken.number(-2)));
        ChaosCheckResult result = ChaosCheckEngine.resolve(request(chain)
                .forcedTokens(List.of(
                        ChaosToken.CULTIST,
                        ChaosToken.CULTIST,
                        ChaosToken.CULTIST,
                        ChaosToken.number(-2)))
                .build());
        assertEquals(4, result.draws().size(), "cultist extra-draw chain");
        assertEquals(-2, result.tokenModifier(), "cultists are zero on hard");
        assertEquals(
                3L,
                result.consequences().stream()
                        .filter(value -> value.kind()
                                == ChaosConsequence.Kind.REMOVE_SOUL)
                        .filter(value -> value.amount() == 200)
                        .count(),
                "hard cultists did not each schedule 200 soul loss");
        assertFalse(result.temporaryBagExhausted(), "non-empty chain reported exhaustion");

        ChaosBagSnapshot onlyCultist = new ChaosBagSnapshot(
                ChaosBagLevel.EXPERT,
                List.of(ChaosToken.CULTIST));
        ChaosCheckResult exhausted = ChaosCheckEngine.resolve(
                request(onlyCultist)
                        .forcedTokens(List.of(ChaosToken.CULTIST))
                        .build());
        assertEquals(1, exhausted.draws().size(), "single cultist draw");
        assertTrue(exhausted.temporaryBagExhausted(), "empty extra draw not recorded");
    }

    private static void overrides() {
        ChaosBagSnapshot overrides = new ChaosBagSnapshot(
                ChaosBagLevel.NORMAL,
                List.of(ChaosToken.AUTO_FAIL, ChaosToken.ELDER_SIGN));
        ChaosCheckResult both = ChaosCheckEngine.resolve(request(overrides)
                .forcedTokens(List.of(
                        ChaosToken.AUTO_FAIL,
                        ChaosToken.ELDER_SIGN))
                .initialDrawCount(2)
                .build());
        assertFalse(both.success(), "auto-fail did not beat elder sign");
        assertTrue(
                both.overrides().contains(
                        ChaosOverride.AUTO_FAIL_OVER_ELDER_SIGN),
                "combined override was not reported");

        ChaosCheckResult elder = ChaosCheckEngine.resolve(request(
                new ChaosBagSnapshot(
                        ChaosBagLevel.NORMAL,
                        List.of(ChaosToken.ELDER_SIGN)))
                .forcedTokens(List.of(ChaosToken.ELDER_SIGN))
                .build());
        assertTrue(elder.success(), "elder sign did not succeed at target");
        assertEquals(4, elder.finalValue(), "elder sign final value");
    }

    private static void modifierAndConsequenceDetails() {
        ChaosBagSnapshot zero = new ChaosBagSnapshot(
                ChaosBagLevel.NORMAL,
                List.of(ChaosToken.number(0)));
        ChaosCheckModifier equipment =
                new ChaosCheckModifier(id("equipment"), 1);
        ChaosCheckResult modified = ChaosCheckEngine.resolve(
                request(zero)
                        .modifiers(List.of(equipment))
                        .forcedTokens(List.of(ChaosToken.number(0)))
                        .build());
        assertEquals(List.of(equipment), modified.otherModifiers(), "modifier details");
        assertEquals(1, modified.otherModifier(), "other modifier sum");
        assertEquals(1, modified.totalModifier(), "total modifier");
        assertEquals(4, modified.finalValue(), "modifier formula");
        assertTrue(modified.success(), "equipment modifier was not applied");

        ChaosCheckResult easyCultist = ChaosCheckEngine.resolve(request(
                new ChaosBagSnapshot(
                        ChaosBagLevel.EASY,
                        List.of(ChaosToken.CULTIST)))
                .forcedTokens(List.of(ChaosToken.CULTIST))
                .build());
        assertEquals(-1, easyCultist.tokenModifier(), "easy cultist value");
        assertTrue(easyCultist.consequences().stream().anyMatch(value ->
                        value.kind() == ChaosConsequence.Kind.REMOVE_SOUL
                                && value.amount() == 100),
                "easy cultist soul consequence");

        ChaosCheckResult hardGhoul = ChaosCheckEngine.resolve(request(
                new ChaosBagSnapshot(
                        ChaosBagLevel.HARD,
                        List.of(ChaosToken.GHOUL)))
                .forcedTokens(List.of(ChaosToken.GHOUL))
                .build());
        assertTrue(hardGhoul.consequences().stream().anyMatch(value ->
                        value.kind() == ChaosConsequence.Kind.SPAWN_GHOUL
                                && value.amount() == 1),
                "hard failed ghoul did not schedule one spawn");
    }

    private static void orderedMutationsAndPersistence() {
        ChaosBagState state = new ChaosBagState();
        ResourceLocation added = id("added");
        ResourceLocation removed = id("removed");
        state.add(ChaosToken.number(-8), 1, added);
        state.setLevel(ChaosBagLevel.EXPERT);
        state.remove(ChaosToken.number(-8), 2, removed);
        assertEquals(
                0L,
                count(state.effectiveTokens(), ChaosToken.number(-8)),
                "ordered add/remove replay");
        int mutationCount = state.mutations().size();
        state.setLevel(ChaosBagLevel.EASY);
        assertEquals(
                mutationCount,
                state.mutations().size(),
                "level switch cleared mutations");
        state.setLevel(ChaosBagLevel.EXPERT);
        assertEquals(
                0L,
                count(state.effectiveTokens(), ChaosToken.number(-8)),
                "mutations were not replayed after switching back");
        state.clearSource(removed);
        assertEquals(
                2L,
                count(state.effectiveTokens(), ChaosToken.number(-8)),
                "source undo did not replay remaining records");

        int before = state.mutations().size();
        ChaosBagState.OperationResult insufficient =
                state.remove(ChaosToken.number(99), 1, id("insufficient"));
        assertFalse(insufficient.success(), "insufficient removal succeeded");
        assertEquals(before, state.mutations().size(), "partial removal record was appended");

        ChaosBagState emptyProtection = new ChaosBagState();
        java.util.LinkedHashMap<ChaosToken, Integer> counts =
                new java.util.LinkedHashMap<>();
        emptyProtection.effectiveTokens().forEach(token ->
                counts.merge(token, 1, Integer::sum));
        List<ChaosToken> distinct = new ArrayList<>(counts.keySet());
        for (int index = 0; index < distinct.size() - 1; index++) {
            ChaosToken token = distinct.get(index);
            assertTrue(emptyProtection.remove(
                            token,
                            counts.get(token),
                            id("empty_" + index))
                    .success(), "setup removal for empty protection");
        }
        ChaosToken last = distinct.get(distinct.size() - 1);
        assertFalse(emptyProtection.remove(
                        last,
                        counts.get(last),
                        id("empty_last"))
                .success(), "mutation emptied the chaos bag");

        ChaosBagSavedData persisted = new ChaosBagSavedData(state);
        CompoundTag tag = persisted.save(new CompoundTag());
        ChaosBagSavedData restored = ChaosBagSavedData.load(tag);
        assertEquals(state.level(), restored.level(), "persisted level");
        assertEquals(state.mutations(), restored.mutations(), "persisted mutations");
        assertEquals(
                state.effectiveTokens(),
                restored.effectiveTokens(),
                "persisted effective configuration");
    }

    private static void snapshotIsolation() {
        ChaosBagState state = new ChaosBagState();
        ChaosBagSnapshot captured = state.snapshot();
        state.add(ChaosToken.number(99), 1, id("later"));
        state.setLevel(ChaosBagLevel.EXPERT);
        assertEquals(
                ChaosBagLevel.NORMAL,
                captured.level(),
                "in-progress snapshot level changed");
        assertEquals(
                0L,
                count(captured.tokens(), ChaosToken.number(99)),
                "in-progress snapshot token list changed");
    }

    private static void ghoulAndTabletRules() {
        ChaosBagSnapshot ghoul = new ChaosBagSnapshot(
                ChaosBagLevel.NORMAL,
                List.of(ChaosToken.GHOUL));
        ChaosCheckResult uncapped = ChaosCheckEngine.resolve(
                request(ghoul)
                        .environment(new ChaosEnvironmentSnapshot(100_000))
                        .forcedTokens(List.of(ChaosToken.GHOUL))
                        .build());
        assertEquals(-100_000, uncapped.tokenModifier(), "normal ghoul -X was capped");

        ChaosBagSnapshot tablet = new ChaosBagSnapshot(
                ChaosBagLevel.HARD,
                List.of(ChaosToken.TABLET));
        ChaosCheckResult success = ChaosCheckEngine.resolve(
                ChaosCheckRequest.builder(
                                PLAYER,
                                SOURCE,
                                ChaosBaseValueSource.FIXED,
                                100,
                                4,
                                tablet,
                                bound -> 0)
                        .environment(new ChaosEnvironmentSnapshot(1))
                        .forcedTokens(List.of(ChaosToken.TABLET))
                        .build());
        assertTrue(success.success(), "tablet success setup failed");
        assertTrue(success.consequences().stream().anyMatch(value ->
                        value.kind() == ChaosConsequence.Kind.DAMAGE
                                && value.amount() == 2),
                "tablet damage incorrectly depended on failure");
        assertTrue(success.consequences().stream().anyMatch(value ->
                        value.kind() == ChaosConsequence.Kind.REMOVE_SOUL
                                && value.amount() == 100),
                "hard tablet soul loss missing on success");
    }

    private static void effectAndFormalEncounterDefinitions() {
        DreamsOfRlyehEffect effect = new DreamsOfRlyehEffect();
        assertTrue(effect.getCurativeItems().isEmpty(), "effect has milk cures");
        assertEquals(
                24_000,
                DreamsOfRlyehEffectService.DURATION_TICKS,
                "effect duration");
        assertEquals(
                -1,
                DreamsOfRlyehEffectService.WILLPOWER_MODIFIER,
                "effect willpower modifier");
        assertEquals(
                -1_000,
                DreamsOfRlyehEffectService.SOUL_CAPACITY_MODIFIER,
                "effect soul maximum modifier");

        DreamsOfRlyehEncounter dreams = new DreamsOfRlyehEncounter();
        TheYellowSignEncounter yellow = new TheYellowSignEncounter();
        assertEquals(id("dreams_of_rlyeh"), dreams.id(), "dreams id");
        assertEquals(id("the_yellow_sign"), yellow.id(), "yellow sign id");
        assertTrue(dreams.defaultEnabled() && yellow.defaultEnabled(), "formal enabled defaults");
        assertEquals(1L, dreams.defaultWeight(), "dreams weight");
        assertEquals(1L, yellow.defaultWeight(), "yellow weight");
        assertEquals(
                Set.of(
                        FormalEncounterMetadata.TREACHERY,
                        FormalEncounterMetadata.OMEN),
                dreams.encounterTags(),
                "formal tags");
        assertEquals(
                FormalEncounterMetadata.APOSTLES_OF_CTHULHU,
                dreams.encounterGroup().orElseThrow(),
                "dreams group");
        assertEquals(
                FormalEncounterMetadata.APOSTLES_OF_HASTUR,
                yellow.encounterGroup().orElseThrow(),
                "yellow group");
    }

    private static void languageAndGhoulTagData() throws IOException {
        Path enPath = Path.of(
                "src/main/resources/assets/goetyarkham/lang/en_us.json");
        Path zhPath = Path.of(
                "src/main/resources/assets/goetyarkham/lang/zh_cn.json");
        JsonObject en = JsonParser.parseReader(Files.newBufferedReader(enPath))
                .getAsJsonObject();
        JsonObject zh = JsonParser.parseReader(Files.newBufferedReader(zhPath))
                .getAsJsonObject();
        assertEquals(en.keySet(), zh.keySet(), "English/Chinese language key set");

        Set<String> required = Set.of(
                "effect.goetyarkham.dreams_of_rlyeh",
                "encounter.goetyarkham.dreams_of_rlyeh.name",
                "encounter.goetyarkham.dreams_of_rlyeh.description",
                "encounter.goetyarkham.the_yellow_sign.name",
                "encounter.goetyarkham.the_yellow_sign.description",
                "message.goetyarkham.chaos_check",
                "chaos_check.override.goetyarkham.auto_fail",
                "chaos_check.override.goetyarkham.elder_sign",
                "chaos_check.override.goetyarkham.auto_fail_over_elder_sign");
        assertTrue(en.keySet().containsAll(required), "required language key missing");

        JsonObject ghoulTag = JsonParser.parseReader(Files.newBufferedReader(
                        Path.of("src/main/resources/data/goetyarkham/tags/"
                                + "entity_types/ghouls.json")))
                .getAsJsonObject();
        Set<String> values = new HashSet<>();
        ghoulTag.getAsJsonArray("values")
                .forEach(value -> values.add(value.getAsString()));
        assertTrue(values.contains("graveyard:ghoul"), "hostile ghoul tag entry");
        assertTrue(values.contains("graveyard:ghouling"), "owned ghouling tag entry");
    }

    private static ChaosCheckRequest.Builder request(
            ChaosBagSnapshot snapshot) {
        return ChaosCheckRequest.builder(
                        PLAYER,
                        SOURCE,
                        ChaosBaseValueSource.FIXED,
                        3,
                        4,
                        snapshot,
                        bound -> 0)
                .modifiers(List.<ChaosCheckModifier>of());
    }

    private static void assertTokens(
            ChaosBagLevel level, int... numericValues) {
        List<ChaosToken> expected = new ArrayList<>();
        for (int value : numericValues) {
            expected.add(ChaosToken.number(value));
        }
        expected.add(ChaosToken.GHOUL);
        expected.add(ChaosToken.GHOUL);
        expected.add(ChaosToken.CULTIST);
        expected.add(ChaosToken.TABLET);
        expected.add(ChaosToken.AUTO_FAIL);
        expected.add(ChaosToken.ELDER_SIGN);
        assertEquals(expected, level.baseTokens(), level + " exact base configuration");
    }

    private static long count(List<ChaosToken> tokens, ChaosToken token) {
        return tokens.stream().filter(token::equals).count();
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("goetyarkham", path);
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
}
