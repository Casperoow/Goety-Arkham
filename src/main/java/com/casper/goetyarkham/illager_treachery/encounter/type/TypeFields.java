package com.casper.goetyarkham.illager_treachery.encounter.type;

import com.casper.goetyarkham.illager_treachery.encounter.EncounterDefinitionException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;

final class TypeFields {
    private TypeFields() {
    }

    static String requiredString(
            ResourceLocation id, JsonObject object, String field)
            throws EncounterDefinitionException {
        JsonElement value = object.get(field);
        if (!(value instanceof JsonPrimitive primitive)
                || !primitive.isString()
                || primitive.getAsString().isBlank()) {
            throw invalid(id, field, "must be a non-empty string");
        }
        return primitive.getAsString();
    }

    static String optionalString(
            ResourceLocation id, JsonObject object, String field)
            throws EncounterDefinitionException {
        return object.has(field) ? requiredString(id, object, field) : null;
    }

    static boolean requiredBoolean(
            ResourceLocation id, JsonObject object, String field)
            throws EncounterDefinitionException {
        JsonElement value = object.get(field);
        if (!(value instanceof JsonPrimitive primitive)
                || !primitive.isBoolean()) {
            throw invalid(id, field, "must be a boolean");
        }
        return primitive.getAsBoolean();
    }

    static int requiredInt(
            ResourceLocation id, JsonObject object, String field)
            throws EncounterDefinitionException {
        JsonElement value = object.get(field);
        if (!(value instanceof JsonPrimitive primitive)
                || !primitive.isNumber()) {
            throw invalid(id, field, "must be an integer");
        }
        try {
            return new BigDecimal(primitive.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid(id, field, "must be an exact 32-bit integer");
        }
    }

    static ResourceLocation requiredId(
            ResourceLocation id, JsonObject object, String field)
            throws EncounterDefinitionException {
        String value = requiredString(id, object, field);
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw invalid(id, field, "is not a valid resource location: " + value);
        }
        return parsed;
    }

    static EncounterDefinitionException invalid(
            ResourceLocation id, String field, String reason) {
        return new EncounterDefinitionException(
                "Encounter " + id + " data field '" + field + "' " + reason);
    }
}
