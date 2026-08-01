package com.casper.goetyarkham.sanity.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SanityClientConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue HUD_OFFSET_X;
    private static final ForgeConfigSpec.IntValue HUD_OFFSET_Y;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("sanity_hud");
        HUD_OFFSET_X = builder
                .comment("Horizontal offset in GUI-scaled pixels for the sanity HUD.")
                .defineInRange("sanityHudOffsetX", 0, -10_000, 10_000);
        HUD_OFFSET_Y = builder
                .comment("Vertical offset in GUI-scaled pixels for the sanity HUD.")
                .defineInRange("sanityHudOffsetY", 0, -10_000, 10_000);
        builder.pop();
        SPEC = builder.build();
    }

    private SanityClientConfig() {
    }

    public static int hudOffsetX() {
        return HUD_OFFSET_X.get();
    }

    public static int hudOffsetY() {
        return HUD_OFFSET_Y.get();
    }
}
