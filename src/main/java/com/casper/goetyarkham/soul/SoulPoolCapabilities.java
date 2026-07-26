package com.casper.goetyarkham.soul;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public final class SoulPoolCapabilities {
    public static final Capability<PlayerSoulPoolData> PLAYER_SOUL_POOL =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private SoulPoolCapabilities() {
    }
}
