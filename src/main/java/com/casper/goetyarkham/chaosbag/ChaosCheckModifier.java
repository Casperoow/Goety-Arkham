package com.casper.goetyarkham.chaosbag;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ChaosCheckModifier(ResourceLocation source, int amount) {
    public ChaosCheckModifier {
        Objects.requireNonNull(source, "source");
    }
}
