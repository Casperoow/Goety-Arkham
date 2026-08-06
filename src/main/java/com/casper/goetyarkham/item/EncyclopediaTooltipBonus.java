package com.casper.goetyarkham.item;

import com.casper.goetyarkham.stats.StatType;

import java.util.Map;

/**
 * Read-only snapshot of the Encyclopedia's own live {@code skill_bonus}
 * contribution, for Shift-tooltip display only. Never mutates slot
 * contents, capacity, or player attributes.
 *
 * @param available whether a live read was possible at all (false in
 *                   contexts with no local client player, e.g. main menu
 *                   or the creative item search)
 * @param equipped   whether the viewing player currently has an Encyclopedia
 *                   equipped (by item type, not by the specific {@code
 *                   ItemStack} instance being hovered - see {@link
 *                   EncyclopediaService#isWearing})
 * @param bonuses    this provider's own {@code +2}-per-item tally across the
 *                   player's current {@code skill_bonus} slot contents,
 *                   always containing every {@link StatType} (zero if
 *                   ungranted); when {@code equipped} is false this is the
 *                   same rule applied as a prediction, not an active bonus
 */
public record EncyclopediaTooltipBonus(
        boolean available, boolean equipped, Map<StatType, Integer> bonuses) {
    public static final EncyclopediaTooltipBonus UNAVAILABLE =
            new EncyclopediaTooltipBonus(false, false, Map.of());
}
