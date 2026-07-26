package com.casper.goetyarkham.stats;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum StatType {
    STRENGTH("strength"),
    AGILITY("agility"),
    WILLPOWER("willpower"),
    INTELLECT("intellect");

    private final String serializedName;

    StatType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<StatType> fromName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(stat -> stat.serializedName.equals(normalized))
                .findFirst();
    }
}
