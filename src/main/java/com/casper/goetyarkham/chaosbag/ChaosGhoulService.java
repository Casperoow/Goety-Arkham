package com.casper.goetyarkham.chaosbag;

import com.casper.goetyarkham.GoetyArkham;
import com.lion.graveyard.entities.GhoulEntity;
import com.lion.graveyard.init.TGEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

public final class ChaosGhoulService {
    public static final double CHECK_RADIUS = 16.0D;
    private static final int SPAWN_ATTEMPTS = 32;

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
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int xOffset = player.getRandom().nextInt(17) - 8;
            int zOffset = player.getRandom().nextInt(17) - 8;
            int yOffset = yOffset(attempt);
            BlockPos position = origin.offset(xOffset, yOffset, zOffset);
            if (!level.isLoaded(position)
                    || !level.getWorldBorder().isWithinBounds(position)
                    || !level.getBlockState(position.below()).isFaceSturdy(
                            level, position.below(), Direction.UP)) {
                continue;
            }

            GhoulEntity ghoul = TGEntities.GHOUL.get().create(level);
            if (ghoul == null) {
                GoetyArkham.LOGGER.warn(
                        "[chaos_bag] graveyard:ghoul EntityType returned null");
                return false;
            }
            ghoul.moveTo(
                    position.getX() + 0.5D,
                    position.getY(),
                    position.getZ() + 0.5D,
                    player.getRandom().nextFloat() * 360.0F,
                    0.0F);
            if (!level.noCollision(ghoul)) {
                ghoul.discard();
                continue;
            }
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
        }
        GoetyArkham.LOGGER.debug(
                "[chaos_bag] No safe loaded spawn position for graveyard:ghoul "
                        + "near player={}, uuid={}, dimension={}, position={}",
                player.getGameProfile().getName(),
                player.getUUID(),
                level.dimension().location(),
                origin);
        return false;
    }

    private static int yOffset(int attempt) {
        int step = attempt % 9;
        if (step == 0) {
            return 0;
        }
        int magnitude = (step + 1) / 2;
        return step % 2 == 0 ? -magnitude : magnitude;
    }
}
