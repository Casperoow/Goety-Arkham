package com.casper.goetyarkham.stats;

import java.util.Map;

/**
 * Read-only view of a player's authoritative attribute data.
 * Mutations are intentionally routed through {@link PlayerStatsService}.
 */
public interface IPlayerStats {
    StatSnapshot get(StatType stat);

    Map<StatType, StatSnapshot> snapshot();
}
