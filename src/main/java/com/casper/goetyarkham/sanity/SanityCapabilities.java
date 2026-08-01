package com.casper.goetyarkham.sanity;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public final class SanityCapabilities {
    public static final Capability<IPlayerSanity> PLAYER_SANITY =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private SanityCapabilities() {
    }
}
