package com.casper.goetyarkham.loneliness;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public final class LonelinessCapabilities {
    public static final Capability<IPlayerLoneliness> PLAYER_LONELINESS =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private LonelinessCapabilities() {
    }
}
