package com.casper.goetyarkham.soul;

import com.Polarice3.Goety.common.blocks.entities.ArcaBlockEntity;
import com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy;
import com.Polarice3.Goety.common.capabilities.soulenergy.SEProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Resolves the active Arca prerequisite used by a cursed cage's soul-transfer
 * gem. This deliberately does not alter Goety's general Arca lifecycle.
 */
public final class SoulTransferArcaValidator {
    private SoulTransferArcaValidator() {
    }

    public static Optional<ArcaBlockEntity> findValidArca(ServerPlayer player) {
        Optional<ISoulEnergy> capability =
                player.getCapability(SEProvider.CAPABILITY).resolve();
        if (capability.isEmpty() || !capability.get().getSEActive()) {
            return Optional.empty();
        }

        BlockPos arcaPos = capability.get().getArcaBlock();
        ResourceKey<Level> arcaDimension =
                capability.get().getArcaBlockDimension();
        if (arcaPos == null || arcaDimension == null) {
            return Optional.empty();
        }

        ServerLevel arcaLevel =
                player.serverLevel().getServer().getLevel(arcaDimension);
        if (arcaLevel == null
                || !arcaLevel.hasChunkAt(arcaPos)
                || !(arcaLevel.getBlockEntity(arcaPos)
                        instanceof ArcaBlockEntity arca)) {
            return Optional.empty();
        }

        return player.getUUID().equals(arca.getOwnerUUID())
                ? Optional.of(arca)
                : Optional.empty();
    }
}
