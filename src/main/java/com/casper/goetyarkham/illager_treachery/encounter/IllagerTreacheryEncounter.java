package com.casper.goetyarkham.illager_treachery.encounter;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;

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

    /**
     * Fixed descriptive metadata. It is deliberately absent from the
     * per-world enabled/weight configuration.
     */
    default Set<ResourceLocation> encounterTags() {
        return Set.of();
    }

    default Optional<ResourceLocation> encounterGroup() {
        return Optional.empty();
    }

    default Optional<String> nameTranslationKey() {
        return Optional.empty();
    }

    default Optional<String> descriptionTranslationKey() {
        return Optional.empty();
    }

    void execute(EncounterExecutionContext context) throws Exception;
}
