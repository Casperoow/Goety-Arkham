package com.casper.goetyarkham.curios;

import java.util.List;
import java.util.Map;

/**
 * Stable Curios slot identifiers used by Goety: Arkham.
 *
 * <p>The data-pack definitions are authoritative for registration. These constants keep
 * future slot lookups from introducing divergent string literals.</p>
 */
public final class CurioSlotIds {
    /** Goety-provided charm slot. Goety: Arkham does not register or resize it. */
    public static final String CHARM = "charm";
    public static final String NECKLACE = "necklace";
    public static final String BODY = "body";
    public static final String TOKEN = "token";
    public static final String HANDS = "hands";
    public static final String FOCUS = "focus";
    public static final String TAROT = "tarot";
    public static final String ASSET = "asset";
    public static final String TALENT = "talent";
    public static final String WEAKNESS = "weakness";

    public static final List<String> ALL = List.of(
            NECKLACE,
            BODY,
            TOKEN,
            HANDS,
            FOCUS,
            TAROT,
            ASSET,
            TALENT,
            WEAKNESS
    );

    public static final Map<String, Integer> BASE_SIZES = Map.of(
            NECKLACE, 2,
            BODY, 1,
            TOKEN, 1,
            HANDS, 2,
            FOCUS, 2,
            TAROT, 1,
            ASSET, 4,
            TALENT, 2,
            WEAKNESS, 1
    );

    private CurioSlotIds() {
    }
}
