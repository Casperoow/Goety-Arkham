package com.casper.goetyarkham.chaosbag;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic position ordering for ghoul spawns. World access is injected
 * so the search policy can be tested without a running server.
 */
final class GhoulSpawnPositionSearch {
    static final int HORIZONTAL_RADIUS = 8;
    static final int HORIZONTAL_ATTEMPTS = 32;
    static final int NEARBY_VERTICAL_RADIUS = 4;

    private static final int[] NEARBY_Y_OFFSETS = {
            0, 1, -1, 2, -2, 3, -3, 4, -4
    };

    private GhoulSpawnPositionSearch() {
    }

    static List<Column> sampleColumns(
            BlockPos origin, ChaosRandom random) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(random, "random");
        int diameter = HORIZONTAL_RADIUS * 2 + 1;
        List<Column> result = new ArrayList<>(HORIZONTAL_ATTEMPTS);
        for (int attempt = 0; attempt < HORIZONTAL_ATTEMPTS; attempt++) {
            result.add(new Column(
                    origin.getX()
                            + random.nextInt(diameter)
                            - HORIZONTAL_RADIUS,
                    origin.getZ()
                            + random.nextInt(diameter)
                            - HORIZONTAL_RADIUS));
        }
        return List.copyOf(result);
    }

    static SearchResult find(
            BlockPos origin,
            List<Column> sampledColumns,
            WorldView world) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sampledColumns, "sampledColumns");
        Objects.requireNonNull(world, "world");

        SearchStats stats = new SearchStats(sampledColumns.size());
        LinkedHashSet<Column> uniqueColumns =
                new LinkedHashSet<>(sampledColumns);
        stats.uniqueColumns = uniqueColumns.size();
        List<Column> loadedColumns = new ArrayList<>(uniqueColumns.size());
        for (Column column : uniqueColumns) {
            if (world.isColumnLoaded(column.x(), column.z())) {
                loadedColumns.add(column);
            } else {
                stats.reject(RejectionReason.UNLOADED_COLUMN);
            }
        }
        stats.loadedColumns = loadedColumns.size();

        for (Column column : loadedColumns) {
            for (int offset : NEARBY_Y_OFFSETS) {
                int y = origin.getY() + offset;
                Optional<SearchResult> found = evaluate(
                        new BlockPos(column.x(), y, column.z()),
                        SearchPhase.NEARBY,
                        world,
                        stats);
                if (found.isPresent()) {
                    return found.get();
                }
            }
        }

        int highestSpawnY = world.maxBuildHeight() - 2;
        int lowestSpawnY = world.minBuildHeight() + 1;
        int fallbackStartY = Math.min(
                origin.getY() - NEARBY_VERTICAL_RADIUS - 1,
                highestSpawnY);
        for (int y = fallbackStartY; y >= lowestSpawnY; y--) {
            for (Column column : loadedColumns) {
                Optional<SearchResult> found = evaluate(
                        new BlockPos(column.x(), y, column.z()),
                        SearchPhase.DOWNWARD,
                        world,
                        stats);
                if (found.isPresent()) {
                    return found.get();
                }
            }
        }
        return stats.failure();
    }

    private static Optional<SearchResult> evaluate(
            BlockPos position,
            SearchPhase phase,
            WorldView world,
            SearchStats stats) {
        if (position.getY() <= world.minBuildHeight()
                || position.getY() >= world.maxBuildHeight() - 1) {
            stats.reject(RejectionReason.OUTSIDE_BUILD_HEIGHT);
            return Optional.empty();
        }

        stats.checkedPositions++;
        CandidateStatus status = Objects.requireNonNull(
                world.evaluate(position), "candidate status");
        if (status == CandidateStatus.SAFE) {
            return Optional.of(stats.success(position, phase));
        }
        stats.reject(status.rejectionReason());
        return Optional.empty();
    }

    record Column(int x, int z) {
    }

    interface WorldView {
        int minBuildHeight();

        int maxBuildHeight();

        boolean isColumnLoaded(int blockX, int blockZ);

        CandidateStatus evaluate(BlockPos spawnPosition);
    }

    enum CandidateStatus {
        SAFE(null),
        OUTSIDE_WORLD_BORDER(RejectionReason.OUTSIDE_WORLD_BORDER),
        UNSUPPORTED(RejectionReason.UNSUPPORTED),
        FLUID(RejectionReason.FLUID),
        DANGEROUS_BLOCK(RejectionReason.DANGEROUS_BLOCK),
        COLLISION(RejectionReason.COLLISION);

        private final RejectionReason rejectionReason;

        CandidateStatus(RejectionReason rejectionReason) {
            this.rejectionReason = rejectionReason;
        }

        RejectionReason rejectionReason() {
            if (rejectionReason == null) {
                throw new IllegalStateException("SAFE is not a rejection");
            }
            return rejectionReason;
        }
    }

    enum RejectionReason {
        UNLOADED_COLUMN,
        OUTSIDE_BUILD_HEIGHT,
        OUTSIDE_WORLD_BORDER,
        UNSUPPORTED,
        FLUID,
        DANGEROUS_BLOCK,
        COLLISION
    }

    enum SearchPhase {
        NEARBY,
        DOWNWARD
    }

    record SearchResult(
            Optional<BlockPos> position,
            Optional<SearchPhase> phase,
            int sampledColumns,
            int uniqueColumns,
            int loadedColumns,
            int checkedPositions,
            Map<RejectionReason, Integer> rejections) {
        SearchResult {
            position = position.map(BlockPos::immutable);
            rejections = Map.copyOf(rejections);
        }

        String failureReason() {
            if (position.isPresent()) {
                return "none";
            }
            if (loadedColumns == 0) {
                return "no_sampled_columns_loaded";
            }
            if (checkedPositions == 0) {
                return "no_candidate_inside_build_height";
            }
            return "no_safe_position_in_loaded_columns";
        }
    }

    private static final class SearchStats {
        private final int sampledColumns;
        private final EnumMap<RejectionReason, Integer> rejections =
                new EnumMap<>(RejectionReason.class);
        private int uniqueColumns;
        private int loadedColumns;
        private int checkedPositions;

        private SearchStats(int sampledColumns) {
            this.sampledColumns = sampledColumns;
        }

        private void reject(RejectionReason reason) {
            rejections.merge(reason, 1, Integer::sum);
        }

        private SearchResult success(
                BlockPos position, SearchPhase phase) {
            return new SearchResult(
                    Optional.of(position),
                    Optional.of(phase),
                    sampledColumns,
                    uniqueColumns,
                    loadedColumns,
                    checkedPositions,
                    rejections);
        }

        private SearchResult failure() {
            return new SearchResult(
                    Optional.empty(),
                    Optional.empty(),
                    sampledColumns,
                    uniqueColumns,
                    loadedColumns,
                    checkedPositions,
                    rejections);
        }
    }
}
