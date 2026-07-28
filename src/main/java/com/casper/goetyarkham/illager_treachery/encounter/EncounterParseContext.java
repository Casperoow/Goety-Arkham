package com.casper.goetyarkham.illager_treachery.encounter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class EncounterParseContext {
    public static final EncounterParseContext SERVER =
            new EncounterParseContext(id ->
                    Optional.ofNullable(ForgeRegistries.MOB_EFFECTS.getValue(id)));

    private final Function<ResourceLocation, Optional<MobEffect>> effectResolver;

    public EncounterParseContext(
            Function<ResourceLocation, Optional<MobEffect>> effectResolver) {
        this.effectResolver = Objects.requireNonNull(effectResolver);
    }

    public Optional<MobEffect> resolveEffect(ResourceLocation id) {
        return effectResolver.apply(Objects.requireNonNull(id));
    }
}
