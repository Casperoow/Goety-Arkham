package com.casper.goetyarkham.chaosbag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ChaosBagSnapshot(ChaosBagLevel level, List<ChaosToken> tokens) {
    public ChaosBagSnapshot {
        Objects.requireNonNull(level, "level");
        tokens = List.copyOf(tokens);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("A chaos bag snapshot cannot be empty");
        }
    }

    public Map<ChaosToken, Integer> counts() {
        Map<ChaosToken, Integer> counts = new LinkedHashMap<>();
        for (ChaosToken token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }
}
