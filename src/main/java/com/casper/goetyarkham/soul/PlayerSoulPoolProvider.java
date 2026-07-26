package com.casper.goetyarkham.soul;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PlayerSoulPoolProvider
        implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    private final PlayerSoulPoolData data = new PlayerSoulPoolData();
    private LazyOptional<PlayerSoulPoolData> optional = createOptional();
    private boolean invalidated;

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> capability, @Nullable Direction side) {
        if (invalidated) {
            optional = createOptional();
            invalidated = false;
        }
        return capability == SoulPoolCapabilities.PLAYER_SOUL_POOL
                ? optional.cast()
                : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.deserializeNBT(nbt);
    }

    void invalidate() {
        if (!invalidated) {
            optional.invalidate();
            invalidated = true;
        }
    }

    private LazyOptional<PlayerSoulPoolData> createOptional() {
        return LazyOptional.of(() -> data);
    }
}
