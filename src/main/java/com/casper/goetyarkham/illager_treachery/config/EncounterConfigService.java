package com.casper.goetyarkham.illager_treachery.config;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.illager_treachery.data.IllagerTreacherySavedData;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterRegistry;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterSettings;
import com.casper.goetyarkham.illager_treachery.encounter.IllagerTreacheryEncounter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;

public final class EncounterConfigService {
    public static final String FILE_NAME =
            "goetyarkham-illager_treachery-encounters.toml";

    private static final Map<MinecraftServer, EncounterConfigService> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Path path;
    private final EncounterRegistry registry;
    private Map<ResourceLocation, EncounterConfigEntry> entries = Map.of();
    private boolean initialized;
    private boolean writesBlockedByInvalidFile;

    public EncounterConfigService(Path path, EncounterRegistry registry) {
        this.path = path.toAbsolutePath();
        this.registry = registry;
    }

    public static EncounterConfigService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(
                server,
                ignored -> new EncounterConfigService(
                        server.getWorldPath(LevelResource.ROOT)
                                .resolve("serverconfig")
                                .resolve(FILE_NAME),
                        EncounterRegistry.INSTANCE));
    }

    public static void remove(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public static void syncAllLoadedServers() {
        List<EncounterConfigService> services;
        synchronized (INSTANCES) {
            services = List.copyOf(INSTANCES.values());
        }
        services.forEach(service -> {
            Operation operation = service.sync();
            if (!operation.success()) {
                GoetyArkham.LOGGER.error(
                        "[illager_treachery] Could not sync a newly registered "
                                + "Java encounter into {}: {}",
                        service.path(),
                        operation.message());
            }
        });
    }

    public synchronized Operation initialize(
            IllagerTreacherySavedData savedData) {
        if (initialized) {
            return Operation.success(false, "already initialized");
        }
        EncounterConfigFile.LoadResult loaded =
                EncounterConfigFile.read(path, this::logError);
        if (loaded.status() == EncounterConfigFile.Status.INVALID) {
            entries = defaultsForAvailable();
            initialized = true;
            writesBlockedByInvalidFile = true;
            return Operation.failure(
                    "invalid TOML; retained safe in-memory defaults and did not rewrite "
                            + path);
        }

        Map<ResourceLocation, EncounterConfigEntry> candidate =
                loaded.status() == EncounterConfigFile.Status.MISSING
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(loaded.entries());
        boolean changed = syncInto(candidate);
        boolean migrationNeeded =
                !savedData.encounterConfigMigrated();
        if (migrationNeeded) {
            applyLegacyOverrides(candidate, savedData);
            changed = changed || !savedData.legacyEncounterOverrides().isEmpty();
        }
        if (loaded.status() == EncounterConfigFile.Status.MISSING || changed) {
            Operation write = writeAndInstall(candidate);
            if (!write.success()) {
                entries = Map.copyOf(candidate);
                initialized = true;
                return write;
            }
        } else {
            entries = Map.copyOf(candidate);
        }
        initialized = true;
        if (migrationNeeded) {
            savedData.markEncounterConfigMigrated();
        }
        return Operation.success(
                changed || loaded.status() == EncounterConfigFile.Status.MISSING,
                "loaded " + path);
    }

    public synchronized Operation reload() {
        return reload(null);
    }

    public synchronized Operation reload(
            IllagerTreacherySavedData savedData) {
        EncounterConfigFile.LoadResult loaded =
                EncounterConfigFile.read(path, this::logError);
        if (loaded.status() != EncounterConfigFile.Status.VALID) {
            if (loaded.status() == EncounterConfigFile.Status.INVALID) {
                writesBlockedByInvalidFile = true;
            }
            return Operation.failure(
                    "could not reload " + path
                            + "; last valid in-memory configuration was retained");
        }
        Map<ResourceLocation, EncounterConfigEntry> candidate =
                new LinkedHashMap<>(loaded.entries());
        writesBlockedByInvalidFile = false;
        boolean synced = syncInto(candidate);
        boolean migrationNeeded = savedData != null
                && !savedData.encounterConfigMigrated();
        if (migrationNeeded) {
            applyLegacyOverrides(candidate, savedData);
            synced = synced || !savedData.legacyEncounterOverrides().isEmpty();
        }
        if (synced) {
            Operation write = writeAndInstall(candidate);
            if (!write.success()) {
                return write;
            }
        } else {
            entries = Map.copyOf(candidate);
        }
        initialized = true;
        if (migrationNeeded) {
            savedData.markEncounterConfigMigrated();
        }
        return Operation.success(synced, "reloaded " + path);
    }

    public synchronized Operation sync() {
        if (writesBlockedByInvalidFile) {
            return Operation.failure(
                    "the TOML is invalid; fix it and run encounter reload before sync");
        }
        EncounterConfigFile.LoadResult loaded =
                EncounterConfigFile.read(path, this::logError);
        if (loaded.status() == EncounterConfigFile.Status.INVALID) {
            writesBlockedByInvalidFile = true;
            return Operation.failure(
                    "the TOML became invalid; it was not overwritten");
        }
        Map<ResourceLocation, EncounterConfigEntry> candidate =
                loaded.status() == EncounterConfigFile.Status.VALID
                        ? new LinkedHashMap<>(loaded.entries())
                        : new LinkedHashMap<>(entries);
        boolean changed = syncInto(candidate);
        if (loaded.status() == EncounterConfigFile.Status.MISSING) {
            return writeAndInstall(candidate);
        }
        if (!changed) {
            return Operation.success(false, "all discovered encounters are already present");
        }
        return writeAndInstall(candidate);
    }

    public synchronized Operation setEnabled(
            ResourceLocation id, boolean enabled) {
        if (writesBlockedByInvalidFile) {
            return invalidFileWriteFailure();
        }
        Map<ResourceLocation, EncounterConfigEntry> candidate =
                candidateForMutation();
        if (candidate == null) {
            return invalidFileWriteFailure();
        }
        EncounterConfigEntry current = candidate.get(id);
        if (current == null) {
            return Operation.failure("unknown encounter id: " + id);
        }
        candidate.put(id, new EncounterConfigEntry(enabled, current.weight()));
        return writeAndInstall(candidate);
    }

    public synchronized Operation setWeight(
            ResourceLocation id, long weight) {
        if (writesBlockedByInvalidFile) {
            return invalidFileWriteFailure();
        }
        if (weight < 0L) {
            return Operation.failure("encounter weight cannot be negative");
        }
        Map<ResourceLocation, EncounterConfigEntry> candidate =
                candidateForMutation();
        if (candidate == null) {
            return invalidFileWriteFailure();
        }
        EncounterConfigEntry current = candidate.get(id);
        if (current == null) {
            return Operation.failure("unknown encounter id: " + id);
        }
        candidate.put(id, new EncounterConfigEntry(current.enabled(), weight));
        return writeAndInstall(candidate);
    }

    public synchronized Operation reset(ResourceLocation id) {
        if (writesBlockedByInvalidFile) {
            return invalidFileWriteFailure();
        }
        IllagerTreacheryEncounter encounter = registry.get(id).orElse(null);
        if (encounter == null) {
            return Operation.failure(
                    "encounter is unavailable and has no current definition: " + id);
        }
        Map<ResourceLocation, EncounterConfigEntry> candidate =
                candidateForMutation();
        if (candidate == null) {
            return invalidFileWriteFailure();
        }
        candidate.put(id, defaults(encounter));
        return writeAndInstall(candidate);
    }

    public synchronized EncounterSettings effectiveSettings(
            IllagerTreacheryEncounter encounter) {
        EncounterConfigEntry value =
                entries.getOrDefault(encounter.id(), defaults(encounter));
        return new EncounterSettings(value.enabled(), value.weight());
    }

    /**
     * Captures configuration once so a concurrent reload cannot mix settings
     * while an event snapshot is being assembled.
     */
    public synchronized Function<IllagerTreacheryEncounter, EncounterSettings>
    snapshotSettings() {
        Map<ResourceLocation, EncounterConfigEntry> captured =
                Map.copyOf(entries);
        return encounter -> {
            EncounterConfigEntry value =
                    captured.getOrDefault(encounter.id(), defaults(encounter));
            return new EncounterSettings(value.enabled(), value.weight());
        };
    }

    public synchronized List<ListEntry> list() {
        Map<ResourceLocation, IllagerTreacheryEncounter> definitions =
                registry.definitions();
        java.util.Set<ResourceLocation> ids =
                new java.util.TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        ids.addAll(entries.keySet());
        ids.addAll(definitions.keySet());
        List<ListEntry> result = new ArrayList<>();
        for (ResourceLocation id : ids) {
            IllagerTreacheryEncounter definition = definitions.get(id);
            EncounterConfigEntry value = entries.get(id);
            if (value == null && definition != null) {
                value = defaults(definition);
            }
            boolean available = definition != null;
            result.add(new ListEntry(
                    id,
                    available ? definition.typeId() : null,
                    available
                            ? definition.encounterGroup().orElse(null)
                            : null,
                    available
                            ? definition.encounterTags()
                            : java.util.Set.of(),
                    available,
                    value != null && value.enabled(),
                    value == null ? 0L : value.weight(),
                    available && value != null && value.drawable(),
                    available && definition.defaultEnabled(),
                    available ? definition.defaultWeight() : 0L));
        }
        return List.copyOf(result);
    }

    public synchronized Optional<EncounterConfigEntry> get(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    public synchronized Map<ResourceLocation, EncounterConfigEntry> entries() {
        return Map.copyOf(entries);
    }

    public Path path() {
        return path;
    }

    private Map<ResourceLocation, EncounterConfigEntry> defaultsForAvailable() {
        Map<ResourceLocation, EncounterConfigEntry> result =
                new LinkedHashMap<>();
        registry.values().forEach(
                encounter -> result.put(encounter.id(), defaults(encounter)));
        return result;
    }

    private boolean syncInto(
            Map<ResourceLocation, EncounterConfigEntry> candidate) {
        boolean changed = false;
        for (IllagerTreacheryEncounter encounter : registry.values()) {
            if (!candidate.containsKey(encounter.id())) {
                GoetyArkham.LOGGER.info(
                        "[illager_treachery] Encounter config entry missing: "
                                + "path={}, encounter={}, fields=enabled,weight; "
                                + "adding current definition defaults",
                        path,
                        encounter.id());
                candidate.put(encounter.id(), defaults(encounter));
                changed = true;
            }
        }
        return changed;
    }

    private void applyLegacyOverrides(
            Map<ResourceLocation, EncounterConfigEntry> candidate,
            IllagerTreacherySavedData savedData) {
        savedData.legacyEncounterOverrides().forEach((id, legacy) -> {
            IllagerTreacheryEncounter definition =
                    registry.get(id).orElse(null);
            EncounterConfigEntry fallback = candidate.get(id);
            if (fallback == null) {
                fallback = definition == null
                        ? new EncounterConfigEntry(false, 0L)
                        : defaults(definition);
            }
            candidate.put(id, new EncounterConfigEntry(
                    legacy.enabled() == null
                            ? fallback.enabled() : legacy.enabled(),
                    legacy.weight() == null
                            ? fallback.weight() : legacy.weight()));
        });
    }

    private Operation writeAndInstall(
            Map<ResourceLocation, EncounterConfigEntry> candidate) {
        try {
            EncounterConfigFile.writeAtomic(path, candidate);
            entries = Map.copyOf(candidate);
            initialized = true;
            writesBlockedByInvalidFile = false;
            return Operation.success(true, "wrote " + path);
        } catch (IOException exception) {
            GoetyArkham.LOGGER.error(
                    "[illager_treachery] Failed writing encounter config {}",
                    path,
                    exception);
            return Operation.failure(
                    "failed writing " + path + ": " + exception.getMessage());
        }
    }

    private Map<ResourceLocation, EncounterConfigEntry> candidateForMutation() {
        EncounterConfigFile.LoadResult loaded =
                EncounterConfigFile.read(path, this::logError);
        if (loaded.status() == EncounterConfigFile.Status.INVALID) {
            writesBlockedByInvalidFile = true;
            return null;
        }
        return loaded.status() == EncounterConfigFile.Status.VALID
                ? new LinkedHashMap<>(loaded.entries())
                : new LinkedHashMap<>(entries);
    }

    private static EncounterConfigEntry defaults(
            IllagerTreacheryEncounter encounter) {
        return new EncounterConfigEntry(
                encounter.defaultEnabled(), encounter.defaultWeight());
    }

    private void logError(String message) {
        GoetyArkham.LOGGER.error("[illager_treachery] {}", message);
    }

    private Operation invalidFileWriteFailure() {
        return Operation.failure(
                "the TOML is invalid and will not be overwritten; fix "
                        + path + " and run encounter reload first");
    }

    public record ListEntry(
            ResourceLocation id,
            ResourceLocation type,
            ResourceLocation group,
            java.util.Set<ResourceLocation> tags,
            boolean available,
            boolean enabled,
            long weight,
            boolean drawable,
            boolean defaultEnabled,
            long defaultWeight) {
        public ListEntry {
            tags = java.util.Set.copyOf(tags);
        }
    }

    public record Operation(boolean success, boolean changed, String message) {
        public static Operation success(boolean changed, String message) {
            return new Operation(true, changed, message);
        }

        public static Operation failure(String message) {
            return new Operation(false, false, message);
        }
    }
}
