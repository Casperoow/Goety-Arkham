package com.casper.goetyarkham.illager_treachery.config;

import com.casper.goetyarkham.illager_treachery.TreacherySettings;
import net.minecraftforge.common.ForgeConfigSpec;

public final class IllagerTreacheryConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.IntValue MINIMUM_SOUL;
    private static final ForgeConfigSpec.LongValue CURVE_MAXIMUM_SOUL;
    private static final ForgeConfigSpec.DoubleValue MINIMUM_PROBABILITY;
    private static final ForgeConfigSpec.DoubleValue MAXIMUM_PROBABILITY;
    private static final ForgeConfigSpec.IntValue GUARANTEED_VALID_DAYS;
    private static final ForgeConfigSpec.LongValue COOLDOWN_TICKS;
    private static final ForgeConfigSpec.IntValue MAXIMUM_EXTRA_DRAWS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("illager_treachery");
        ENABLED = builder
                .comment("Master switch for the server-authoritative illager treachery event.")
                .define("enabled", true);
        MINIMUM_SOUL = builder
                .comment("Minimum final soul-energy maximum required to trigger the event.")
                .defineInRange("minimum_soul", 1_000, 0, Integer.MAX_VALUE);
        CURVE_MAXIMUM_SOUL = builder
                .comment("Soul maximum at which the probability curve reaches its cap.")
                .defineInRange("curve_maximum_soul", 100_000L, 1L, Long.MAX_VALUE);
        MINIMUM_PROBABILITY = builder
                .comment("Probability at minimum_soul. Values are clamped to [0, 1].")
                .defineInRange("minimum_probability", 0.075D, 0.0D, 1.0D);
        MAXIMUM_PROBABILITY = builder
                .comment("Probability at curve_maximum_soul. Must not be below minimum_probability.")
                .defineInRange("maximum_probability", 0.35D, 0.0D, 1.0D);
        GUARANTEED_VALID_DAYS = builder
                .comment("Number of valid daily decisions before the global guarantee.")
                .defineInRange("guaranteed_valid_days", 6, 1, Integer.MAX_VALUE);
        COOLDOWN_TICKS = builder
                .comment("Shared global cooldown for daily and ritual probability checks.")
                .defineInRange("cooldown_ticks", 24_000L, 0L, Long.MAX_VALUE);
        MAXIMUM_EXTRA_DRAWS = builder
                .comment("Maximum extra encounter draws per player and global event.")
                .defineInRange("maximum_extra_draws", 999, 0, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private IllagerTreacheryConfig() {
    }

    public static TreacherySettings settings() {
        return TreacherySettings.sanitize(
                ENABLED.get(),
                MINIMUM_SOUL.get(),
                CURVE_MAXIMUM_SOUL.get(),
                MINIMUM_PROBABILITY.get(),
                MAXIMUM_PROBABILITY.get(),
                GUARANTEED_VALID_DAYS.get(),
                COOLDOWN_TICKS.get(),
                MAXIMUM_EXTRA_DRAWS.get()
        );
    }
}
