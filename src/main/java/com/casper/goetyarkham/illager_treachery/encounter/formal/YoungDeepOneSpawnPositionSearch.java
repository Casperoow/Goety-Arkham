package com.casper.goetyarkham.illager_treachery.encounter.formal;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class YoungDeepOneSpawnPositionSearch {
    static final int SEARCH_RADIUS = 8;
    static final int SEARCH_RADIUS_SQUARED =
            SEARCH_RADIUS * SEARCH_RADIUS;

    private static final List<Offset> FALLBACK_OFFSETS = buildOffsets();

    private YoungDeepOneSpawnPositionSearch() {
    }

    static SearchResult find(Vec3 origin, WorldView world) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(world, "world");

        SearchStats stats = new SearchStats();
        Vec3 overlappingFallback = null;

        CandidateStatus originStatus = world.evaluate(origin, true);
        stats.checkedPositions++;
        if (originStatus == CandidateStatus.SAFE) {
            return stats.success(origin, SearchPhase.PLAYER_POSITION, false);
        }
        if (originStatus == CandidateStatus.ENTITY_OVERLAP) {
            overlappingFallback = origin;
        } else {
            stats.reject(originStatus);
        }

        for (Offset offset : FALLBACK_OFFSETS) {
            Vec3 candidate = origin.add(offset.dx(), offset.dy(), offset.dz());
            CandidateStatus status = world.evaluate(candidate, false);
            stats.checkedPositions++;
            if (status == CandidateStatus.SAFE) {
                return stats.success(candidate, SearchPhase.FALLBACK, false);
            }
            if (status == CandidateStatus.ENTITY_OVERLAP) {
                if (overlappingFallback == null) {
                    overlappingFallback = candidate;
                }
            } else {
                stats.reject(status);
            }
        }

        if (overlappingFallback != null) {
            return stats.success(
                    overlappingFallback,
                    SearchPhase.FALLBACK,
                    true
            );
        }
        return stats.failure();
    }

    static List<Offset> fallbackOffsets() {
        return FALLBACK_OFFSETS;
    }

    private static List<Offset> buildOffsets() {
        List<Offset> offsets = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    int distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared == 0
                            || distanceSquared > SEARCH_RADIUS_SQUARED) {
                        continue;
                    }
                    offsets.add(new Offset(dx, dy, dz, distanceSquared));
                }
            }
        }
        offsets.sort(Comparator
                .comparingInt(Offset::distanceSquared)
                .thenComparingInt(Offset::dy)
                .thenComparingInt(Offset::dx)
                .thenComparingInt(Offset::dz));
        return List.copyOf(offsets);
    }

    interface WorldView {
        CandidateStatus evaluate(
                Vec3 position,
                boolean allowTriggerPlayerOverlap
        );
    }

    enum CandidateStatus {
        SAFE,
        UNLOADED,
        OUTSIDE_BUILD_HEIGHT,
        OUTSIDE_WORLD_BORDER,
        LAVA,
        COLLISION,
        UNSUPPORTED,
        ENTITY_OVERLAP
    }

    enum SearchPhase {
        PLAYER_POSITION,
        FALLBACK
    }

    record Offset(int dx, int dy, int dz, int distanceSquared) {
    }

    record SearchResult(
            Optional<Vec3> position,
            Optional<SearchPhase> phase,
            boolean entityOverlap,
            int checkedPositions,
            Map<CandidateStatus, Integer> rejections
    ) {
        SearchResult {
            rejections = Map.copyOf(rejections);
        }
    }

    private static final class SearchStats {
        private final EnumMap<CandidateStatus, Integer> rejections =
                new EnumMap<>(CandidateStatus.class);
        private int checkedPositions;

        private void reject(CandidateStatus status) {
            this.rejections.merge(status, 1, Integer::sum);
        }

        private SearchResult success(
                Vec3 position,
                SearchPhase phase,
                boolean entityOverlap
        ) {
            return new SearchResult(
                    Optional.of(position),
                    Optional.of(phase),
                    entityOverlap,
                    this.checkedPositions,
                    this.rejections
            );
        }

        private SearchResult failure() {
            return new SearchResult(
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    this.checkedPositions,
                    this.rejections
            );
        }
    }
}
