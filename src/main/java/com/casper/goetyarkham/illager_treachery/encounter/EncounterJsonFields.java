package com.casper.goetyarkham.illager_treachery.encounter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;

final class EncounterJsonFields {
    private EncounterJsonFields() {
    }

    static String requiredString(
            ResourceLocation encounterId, JsonObject object, String field)
            throws EncounterDefinitionException {
        JsonElement element = object.get(field);
        if (!(element instanceof JsonPrimitive primitive)
                || !primitive.isString()
                || primitive.getAsString().isBlank()) {
            throw invalid(encounterId, field, "must be a non-empty string");
        }
        return primitive.getAsString();
    }

    static String optionalString(
            ResourceLocation encounterId, JsonObject object, String field)
            throws EncounterDefinitionException {
        if (!object.has(field)) {
            return null;
        }
        return requiredString(encounterId, object, field);
    }

    static boolean requiredBoolean(
            ResourceLocation encounterId, JsonObject object, String field)
            throws EncounterDefinitionException {
        JsonElement element = object.get(field);
        if (!(element instanceof JsonPrimitive primitive)
                || !primitive.isBoolean()) {
            throw invalid(encounterId, field, "must be a boolean");
        }
        return primitive.getAsBoolean();
    }

    static int requiredInt(
            ResourceLocation encounterId, JsonObject object, String field)
            throws EncounterDefinitionException {
        long value = requiredLong(encounterId, object, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(encounterId, field, "is outside the integer range");
        }
        return (int) value;
    }

    static long requiredLong(
            ResourceLocation encounterId, JsonObject object, String field)
            throws EncounterDefinitionException {
        JsonElement element = object.get(field);
        if (!(element instanceof JsonPrimitive primitive)
                || !primitive.isNumber()) {
            throw invalid(encounterId, field, "must be an integer");
        }
        try {
            return new BigDecimal(primitive.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid(encounterId, field, "must be an exact 64-bit integer");
        }
    }

    static ResourceLocation requiredId(
            ResourceLocation encounterId, JsonObject object, String field)
            throws EncounterDefinitionException {
        String raw = requiredString(encounterId, object, field);
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) {
            throw invalid(encounterId, field, "is not a valid resource location: " + raw);
        }
        return parsed;
    }

    static EncounterDefinitionException invalid(
            ResourceLocation encounterId, String field, String reason) {
        return new EncounterDefinitionException(
                "Encounter " + encounterId + " field '" + field + "' " + reason);
    }
}
