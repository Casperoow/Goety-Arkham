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
    /** Extra slot granted only by other items' dynamic modifiers; base size is 0. */
    public static final String BOOK = "book";
    public static final String FOCUS = "focus";
    public static final String TAROT = "tarot";
    public static final String ASSET = "asset";
    public static final String TALENT = "talent";
    public static final String WEAKNESS = "weakness";
    /** Extra slot granted only by other items' dynamic modifiers; base size is 0. */
    public static final String ENCYCLOPEDIA_SKILL = "encyclopedia_skill";

    public static final List<String> ALL = List.of(
            NECKLACE,
            BODY,
            TOKEN,
            HANDS,
            BOOK,
            FOCUS,
            TAROT,
            ASSET,
            TALENT,
            WEAKNESS,
            ENCYCLOPEDIA_SKILL
    );

    public static final Map<String, Integer> BASE_SIZES = Map.ofEntries(
            Map.entry(NECKLACE, 2),
            Map.entry(BODY, 1),
            Map.entry(TOKEN, 1),
            Map.entry(HANDS, 2),
            Map.entry(BOOK, 0),
            Map.entry(FOCUS, 2),
            Map.entry(TAROT, 1),
            Map.entry(ASSET, 4),
            Map.entry(TALENT, 2),
            Map.entry(WEAKNESS, 1),
            Map.entry(ENCYCLOPEDIA_SKILL, 0)
    );

    private CurioSlotIds() {
    }
}
