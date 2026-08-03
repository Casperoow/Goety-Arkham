package com.casper.goetyarkham.loneliness;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PlayerLonelinessProvider
        implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    private final PlayerLonelinessData data = new PlayerLonelinessData();
    private LazyOptional<IPlayerLoneliness> optional = createOptional();
    private boolean invalidated;

    @Override
    public synchronized <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability != LonelinessCapabilities.PLAYER_LONELINESS) {
            return LazyOptional.empty();
        }
        if (invalidated) {
            optional = createOptional();
            invalidated = false;
        }
        return optional.cast();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.deserializeNBT(nbt);
    }

    synchronized void invalidate() {
        if (!invalidated) {
            optional.invalidate();
            invalidated = true;
        }
    }

    private LazyOptional<IPlayerLoneliness> createOptional() {
        return LazyOptional.of(() -> data);
    }
}
