package com.casper.goetyarkham.stats;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public final class ModCapabilities {
    public static final Capability<IPlayerStats> PLAYER_STATS =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private ModCapabilities() {
    }
}
