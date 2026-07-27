package com.casper.goetyarkham.illager_treachery.encounter;

import net.minecraft.resources.ResourceLocation;

/**
 * One independently weighted player encounter. Implementations are registered
 * through {@code IllagerTreacheryApi}; none are registered by the framework.
 */
public interface IllagerTreacheryEncounter {
    ResourceLocation id();

    default boolean defaultEnabled() {
        return true;
    }

    default long defaultWeight() {
        return 1L;
    }

    void execute(EncounterExecutionContext context) throws Exception;
}
