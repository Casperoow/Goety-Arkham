package com.casper.goetyarkham.chaosbag;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ChaosBagMutation(
        Operation operation,
        ChaosToken token,
        int count,
        ResourceLocation source) {

    public ChaosBagMutation {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(source, "source");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public enum Operation {
        ADD,
        REMOVE
    }
}
