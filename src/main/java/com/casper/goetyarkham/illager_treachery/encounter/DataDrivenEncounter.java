package com.casper.goetyarkham.illager_treachery.encounter;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class DataDrivenEncounter<D> implements IllagerTreacheryEncounter {
    private final ResourceLocation id;
    private final ResourceLocation typeId;
    private final boolean defaultEnabled;
    private final long defaultWeight;
    private final EncounterType<D> type;
    private final D data;

    public DataDrivenEncounter(
            ResourceLocation id,
            ResourceLocation typeId,
            boolean defaultEnabled,
            long defaultWeight,
            EncounterType<D> type,
            D data) {
        this.id = Objects.requireNonNull(id);
        this.typeId = Objects.requireNonNull(typeId);
        this.defaultEnabled = defaultEnabled;
        if (defaultWeight < 0L) {
            throw new IllegalArgumentException("defaultWeight cannot be negative");
        }
        this.defaultWeight = defaultWeight;
        this.type = Objects.requireNonNull(type);
        this.data = Objects.requireNonNull(data);
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public ResourceLocation typeId() {
        return typeId;
    }

    @Override
    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    @Override
    public long defaultWeight() {
        return defaultWeight;
    }

    @Override
    public void execute(EncounterExecutionContext context) throws Exception {
        type.execute(data, context);
    }

    public D data() {
        return data;
    }
}
