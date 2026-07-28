package com.casper.goetyarkham.illager_treachery.encounter;

import net.minecraft.resources.ResourceLocation;

/**
 * One independently weighted player encounter. Implementations are registered
 * through {@code IllagerTreacheryApi}; none are registered by the framework.
 */
public interface IllagerTreacheryEncounter {
    ResourceLocation JAVA_TYPE_ID =
            new ResourceLocation("goetyarkham", "java");

    ResourceLocation id();

    default ResourceLocation typeId() {
        return JAVA_TYPE_ID;
    }

    default boolean defaultEnabled() {
        return true;
    }

    default long defaultWeight() {
        return 1L;
    }

    void execute(EncounterExecutionContext context) throws Exception;
}
