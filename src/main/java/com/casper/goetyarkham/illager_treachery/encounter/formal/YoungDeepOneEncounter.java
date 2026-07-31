package com.casper.goetyarkham.illager_treachery.encounter.formal;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.entity.ModEntities;
import com.casper.goetyarkham.entity.YoungDeepOneEntity;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.IllagerTreacheryEncounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

public final class YoungDeepOneEncounter
        implements IllagerTreacheryEncounter {
    public static final ResourceLocation ID =
            new ResourceLocation(GoetyArkham.MOD_ID, "young_deep_one");

    private static final String NAME_KEY =
            "encounter.goetyarkham.young_deep_one.name";
    private static final String DESCRIPTION_KEY =
            "encounter.goetyarkham.young_deep_one.description";
    private static final double SUPPORT_PROBE_DEPTH = 0.125D;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Set<ResourceLocation> encounterTags() {
        return Set.of(
                FormalEncounterMetadata.ENEMY,
                FormalEncounterMetadata.HUMANOID,
                FormalEncounterMetadata.MONSTER,
                FormalEncounterMetadata.DEEP_ONE
        );
    }

    @Override
    public Optional<ResourceLocation> encounterGroup() {
        return Optional.of(FormalEncounterMetadata.APOSTLES_OF_CTHULHU);
    }

    @Override
    public Optional<String> nameTranslationKey() {
        return Optional.of(NAME_KEY);
    }

    @Override
    public Optional<String> descriptionTranslationKey() {
        return Optional.of(DESCRIPTION_KEY);
    }

    @Override
    public void execute(EncounterExecutionContext context) {
        ServerPlayer player = context.player();
        player.sendSystemMessage(Component.translatable(DESCRIPTION_KEY));
        spawnFor(player);
    }

    static boolean spawnFor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        YoungDeepOneEntity deepOne =
                ModEntities.YOUNG_DEEP_ONE.get().create(level);
        if (deepOne == null) {
            warnSpawnFailure(player, "entity_type_returned_null", null);
            return false;
        }

        YoungDeepOneSpawnPositionSearch.SearchResult search =
                YoungDeepOneSpawnPositionSearch.find(
                        player.position(),
                        new ServerWorldView(level, player, deepOne)
                );
        Vec3 position = search.position().orElse(null);
        if (position == null) {
            deepOne.discard();
            warnSpawnFailure(
                    player,
                    "no_safe_position; checked=" + search.checkedPositions()
                            + "; rejections=" + search.rejections(),
                    null
            );
            return false;
        }

        deepOne.moveTo(
                position.x,
                position.y,
                position.z,
                player.getYRot(),
                0.0F
        );
        deepOne.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(BlockPos.containing(position)),
                MobSpawnType.EVENT,
                null,
                null
        );
        if (level.addFreshEntity(deepOne)) {
            return true;
        }

        deepOne.discard();
        warnSpawnFailure(player, "entity_insertion_rejected", position);
        return false;
    }

    private static void warnSpawnFailure(
            ServerPlayer player,
            String reason,
            Vec3 attemptedPosition
    ) {
        GoetyArkham.LOGGER.warn(
                "[illager_treachery] Encounter spawn failed: encounter={}, "
                        + "player_uuid={}, dimension={}, player_position={}, "
                        + "attempted_position={}, reason={}",
                ID,
                player.getUUID(),
                player.serverLevel().dimension().location(),
                player.position(),
                attemptedPosition,
                reason
        );
    }

    private static final class ServerWorldView
            implements YoungDeepOneSpawnPositionSearch.WorldView {
        private final ServerLevel level;
        private final ServerPlayer triggerPlayer;
        private final YoungDeepOneEntity deepOne;

        private ServerWorldView(
                ServerLevel level,
                ServerPlayer triggerPlayer,
                YoungDeepOneEntity deepOne
        ) {
            this.level = level;
            this.triggerPlayer = triggerPlayer;
            this.deepOne = deepOne;
        }

        @Override
        public YoungDeepOneSpawnPositionSearch.CandidateStatus evaluate(
                Vec3 position,
                boolean allowTriggerPlayerOverlap
        ) {
            this.deepOne.moveTo(
                    position.x,
                    position.y,
                    position.z,
                    0.0F,
                    0.0F
            );
            AABB bounds = this.deepOne.getBoundingBox();
            if (bounds.minY < this.level.getMinBuildHeight()
                    || bounds.maxY > this.level.getMaxBuildHeight()) {
                return YoungDeepOneSpawnPositionSearch.CandidateStatus
                        .OUTSIDE_BUILD_HEIGHT;
            }
            if (!this.areChunksLoaded(bounds)) {
                return YoungDeepOneSpawnPositionSearch.CandidateStatus
                        .UNLOADED;
            }
            if (!this.level.getWorldBorder().isWithinBounds(bounds)) {
                return YoungDeepOneSpawnPositionSearch.CandidateStatus
                        .OUTSIDE_WORLD_BORDER;
            }

            boolean inWater = false;
            for (BlockPos blockPos : blocksIntersecting(bounds)) {
                if (this.level.getFluidState(blockPos).is(FluidTags.LAVA)) {
                    return YoungDeepOneSpawnPositionSearch.CandidateStatus.LAVA;
                }
                if (this.level.getFluidState(blockPos).is(FluidTags.WATER)) {
                    inWater = true;
                }
            }
            if (this.level.getBlockCollisions(this.deepOne, bounds)
                    .iterator().hasNext()) {
                return YoungDeepOneSpawnPositionSearch.CandidateStatus
                        .COLLISION;
            }
            if (!inWater && !this.hasSupport(bounds)) {
                return YoungDeepOneSpawnPositionSearch.CandidateStatus
                        .UNSUPPORTED;
            }

            boolean entityOverlap = !this.level.getEntities(
                    this.deepOne,
                    bounds,
                    entity -> isBlockingEntity(
                            entity,
                            allowTriggerPlayerOverlap
                    )
            ).isEmpty();
            return entityOverlap
                    ? YoungDeepOneSpawnPositionSearch.CandidateStatus
                    .ENTITY_OVERLAP
                    : YoungDeepOneSpawnPositionSearch.CandidateStatus.SAFE;
        }

        private boolean areChunksLoaded(AABB bounds) {
            int minSectionX = SectionPos.blockToSectionCoord(
                    Mth.floor(bounds.minX));
            int maxSectionX = SectionPos.blockToSectionCoord(
                    Mth.floor(bounds.maxX - 1.0E-7D));
            int minSectionZ = SectionPos.blockToSectionCoord(
                    Mth.floor(bounds.minZ));
            int maxSectionZ = SectionPos.blockToSectionCoord(
                    Mth.floor(bounds.maxZ - 1.0E-7D));
            for (int sectionX = minSectionX;
                 sectionX <= maxSectionX;
                 sectionX++) {
                for (int sectionZ = minSectionZ;
                     sectionZ <= maxSectionZ;
                     sectionZ++) {
                    if (!this.level.getChunkSource().hasChunk(
                            sectionX,
                            sectionZ
                    )) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean hasSupport(AABB bounds) {
            return this.level.getBlockCollisions(
                    this.deepOne,
                    bounds.move(0.0D, -SUPPORT_PROBE_DEPTH, 0.0D)
            ).iterator().hasNext();
        }

        private boolean isBlockingEntity(
                Entity entity,
                boolean allowTriggerPlayerOverlap
        ) {
            return entity.isAlive()
                    && !entity.isSpectator()
                    && (!allowTriggerPlayerOverlap
                    || entity != this.triggerPlayer);
        }

        private static Iterable<BlockPos> blocksIntersecting(AABB bounds) {
            return BlockPos.betweenClosed(
                    Mth.floor(bounds.minX),
                    Mth.floor(bounds.minY),
                    Mth.floor(bounds.minZ),
                    Mth.floor(bounds.maxX - 1.0E-7D),
                    Mth.floor(bounds.maxY - 1.0E-7D),
                    Mth.floor(bounds.maxZ - 1.0E-7D)
            );
        }
    }
}
