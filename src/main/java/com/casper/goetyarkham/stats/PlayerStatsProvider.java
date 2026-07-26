package com.casper.goetyarkham.stats;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerStatsProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    private final PlayerStats stats = new PlayerStats();
    private final ServerPlayer player;
    private LazyOptional<IPlayerStats> optional = createOptional();
    private boolean invalidated;

    public PlayerStatsProvider(ServerPlayer player) {
        this.player = player;
        stats.setOnChanged(() -> PlayerStatsService.sync(player));
    }

    @Override
    public synchronized <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability != ModCapabilities.PLAYER_STATS) {
            return LazyOptional.empty();
        }
        /*
         * ServerPlayer dimension travel and death cloning invalidate the entity's
         * capability dispatcher and later revive it. Recreate only the invalidated
         * LazyOptional wrapper; the attached provider and its PlayerStats instance
         * remain the same.
         */
        if (invalidated) {
            optional = createOptional();
            invalidated = false;
            GoetyArkham.LOGGER.info(
                    "[StatsCapability] Revived provider LazyOptional: player={}, uuid={}, entityIdentity={}",
                    safePlayerName(),
                    player.getUUID(),
                    System.identityHashCode(player)
            );
        }
        return optional.cast();
    }

    @Override
    public CompoundTag serializeNBT() {
        return stats.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        stats.deserializeNBT(nbt);
    }

    public synchronized void invalidate() {
        if (!invalidated) {
            optional.invalidate();
            invalidated = true;
            GoetyArkham.LOGGER.info(
                    "[StatsCapability] Invalidated provider LazyOptional: player={}, uuid={}, entityIdentity={}",
                    safePlayerName(),
                    player.getUUID(),
                    System.identityHashCode(player)
            );
        }
    }

    private LazyOptional<IPlayerStats> createOptional() {
        return LazyOptional.of(() -> stats);
    }

    private String safePlayerName() {
        return player.getGameProfile() == null
                ? "<uninitialized>"
                : player.getGameProfile().getName();
    }
}
