package com.casper.goetyarkham.illager_treachery;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record PlayerTreacheryResolution(
        UUID playerId,
        PlayerTreacheryResult result,
        List<ResourceLocation> encounters,
        int executionFailures) {

    public PlayerTreacheryResolution {
        encounters = List.copyOf(encounters);
    }
}
