package com.casper.goetyarkham.chaosbag;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A token definition. Duplicate entries in a bag remain separate list entries;
 * equality here describes the printed token type, not a drawn instance.
 */
public record ChaosToken(Kind kind, int value) {
    public static final ChaosToken GHOUL = named(Kind.GHOUL);
    public static final ChaosToken CULTIST = named(Kind.CULTIST);
    public static final ChaosToken TABLET = named(Kind.TABLET);
    public static final ChaosToken AUTO_FAIL = named(Kind.AUTO_FAIL);
    public static final ChaosToken ELDER_SIGN = named(Kind.ELDER_SIGN);

    public ChaosToken {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        if (kind != Kind.NUMBER && value != 0) {
            throw new IllegalArgumentException("named tokens cannot carry a value");
        }
    }

    public static ChaosToken number(int value) {
        return new ChaosToken(Kind.NUMBER, value);
    }

    public static ChaosToken named(Kind kind) {
        if (kind == Kind.NUMBER) {
            throw new IllegalArgumentException("Use number(value) for numeric tokens");
        }
        return new ChaosToken(kind, 0);
    }

    public String serializedName() {
        return kind == Kind.NUMBER ? Integer.toString(value) : kind.serializedName;
    }

    public String translationKey() {
        return "chaos_token.goetyarkham." + kind.serializedName;
    }

    public static Optional<ChaosToken> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        try {
            return Optional.of(number(Integer.parseInt(normalized)));
        } catch (NumberFormatException ignored) {
            return Arrays.stream(Kind.values())
                    .filter(kind -> kind != Kind.NUMBER)
                    .filter(kind -> kind.serializedName.equals(normalized))
                    .findFirst()
                    .map(ChaosToken::named);
        }
    }

    public enum Kind {
        NUMBER("number"),
        GHOUL("ghoul"),
        CULTIST("cultist"),
        TABLET("tablet"),
        AUTO_FAIL("auto_fail"),
        ELDER_SIGN("elder_sign");

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }
}
