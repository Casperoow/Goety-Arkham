package com.casper.goetyarkham.illager_treachery.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Strict NightConfig TOML codec. Any syntax or validation error rejects the
 * complete candidate, allowing the caller to retain its last valid state.
 */
public final class EncounterConfigFile {
    public static final int SCHEMA_VERSION = 1;

    private EncounterConfigFile() {
    }

    public static LoadResult read(Path path, Consumer<String> errorSink) {
        if (!Files.exists(path)) {
            return LoadResult.missing();
        }
        try (BufferedReader reader =
                     Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CommentedConfig root = new TomlParser().parse(reader);
            List<String> errors = new ArrayList<>();
            Object schema = root.getRaw(List.of("schema_version"));
            if (!(schema instanceof Number number)
                    || !isIntegral(number)
                    || number.longValue() != SCHEMA_VERSION) {
                errors.add(error(
                        path, "<file>", "schema_version",
                        "must be integer " + SCHEMA_VERSION));
            }

            Object tableValue = root.getRaw(List.of("encounters"));
            if (!(tableValue instanceof UnmodifiableConfig encounterTable)) {
                errors.add(error(
                        path, "<file>", "encounters",
                        "must be a TOML table"));
            }

            Map<ResourceLocation, EncounterConfigEntry> entries =
                    new LinkedHashMap<>();
            if (tableValue instanceof UnmodifiableConfig encounterTable) {
                for (Map.Entry<String, Object> raw
                        : encounterTable.valueMap().entrySet()) {
                    String rawId = raw.getKey();
                    ResourceLocation id = ResourceLocation.tryParse(rawId);
                    if (id == null) {
                        errors.add(error(
                                path, rawId, "id",
                                "is not a valid resource location"));
                        continue;
                    }
                    if (!(raw.getValue() instanceof UnmodifiableConfig entry)) {
                        errors.add(error(
                                path, id.toString(), "<entry>",
                                "must be a TOML table"));
                        continue;
                    }
                    Object enabledValue = entry.getRaw(List.of("enabled"));
                    Object weightValue = entry.getRaw(List.of("weight"));
                    boolean valid = true;
                    if (!(enabledValue instanceof Boolean)) {
                        errors.add(error(
                                path, id.toString(), "enabled",
                                "must be a boolean"));
                        valid = false;
                    }
                    if (!(weightValue instanceof Number number)
                            || !isIntegral(number)) {
                        errors.add(error(
                                path, id.toString(), "weight",
                                "must be a 64-bit integer"));
                        valid = false;
                    } else if (number.longValue() < 0L) {
                        errors.add(error(
                                path, id.toString(), "weight",
                                "cannot be negative"));
                        valid = false;
                    }
                    if (valid) {
                        entries.put(id, new EncounterConfigEntry(
                                (Boolean) enabledValue,
                                ((Number) weightValue).longValue()));
                    }
                }
            }
            if (!errors.isEmpty()) {
                errors.forEach(errorSink);
                return LoadResult.invalid();
            }
            return LoadResult.valid(entries);
        } catch (Exception exception) {
            errorSink.accept(error(
                    path,
                    "<file>",
                    "<parse>",
                    exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()));
            return LoadResult.invalid();
        }
    }

    public static void writeAtomic(
            Path path, Map<ResourceLocation, EncounterConfigEntry> entries)
            throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("Configuration path has no parent: " + path);
        }
        Files.createDirectories(parent);
        Path temporary = parent.resolve(
                path.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            CommentedConfig root = TomlFormat.newConfig(LinkedHashMap::new);
            root.set("schema_version", SCHEMA_VERSION);
            CommentedConfig encounterTable = root.createSubConfig();
            entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        CommentedConfig values = root.createSubConfig();
                        values.set("enabled", entry.getValue().enabled());
                        values.set("weight", entry.getValue().weight());
                        encounterTable.set(
                                List.of(entry.getKey().toString()), values);
                    });
            root.set("encounters", encounterTable);
            try (BufferedWriter writer =
                         Files.newBufferedWriter(
                                 temporary, StandardCharsets.UTF_8)) {
                new TomlWriter().write(root, writer);
            }
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean isIntegral(Number number) {
        return number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long;
    }

    private static String error(
            Path path, String id, String field, String reason) {
        return "Encounter config error: path=" + path.toAbsolutePath()
                + ", encounter=" + id
                + ", field=" + field
                + ", reason=" + reason;
    }

    public record LoadResult(
            Status status,
            Map<ResourceLocation, EncounterConfigEntry> entries) {
        public LoadResult {
            entries = Map.copyOf(entries);
        }

        public static LoadResult missing() {
            return new LoadResult(Status.MISSING, Map.of());
        }

        public static LoadResult invalid() {
            return new LoadResult(Status.INVALID, Map.of());
        }

        public static LoadResult valid(
                Map<ResourceLocation, EncounterConfigEntry> entries) {
            return new LoadResult(Status.VALID, entries);
        }
    }

    public enum Status {
        MISSING,
        VALID,
        INVALID
    }
}
