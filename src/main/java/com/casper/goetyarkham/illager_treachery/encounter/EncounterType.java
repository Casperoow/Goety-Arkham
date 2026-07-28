package com.casper.goetyarkham.illager_treachery.encounter;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/**
 * A validated, server-only data encounter executor. Parsing happens during
 * resource reload; execution receives the resulting immutable data object.
 */
public interface EncounterType<D> {
    D parse(
            ResourceLocation encounterId,
            JsonObject data,
            EncounterParseContext context) throws EncounterDefinitionException;

    void execute(D data, EncounterExecutionContext context) throws Exception;
}
