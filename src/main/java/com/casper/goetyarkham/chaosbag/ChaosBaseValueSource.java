package com.casper.goetyarkham.chaosbag;

import com.casper.goetyarkham.stats.StatType;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ChaosBaseValueSource {
    STRENGTH("strength", StatType.STRENGTH),
    AGILITY("agility", StatType.AGILITY),
    WILLPOWER("willpower", StatType.WILLPOWER),
    INTELLECT("intellect", StatType.INTELLECT),
    FIXED("fixed", null);

    private final String serializedName;
    private final StatType stat;

    ChaosBaseValueSource(String serializedName, StatType stat) {
        this.serializedName = serializedName;
        this.stat = stat;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "chaos_check.base_source.goetyarkham." + serializedName;
    }

    public Optional<StatType> stat() {
        return Optional.ofNullable(stat);
    }

    public static Optional<ChaosBaseValueSource> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(normalized))
                .findFirst();
    }
}
