package com.casper.goetyarkham.chaosbag;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ChaosBagLevel {
    EASY("easy", numbers(1, 1, 0, 0, 0, -1, -1, -1, -2, -2)),
    NORMAL("normal", numbers(1, 0, 0, -1, -1, -1, -2, -2, -3, -4)),
    HARD("hard", numbers(0, 0, 0, -1, -1, -2, -2, -3, -3, -4, -5)),
    EXPERT("expert", numbers(0, -1, -1, -2, -2, -3, -3, -4, -4, -5, -6, -8));

    private final String serializedName;
    private final List<ChaosToken> baseTokens;

    ChaosBagLevel(String serializedName, List<ChaosToken> numericTokens) {
        this.serializedName = serializedName;
        java.util.ArrayList<ChaosToken> tokens =
                new java.util.ArrayList<>(numericTokens);
        tokens.add(ChaosToken.GHOUL);
        tokens.add(ChaosToken.GHOUL);
        tokens.add(ChaosToken.CULTIST);
        tokens.add(ChaosToken.TABLET);
        tokens.add(ChaosToken.AUTO_FAIL);
        tokens.add(ChaosToken.ELDER_SIGN);
        this.baseTokens = List.copyOf(tokens);
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "chaos_bag.level.goetyarkham." + serializedName;
    }

    public List<ChaosToken> baseTokens() {
        return baseTokens;
    }

    public boolean isHardOrExpert() {
        return this == HARD || this == EXPERT;
    }

    public static Optional<ChaosBagLevel> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(level -> level.serializedName.equals(normalized))
                .findFirst();
    }

    private static List<ChaosToken> numbers(int... values) {
        return Arrays.stream(values).mapToObj(ChaosToken::number).toList();
    }
}
