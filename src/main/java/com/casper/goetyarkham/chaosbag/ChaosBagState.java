package com.casper.goetyarkham.chaosbag;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure ordered-replay state used by SavedData and deterministic tests.
 */
public final class ChaosBagState {
    public static final int MAX_MUTATION_COUNT = 100_000;

    private ChaosBagLevel level;
    private final List<ChaosBagMutation> mutations;

    public ChaosBagState() {
        this(ChaosBagLevel.NORMAL, List.of());
    }

    public ChaosBagState(
            ChaosBagLevel level, List<ChaosBagMutation> mutations) {
        this.level = Objects.requireNonNull(level);
        this.mutations = new ArrayList<>(mutations);
        if (effectiveTokens(level, this.mutations).isEmpty()) {
            throw new IllegalArgumentException("Restored chaos bag is empty");
        }
    }

    public synchronized ChaosBagLevel level() {
        return level;
    }

    public synchronized List<ChaosBagMutation> mutations() {
        return List.copyOf(mutations);
    }

    public synchronized List<ChaosToken> baseTokens() {
        return level.baseTokens();
    }

    public synchronized List<ChaosToken> effectiveTokens() {
        return List.copyOf(effectiveTokens(level, mutations));
    }

    public synchronized ChaosBagSnapshot snapshot() {
        return new ChaosBagSnapshot(level, effectiveTokens());
    }

    public synchronized OperationResult setLevel(ChaosBagLevel replacement) {
        Objects.requireNonNull(replacement);
        List<ChaosToken> candidate = effectiveTokens(replacement, mutations);
        if (candidate.isEmpty()) {
            return OperationResult.failure(
                    "level change would leave the effective chaos bag empty");
        }
        if (replacement == level) {
            return OperationResult.success(false, "level is already " + level.serializedName());
        }
        level = replacement;
        return OperationResult.success(true, "level changed to " + level.serializedName());
    }

    public synchronized OperationResult add(
            ChaosToken token, int count, ResourceLocation source) {
        if (!validCount(count)) {
            return invalidCount();
        }
        return append(new ChaosBagMutation(
                ChaosBagMutation.Operation.ADD,
                token,
                count,
                source));
    }

    public synchronized OperationResult remove(
            ChaosToken token, int count, ResourceLocation source) {
        if (!validCount(count)) {
            return invalidCount();
        }
        int safeCount = count;
        long available = effectiveTokens().stream().filter(token::equals).count();
        if (available < safeCount) {
            return OperationResult.failure(
                    "requested " + safeCount + " " + token.serializedName()
                            + " token(s), but only " + available + " are effective");
        }
        return append(new ChaosBagMutation(
                ChaosBagMutation.Operation.REMOVE,
                token,
                safeCount,
                source));
    }

    public synchronized OperationResult clearSource(ResourceLocation source) {
        Objects.requireNonNull(source);
        List<ChaosBagMutation> candidate = mutations.stream()
                .filter(mutation -> !mutation.source().equals(source))
                .toList();
        if (candidate.size() == mutations.size()) {
            return OperationResult.success(false, "source has no mutation records");
        }
        if (effectiveTokens(level, candidate).isEmpty()) {
            return OperationResult.failure(
                    "clearing source would leave the effective chaos bag empty");
        }
        mutations.clear();
        mutations.addAll(candidate);
        return OperationResult.success(true, "source mutations cleared");
    }

    private OperationResult append(ChaosBagMutation mutation) {
        List<ChaosBagMutation> candidate = new ArrayList<>(mutations);
        candidate.add(mutation);
        if (effectiveTokens(level, candidate).isEmpty()) {
            return OperationResult.failure(
                    "mutation would leave the effective chaos bag empty");
        }
        mutations.add(mutation);
        return OperationResult.success(true, "mutation appended");
    }

    static List<ChaosToken> effectiveTokens(
            ChaosBagLevel level, List<ChaosBagMutation> mutations) {
        List<ChaosToken> result = new ArrayList<>(level.baseTokens());
        for (ChaosBagMutation mutation : mutations) {
            if (mutation.operation() == ChaosBagMutation.Operation.ADD) {
                for (int index = 0; index < mutation.count(); index++) {
                    result.add(mutation.token());
                }
                continue;
            }
            int remaining = mutation.count();
            for (int index = 0; index < result.size() && remaining > 0; ) {
                if (result.get(index).equals(mutation.token())) {
                    result.remove(index);
                    remaining--;
                } else {
                    index++;
                }
            }
        }
        return result;
    }

    private static boolean validCount(int count) {
        return count > 0 && count <= MAX_MUTATION_COUNT;
    }

    private static OperationResult invalidCount() {
        return OperationResult.failure(
                "count must be in [1, " + MAX_MUTATION_COUNT + "]");
    }

    public record OperationResult(boolean success, boolean changed, String message) {
        public static OperationResult success(boolean changed, String message) {
            return new OperationResult(true, changed, message);
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, false, message);
        }
    }
}
