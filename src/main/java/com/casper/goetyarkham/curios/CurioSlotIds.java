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
    /**
     * Shared "Bonus Slot" pool: grants no capacity of its own (base size 0).
     * Any number of Curios can each independently declare how many of these
     * slots they grant while worn (see {@link SharedBonusSlotProvider}); the
     * slot's actual capacity is the maximum declared by any one currently
     * equipped, deduplicated-by-provider-ID contributor - never a sum - and
     * every equipped contributor independently scores whatever skill items
     * (Iron Ingot, Rabbit's Foot, Book, Goety's Ectoplasm) sit in these
     * slots. See {@link SharedBonusSlotService}.
     */
    public static final String SKILL_BONUS = "skill_bonus";

    public static final List<String> ALL = List.of(
            NECKLACE,
            BODY,
            TOKEN,
            HANDS,
            BOOK,
            FOCUS,
            TAROT,
            ASSET,
            SKILL_BONUS,
            TALENT,
            WEAKNESS
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
            Map.entry(SKILL_BONUS, 0)
    );

    private CurioSlotIds() {
    }
}
