package com.casper.goetyarkham.illager_treachery;

import com.casper.goetyarkham.illager_treachery.config.EncounterConfigEntry;
import com.casper.goetyarkham.illager_treachery.config.EncounterConfigFile;
import com.casper.goetyarkham.illager_treachery.config.EncounterConfigService;
import com.casper.goetyarkham.illager_treachery.data.IllagerTreacherySavedData;
import com.casper.goetyarkham.illager_treachery.encounter.DataDrivenEncounter;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterDefinitionException;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterDefinitionParser;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterDefinitionSetLoader;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterParseContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterRegistry;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterSettings;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterSnapshot;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterType;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterTypeRegistry;
import com.casper.goetyarkham.illager_treachery.encounter.IllagerTreacheryEncounter;
import com.casper.goetyarkham.illager_treachery.encounter.type.BuiltInEncounterTypes;
import com.casper.goetyarkham.illager_treachery.encounter.type.ExtraDrawEncounterType;
import com.casper.goetyarkham.illager_treachery.encounter.type.MessageEncounterType;
import com.casper.goetyarkham.illager_treachery.encounter.type.MobEffectEncounterType;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class EncounterDataSelfTest {
    private static final ResourceLocation DARKNESS =
            new ResourceLocation("minecraft", "darkness");
    private static final MobEffect TEST_DARKNESS =
            new TestMobEffect();
    private static final EncounterParseContext PARSE_CONTEXT =
            new EncounterParseContext(id -> id.equals(DARKNESS)
                    ? Optional.of(TEST_DARKNESS) : Optional.empty());

    private EncounterDataSelfTest() {
    }

    static void run() throws Exception {
        jsonValidationAndFormalData();
        formalResourcesAreLoadable();
        tomlSyncReloadResetAndDlcLifecycle();
        invalidTomlRetainsLastValidState();
        weightedSnapshotAndReloadIsolation();
        extraDrawOncePerPlayerPerEvent();
        legacySavedDataMigrationIsOneShot();
    }

    private static void jsonValidationAndFormalData() throws Exception {
        EncounterTypeRegistry types = newTypes();
        ResourceLocation messageId = id("valid_message");
        DataDrivenEncounter<?> message = parse(
                messageId,
                """
                {
                  "schema_version": 1,
                  "type": "goetyarkham:message",
                  "default_enabled": true,
                  "default_weight": 3,
                  "data": {"translation_key": "message.test"}
                }
                """,
                types);
        assertTrue(message.defaultEnabled(), "valid JSON lost default enabled");
        assertTrue(message.defaultWeight() == 3L, "valid JSON lost default weight");
        assertTrue(message.data() instanceof MessageEncounterType.Data data
                        && data.translationKey().equals("message.test"),
                "message parameters were not parsed immutably");
        List<Component> playerOneMessages = new ArrayList<>();
        List<Component> playerTwoMessages = new ArrayList<>();
        MessageEncounterType.deliver(
                (MessageEncounterType.Data) message.data(),
                playerOneMessages::add);
        assertTrue(playerOneMessages.size() == 1
                        && playerTwoMessages.isEmpty(),
                "message encounter affected a player other than its target");

        expectDefinitionFailure(
                messageId,
                validRoot().replace("\"schema_version\": 1",
                        "\"schema_version\": 2"),
                types,
                "unknown schema_version");
        expectDefinitionFailure(
                messageId,
                validRoot().replace(
                        "\"goetyarkham:message\"", "\"dlc:not_registered\""),
                types,
                "unregistered type");
        expectDefinitionFailure(
                messageId,
                validRoot().replace(
                        "\"default_weight\": 3", "\"default_weight\": -1"),
                types,
                "negative default weight");

        ResourceLocation darknessId = id("darkness");
        DataDrivenEncounter<?> darkness = parse(
                darknessId,
                """
                {
                  "schema_version": 1,
                  "type": "goetyarkham:mob_effect",
                  "default_enabled": true,
                  "default_weight": 1,
                  "data": {
                    "effect": "minecraft:darkness",
                    "duration_ticks": 300,
                    "amplifier": 0,
                    "ambient": false,
                    "show_particles": false,
                    "show_icon": true,
                    "translation_key": "message.darkness"
                  }
                }
                """,
                types);
        assertTrue(darkness.data() instanceof MobEffectEncounterType.Data data
                        && data.effect() == TEST_DARKNESS
                        && data.durationTicks() == 300
                        && data.amplifier() == 0
                        && !data.ambient()
                        && !data.showParticles()
                        && data.showIcon()
                        && data.translationKey().equals("message.darkness"),
                "mob_effect parameters changed");

        expectDefinitionFailure(
                darknessId,
                """
                {
                  "schema_version": 1,
                  "type": "goetyarkham:mob_effect",
                  "default_enabled": true,
                  "default_weight": 1,
                  "data": {
                    "effect": "missing:not_an_effect",
                    "duration_ticks": 300,
                    "amplifier": 0,
                    "ambient": false,
                    "show_particles": false,
                    "show_icon": true
                  }
                }
                """,
                types,
                "bad effect id");

        Map<ResourceLocation, JsonElement> mixed = new LinkedHashMap<>();
        mixed.put(messageId, JsonParser.parseString(validRoot()));
        mixed.put(id("invalid"), JsonParser.parseString(
                validRoot().replace("\"schema_version\": 1",
                        "\"schema_version\": 99")));
        EncounterDefinitionSetLoader.Result batch =
                EncounterDefinitionSetLoader.load(
                        mixed, types, PARSE_CONTEXT, ignored -> false);
        assertTrue(batch.accepted().keySet().equals(Set.of(messageId))
                        && batch.rejected().size() == 1,
                "one invalid JSON caused a silent or partial batch state");
        EncounterDefinitionSetLoader.Result conflict =
                EncounterDefinitionSetLoader.load(
                        Map.of(messageId, JsonParser.parseString(validRoot())),
                        types,
                        PARSE_CONTEXT,
                        messageId::equals);
        assertTrue(conflict.accepted().isEmpty()
                        && conflict.rejected().size() == 1,
                "Java/data encounter id conflict was silently overwritten");

        EncounterTypeRegistry duplicateTypes = new EncounterTypeRegistry();
        BuiltInEncounterTypes.register(duplicateTypes);
        try {
            duplicateTypes.register(
                    BuiltInEncounterTypes.MESSAGE,
                    new MessageEncounterType());
            throw new AssertionError("duplicate encounter type id was accepted");
        } catch (IllegalStateException expected) {
            // Expected.
        }
        duplicateTypes.freeze();
        try {
            duplicateTypes.register(
                    id("late_type"), new MessageEncounterType());
            throw new AssertionError(
                    "encounter type registration remained mutable after freeze");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void formalResourcesAreLoadable() throws Exception {
        EncounterTypeRegistry types = newTypes();
        assertFormalResource(
                types,
                "ominous_whispers",
                BuiltInEncounterTypes.MESSAGE,
                true,
                3L);
        DataDrivenEncounter<?> darkness = assertFormalResource(
                types,
                "encroaching_darkness",
                BuiltInEncounterTypes.MOB_EFFECT,
                true,
                1L);
        assertTrue(darkness.data() instanceof MobEffectEncounterType.Data data
                        && data.durationTicks() == 300
                        && !data.showParticles()
                        && data.showIcon(),
                "formal encroaching_darkness parameters are incorrect");
        DataDrivenEncounter<?> misfortune = assertFormalResource(
                types,
                "misfortune_never_comes_alone",
                BuiltInEncounterTypes.EXTRA_DRAW,
                false,
                1L);
        assertTrue(misfortune.data() instanceof ExtraDrawEncounterType.Data data
                        && data.oncePerPlayerPerEvent(),
                "formal misfortune encounter lost its per-event guard");
    }

    private static DataDrivenEncounter<?> assertFormalResource(
            EncounterTypeRegistry types,
            String path,
            ResourceLocation type,
            boolean enabled,
            long weight) throws Exception {
        String resource = "data/goetyarkham/goetyarkham/"
                + "illager_treachery/encounters/" + path + ".json";
        try (var stream = EncounterDataSelfTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError("missing formal encounter resource " + resource);
            }
            DataDrivenEncounter<?> parsed = EncounterDefinitionParser.parse(
                    new ResourceLocation("goetyarkham", path),
                    JsonParser.parseReader(new InputStreamReader(
                            stream, StandardCharsets.UTF_8)),
                    types,
                    PARSE_CONTEXT);
            assertTrue(parsed.typeId().equals(type)
                            && parsed.defaultEnabled() == enabled
                            && parsed.defaultWeight() == weight,
                    "formal encounter metadata is incorrect: " + path);
            return parsed;
        }
    }

    private static void tomlSyncReloadResetAndDlcLifecycle()
            throws IOException {
        Path directory = Files.createTempDirectory("goetyarkham-encounters-");
        Path path = directory.resolve(EncounterConfigService.FILE_NAME);
        EncounterRegistry registry = new EncounterRegistry();
        DataDrivenEncounter<String> base =
                dataEncounter(id("base"), true, 3L);
        registry.replaceDataDriven(Map.of(base.id(), base));
        EncounterConfigService service =
                new EncounterConfigService(path, registry);
        IllagerTreacherySavedData saved = new IllagerTreacherySavedData();
        assertTrue(service.initialize(saved).success(), "new TOML was not created");
        assertEntry(service, base.id(), true, 3L, "new JSON defaults");

        assertTrue(service.setEnabled(base.id(), false).success(),
                "command-like enabled mutation failed");
        assertTrue(service.setWeight(base.id(), 9L).success(),
                "command-like weight mutation failed");

        Map<ResourceLocation, EncounterConfigEntry> beforeDlc =
                new LinkedHashMap<>(service.entries());
        beforeDlc.put(base.id(), new EncounterConfigEntry(false, 13L));
        EncounterConfigFile.writeAtomic(path, beforeDlc);
        DataDrivenEncounter<String> dlc =
                dataEncounter(new ResourceLocation("sample_dlc", "hunt"), true, 5L);
        registry.replaceDataDriven(Map.of(base.id(), base, dlc.id(), dlc));
        assertTrue(service.sync().success(), "DLC sync failed");
        assertEntry(service, base.id(), false, 13L,
                "sync overwrote a manually edited existing setting");
        assertEntry(service, dlc.id(), true, 5L, "DLC default was not synchronized");

        registry.replaceDataDriven(Map.of(base.id(), base));
        service.sync();
        EncounterConfigService.ListEntry unavailable =
                service.list().stream()
                        .filter(entry -> entry.id().equals(dlc.id()))
                        .findFirst().orElseThrow();
        assertTrue(!unavailable.available() && !unavailable.drawable(),
                "removed DLC did not remain as unavailable");

        DataDrivenEncounter<String> reinstalled =
                dataEncounter(dlc.id(), false, 1L);
        registry.replaceDataDriven(Map.of(base.id(), base, dlc.id(), reinstalled));
        service.sync();
        assertEntry(service, dlc.id(), true, 5L,
                "reinstalled DLC did not recover old TOML settings");

        service.setEnabled(base.id(), true);
        EncounterSnapshot inFlight =
                registry.snapshot(service.snapshotSettings());
        long inFlightBaseWeight = inFlight.entries().stream()
                .filter(entry -> entry.id().equals(base.id()))
                .findFirst().orElseThrow().weight();
        Map<ResourceLocation, EncounterConfigEntry> manuallyEdited =
                new LinkedHashMap<>(service.entries());
        manuallyEdited.put(base.id(), new EncounterConfigEntry(true, 17L));
        EncounterConfigFile.writeAtomic(path, manuallyEdited);
        assertTrue(service.reload().success(), "valid TOML reload failed");
        assertEntry(service, base.id(), true, 17L,
                "manual TOML edit was not loaded");
        assertTrue(inFlightBaseWeight == 13L
                        && inFlight.entries().stream()
                        .filter(entry -> entry.id().equals(base.id()))
                        .findFirst().orElseThrow().weight() == 13L,
                "TOML reload mutated an in-flight snapshot");

        assertTrue(service.reset(base.id()).success(), "reset failed");
        assertEntry(service, base.id(), true, 3L,
                "reset did not restore definition defaults");

        EncounterConfigFile.LoadResult disk =
                EncounterConfigFile.read(path, ignored -> {
                });
        assertTrue(disk.status() == EncounterConfigFile.Status.VALID,
                "command mutations did not persist in the centralized TOML");
    }

    private static void invalidTomlRetainsLastValidState() throws IOException {
        Path directory = Files.createTempDirectory("goetyarkham-bad-toml-");
        Path path = directory.resolve(EncounterConfigService.FILE_NAME);
        EncounterRegistry registry = new EncounterRegistry();
        DataDrivenEncounter<String> encounter =
                dataEncounter(id("retained"), true, 2L);
        registry.replaceDataDriven(Map.of(encounter.id(), encounter));
        EncounterConfigService service =
                new EncounterConfigService(path, registry);
        service.initialize(new IllagerTreacherySavedData());
        service.setWeight(encounter.id(), 11L);

        String invalid = """
                schema_version = 1
                [encounters."goetyarkham_test:retained"]
                enabled = true
                weight = -9
                """;
        Files.writeString(path, invalid, StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(path);
        assertTrue(!service.reload().success(), "invalid TOML reload succeeded");
        assertEntry(service, encounter.id(), true, 11L,
                "invalid reload destroyed last valid memory state");
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(path)),
                "invalid TOML was silently overwritten");
        assertTrue(!service.sync().success()
                        && !service.setWeight(encounter.id(), 12L).success()
                        && java.util.Arrays.equals(before, Files.readAllBytes(path)),
                "automatic sync or command mutation overwrote invalid TOML");

        List<String> errors = new ArrayList<>();
        EncounterConfigFile.LoadResult result =
                EncounterConfigFile.read(path, errors::add);
        assertTrue(result.status() == EncounterConfigFile.Status.INVALID,
                "negative TOML weight was accepted");
        assertTrue(errors.stream().anyMatch(error ->
                        error.contains(path.toAbsolutePath().toString())
                                && error.contains(encounter.id().toString())
                                && error.contains("weight")),
                "TOML error omitted path, encounter id, or field");
    }

    private static void weightedSnapshotAndReloadIsolation() {
        EncounterRegistry registry = new EncounterRegistry();
        IllagerTreacheryEncounter first =
                dataEncounter(id("first"), true, 3L);
        IllagerTreacheryEncounter second =
                dataEncounter(id("second"), true, 1L);
        Map<ResourceLocation, IllagerTreacheryEncounter> ordered =
                new LinkedHashMap<>();
        ordered.put(first.id(), first);
        ordered.put(second.id(), second);
        registry.replaceDataDriven(ordered);
        EncounterSnapshot snapshot = registry.snapshot(
                encounter -> new EncounterSettings(
                        true, encounter.defaultWeight()));
        assertTrue(snapshot.draw(new LongRandom(0L)).orElseThrow()
                        .id().equals(first.id()),
                "weight boundary 0 did not select first 3/4 interval");
        assertTrue(snapshot.draw(new LongRandom(3L << 61)).orElseThrow()
                        .id().equals(second.id()),
                "weight boundary 3 did not select final 1/4 interval");

        IllagerTreacheryEncounter replacement =
                dataEncounter(id("replacement"), true, 100L);
        registry.replaceDataDriven(Map.of(replacement.id(), replacement));
        assertTrue(snapshot.size() == 2
                        && snapshot.entries().stream()
                        .noneMatch(entry -> entry.id().equals(replacement.id())),
                "data reload mutated an in-flight immutable snapshot");
    }

    private static void extraDrawOncePerPlayerPerEvent() {
        ResourceLocation id = id("extra_once");
        Set<ResourceLocation> playerOne = new HashSet<>();
        Set<ResourceLocation> playerTwo = new HashSet<>();
        assertTrue(ExtraDrawEncounterType.shouldRequestExtraDraw(
                        true, id, playerOne::add),
                "first extra draw was rejected");
        assertTrue(!ExtraDrawEncounterType.shouldRequestExtraDraw(
                        true, id, playerOne::add),
                "same player's second extra draw was accepted");
        assertTrue(ExtraDrawEncounterType.shouldRequestExtraDraw(
                        true, id, playerTwo::add),
                "one player's claim leaked to another");
        assertTrue(ExtraDrawEncounterType.shouldRequestExtraDraw(
                        false, id, ignored -> false),
                "non-once extra draw incorrectly required a claim");
    }

    private static void legacySavedDataMigrationIsOneShot() throws IOException {
        Path directory = Files.createTempDirectory("goetyarkham-migration-");
        Path path = directory.resolve(EncounterConfigService.FILE_NAME);
        ResourceLocation encounterId = id("legacy");
        EncounterRegistry registry = new EncounterRegistry();
        registry.register(new TestEncounter(encounterId, true, 1L));

        IllagerTreacherySavedData oldData = new IllagerTreacherySavedData();
        oldData.setEncounterEnabled(encounterId, false);
        oldData.setEncounterWeight(encounterId, 42L);
        EncounterConfigService first =
                new EncounterConfigService(path, registry);
        first.initialize(oldData);
        assertEntry(first, encounterId, false, 42L,
                "legacy SavedData override was not migrated");
        assertTrue(oldData.encounterConfigMigrated(),
                "migration marker was not persisted in SavedData state");

        first.setEnabled(encounterId, true);
        first.setWeight(encounterId, 7L);
        CompoundTag persisted = oldData.save(new CompoundTag());
        IllagerTreacherySavedData restarted =
                IllagerTreacherySavedData.load(persisted);
        assertTrue(restarted.legacyEncounterOverrides().isEmpty(),
                "migrated SavedData continued persisting encounter overrides");
        EncounterConfigService second =
                new EncounterConfigService(path, registry);
        second.initialize(restarted);
        assertEntry(second, encounterId, true, 7L,
                "old SavedData overrides repeatedly overwrote newer TOML edits");
    }

    private static EncounterTypeRegistry newTypes() {
        EncounterTypeRegistry types = new EncounterTypeRegistry();
        BuiltInEncounterTypes.register(types);
        return types;
    }

    private static DataDrivenEncounter<?> parse(
            ResourceLocation id,
            String json,
            EncounterTypeRegistry types) throws EncounterDefinitionException {
        JsonElement root = JsonParser.parseString(json);
        return EncounterDefinitionParser.parse(
                id, root, types, PARSE_CONTEXT);
    }

    private static void expectDefinitionFailure(
            ResourceLocation id,
            String json,
            EncounterTypeRegistry types,
            String message) {
        try {
            parse(id, json, types);
            throw new AssertionError(message + " was accepted");
        } catch (EncounterDefinitionException expected) {
            // Expected.
        }
    }

    private static String validRoot() {
        return """
                {
                  "schema_version": 1,
                  "type": "goetyarkham:message",
                  "default_enabled": true,
                  "default_weight": 3,
                  "data": {"translation_key": "message.test"}
                }
                """;
    }

    private static DataDrivenEncounter<String> dataEncounter(
            ResourceLocation id, boolean enabled, long weight) {
        return new DataDrivenEncounter<>(
                id,
                new ResourceLocation("goetyarkham_test", "noop"),
                enabled,
                weight,
                new EncounterType<>() {
                    @Override
                    public String parse(
                            ResourceLocation encounterId,
                            com.google.gson.JsonObject data,
                            EncounterParseContext context) {
                        return "noop";
                    }

                    @Override
                    public void execute(
                            String data, EncounterExecutionContext context) {
                    }
                },
                "noop");
    }

    private static void assertEntry(
            EncounterConfigService service,
            ResourceLocation id,
            boolean enabled,
            long weight,
            String message) {
        EncounterConfigEntry entry = service.get(id).orElseThrow(
                () -> new AssertionError(message + ": missing " + id));
        assertTrue(entry.enabled() == enabled && entry.weight() == weight,
                message + ": got " + entry);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("goetyarkham_test", path);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record TestEncounter(
            ResourceLocation id,
            boolean defaultEnabled,
            long defaultWeight) implements IllagerTreacheryEncounter {
        @Override
        public void execute(EncounterExecutionContext context) {
        }
    }

    private record LongRandom(long value) implements TreacheryRandom {
        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public double nextDouble() {
            return 0.0D;
        }
    }

    private static final class TestMobEffect extends MobEffect {
        private TestMobEffect() {
            super(MobEffectCategory.HARMFUL, 0);
        }
    }
}
