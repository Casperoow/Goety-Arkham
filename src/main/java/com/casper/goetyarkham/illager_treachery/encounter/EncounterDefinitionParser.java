package com.casper.goetyarkham.illager_treachery.encounter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class EncounterDefinitionParser {
    public static final int SCHEMA_VERSION = 1;

    private EncounterDefinitionParser() {
    }

    public static DataDrivenEncounter<?> parse(
            ResourceLocation encounterId,
            JsonElement element,
            EncounterTypeRegistry types,
            EncounterParseContext context) throws EncounterDefinitionException {
        Objects.requireNonNull(encounterId);
        if (element == null || !element.isJsonObject()) {
            throw new EncounterDefinitionException(
                    "Encounter " + encounterId + " root must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        int schema = EncounterJsonFields.requiredInt(
                encounterId, root, "schema_version");
        if (schema != SCHEMA_VERSION) {
            throw EncounterJsonFields.invalid(
                    encounterId,
                    "schema_version",
                    "uses unsupported value " + schema);
        }
        ResourceLocation typeId = EncounterJsonFields.requiredId(
                encounterId, root, "type");
        EncounterType<?> type = types.get(typeId).orElseThrow(() ->
                EncounterJsonFields.invalid(
                        encounterId, "type", "is not registered: " + typeId));
        boolean defaultEnabled = EncounterJsonFields.requiredBoolean(
                encounterId, root, "default_enabled");
        long defaultWeight = EncounterJsonFields.requiredLong(
                encounterId, root, "default_weight");
        if (defaultWeight < 0L) {
            throw EncounterJsonFields.invalid(
                    encounterId, "default_weight", "cannot be negative");
        }
        JsonElement dataElement = root.get("data");
        if (dataElement == null || !dataElement.isJsonObject()) {
            throw EncounterJsonFields.invalid(
                    encounterId, "data", "must be a JSON object");
        }
        return create(
                encounterId,
                typeId,
                defaultEnabled,
                defaultWeight,
                type,
                dataElement.getAsJsonObject(),
                context);
    }

    private static <D> DataDrivenEncounter<D> create(
            ResourceLocation encounterId,
            ResourceLocation typeId,
            boolean defaultEnabled,
            long defaultWeight,
            EncounterType<D> type,
            JsonObject data,
            EncounterParseContext context) throws EncounterDefinitionException {
        D parsedData;
        try {
            parsedData = type.parse(encounterId, data, context);
        } catch (EncounterDefinitionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EncounterDefinitionException(
                    "Encounter " + encounterId
                            + " type " + typeId + " rejected its data",
                    exception);
        }
        if (parsedData == null) {
            throw new EncounterDefinitionException(
                    "Encounter " + encounterId
                            + " type " + typeId + " returned null data");
        }
        return new DataDrivenEncounter<>(
                encounterId,
                typeId,
                defaultEnabled,
                defaultWeight,
                type,
                parsedData);
    }
}
