package com.casper.goetyarkham.sanity;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PlayerSanityProvider
        implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    private final PlayerSanityData data = new PlayerSanityData();
    private final ServerPlayer player;
    private LazyOptional<IPlayerSanity> optional = createOptional();
    private boolean invalidated;

    PlayerSanityProvider(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public synchronized <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability != SanityCapabilities.PLAYER_SANITY) {
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
        int maximum = SanityMath.maximumSanity(
                SanityService.getMaximumAttributeValue(player),
                SanityMath.clampPermanentLoss(nbt.getInt("permanentMaxLoss")));
        data.deserializeNBT(nbt, maximum);
    }

    synchronized void invalidate() {
        if (!invalidated) {
            optional.invalidate();
            invalidated = true;
        }
    }

    private LazyOptional<IPlayerSanity> createOptional() {
        return LazyOptional.of(() -> data);
    }
}
