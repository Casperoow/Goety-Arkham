package com.casper.goetyarkham.chaosbag;

import com.casper.goetyarkham.GoetyArkham;
import com.lion.graveyard.entities.GhoulEntity;
import com.lion.graveyard.init.TGEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class ChaosGhoulService {
    public static final double CHECK_RADIUS = 16.0D;
    public static final int SPAWN_HORIZONTAL_RADIUS =
            GhoulSpawnPositionSearch.HORIZONTAL_RADIUS;
    public static final int SPAWN_HORIZONTAL_ATTEMPTS =
            GhoulSpawnPositionSearch.HORIZONTAL_ATTEMPTS;
    public static final int SPAWN_NEARBY_VERTICAL_RADIUS =
            GhoulSpawnPositionSearch.NEARBY_VERTICAL_RADIUS;

    private ChaosGhoulService() {
    }

    public static ChaosEnvironmentSnapshot capture(ServerPlayer player) {
        AABB bounds = player.getBoundingBox().inflate(CHECK_RADIUS);
        double maximumDistanceSquared = CHECK_RADIUS * CHECK_RADIUS;
        int count = player.serverLevel().getEntities(
                        player,
                        bounds,
                        entity -> entity.getType().is(ChaosBagTags.GHOULS)
                                && entity.distanceToSqr(player)
                                <= maximumDistanceSquared)
                .size();
        return new ChaosEnvironmentSnapshot(count);
    }

    public static boolean spawnNearby(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        return spawnNearby(
                level,
                origin,
                GhoulSpawnPositionSearch.sampleColumns(
                        origin, player.getRandom()::nextInt),
                player.getRandom().nextFloat() * 360.0F,
                player.getGameProfile().getName(),
                player.getUUID());
    }

    static boolean spawnNearby(
            ServerLevel level,
            BlockPos origin,
            List<GhoulSpawnPositionSearch.Column> columns) {
        return spawnNearby(
                level,
                origin,
                columns,
                0.0F,
                "<gametest>",
                null);
    }

    private static boolean spawnNearby(
            ServerLevel level,
            BlockPos origin,
            List<GhoulSpawnPositionSearch.Column> columns,
            float yaw,
            String requesterName,
            @Nullable UUID requesterUuid) {
        GhoulEntity ghoul = TGEntities.GHOUL.get().create(level);
        if (ghoul == null) {
            GoetyArkham.LOGGER.warn(
                    "[chaos_bag] graveyard:ghoul EntityType returned null");
            return false;
        }

        GhoulSpawnPositionSearch.SearchResult search =
                GhoulSpawnPositionSearch.find(
                        origin,
                        columns,
                        new ServerWorldView(level, ghoul));
        BlockPos position = search.position().orElse(null);
        if (position == null) {
            ghoul.discard();
            GoetyArkham.LOGGER.debug(
                    "[chaos_bag] Skipped graveyard:ghoul spawn: reason={}, "
                            + "sampled_columns={}, unique_columns={}, "
                            + "loaded_columns={}, checked_positions={}, "
                            + "rejections={}, player={}, uuid={}, dimension={}, "
                            + "position={}",
                    search.failureReason(),
                    search.sampledColumns(),
                    search.uniqueColumns(),
                    search.loadedColumns(),
                    search.checkedPositions(),
                    search.rejections(),
                    requesterName,
                    requesterUuid,
                    level.dimension().location(),
                    origin);
            return false;
        }

        ghoul.moveTo(
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D,
                yaw,
                0.0F);
        ghoul.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(position),
                MobSpawnType.EVENT,
                null,
                null);
        if (level.addFreshEntity(ghoul)) {
            return true;
        }
        ghoul.discard();
        GoetyArkham.LOGGER.debug(
                "[chaos_bag] Skipped graveyard:ghoul spawn: "
                        + "reason=entity_insertion_rejected, player={}, uuid={}, "
                        + "dimension={}, position={}",
                requesterName,
                requesterUuid,
                level.dimension().location(),
                position);
        return false;
    }

    private static final class ServerWorldView
            implements GhoulSpawnPositionSearch.WorldView {
        private final ServerLevel level;
        private final GhoulEntity ghoul;

        private ServerWorldView(ServerLevel level, GhoulEntity ghoul) {
            this.level = level;
            this.ghoul = ghoul;
        }

        @Override
        public int minBuildHeight() {
            return level.getMinBuildHeight();
        }

        @Override
        public int maxBuildHeight() {
            return level.getMaxBuildHeight();
        }

        @Override
        public boolean isColumnLoaded(int blockX, int blockZ) {
            return level.getChunkSource().hasChunk(
                    SectionPos.blockToSectionCoord(blockX),
                    SectionPos.blockToSectionCoord(blockZ));
        }

        @Override
        public GhoulSpawnPositionSearch.CandidateStatus evaluate(
                BlockPos position) {
            BlockPos supportPosition = position.below();
            BlockPos headPosition = position.above();
            if (level.isOutsideBuildHeight(supportPosition)
                    || level.isOutsideBuildHeight(position)
                    || level.isOutsideBuildHeight(headPosition)) {
                return GhoulSpawnPositionSearch.CandidateStatus.COLLISION;
            }

            ghoul.moveTo(
                    position.getX() + 0.5D,
                    position.getY(),
                    position.getZ() + 0.5D,
                    0.0F,
                    0.0F);
            if (!level.getWorldBorder().isWithinBounds(
                    ghoul.getBoundingBox())) {
                return GhoulSpawnPositionSearch.CandidateStatus
                        .OUTSIDE_WORLD_BORDER;
            }

            BlockState support = level.getBlockState(supportPosition);
            if (!support.isFaceSturdy(
                    level, supportPosition, Direction.UP)) {
                return GhoulSpawnPositionSearch.CandidateStatus.UNSUPPORTED;
            }

            EntityType<?> type = ghoul.getType();
            if (type.isBlockDangerous(support)
                    || type.isBlockDangerous(level.getBlockState(position))
                    || type.isBlockDangerous(level.getBlockState(headPosition))) {
                return GhoulSpawnPositionSearch.CandidateStatus
                        .DANGEROUS_BLOCK;
            }
            if (!level.getFluidState(position).isEmpty()
                    || !level.getFluidState(headPosition).isEmpty()
                    || level.containsAnyLiquid(ghoul.getBoundingBox())) {
                return GhoulSpawnPositionSearch.CandidateStatus.FLUID;
            }
            if (!level.noCollision(ghoul)) {
                return GhoulSpawnPositionSearch.CandidateStatus.COLLISION;
            }
            return GhoulSpawnPositionSearch.CandidateStatus.SAFE;
        }
    }
}
