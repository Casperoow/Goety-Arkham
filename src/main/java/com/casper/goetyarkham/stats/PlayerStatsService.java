package com.casper.goetyarkham.stats;

import com.casper.goetyarkham.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class PlayerStatsService {
    private PlayerStatsService() {
    }

    public static Optional<IPlayerStats> get(ServerPlayer player) {
        return player.getCapability(ModCapabilities.PLAYER_STATS).resolve();
    }

    public static Optional<StatSnapshot> setBase(ServerPlayer player, StatType stat, int value) {
        return setBase(mutable(player), stat, value);
    }

    public static Optional<StatSnapshot> addBase(ServerPlayer player, StatType stat, int amount) {
        return addBase(mutable(player), stat, amount);
    }

    public static Optional<Boolean> reset(ServerPlayer player) {
        return reset(mutable(player));
    }

    /**
     * Extension points for the later equipment, temporary-effect, and derived-stat phases.
     */
    public static boolean setEquipment(ServerPlayer player, StatType stat, int value) {
        return mutable(player).map(data -> data.setEquipment(stat, value)).orElse(false);
    }

    public static boolean setTemporary(ServerPlayer player, StatType stat, int value) {
        return mutable(player).map(data -> data.setTemporary(stat, value)).orElse(false);
    }

    public static boolean setDerived(ServerPlayer player, StatType stat, int value) {
        return mutable(player).map(data -> data.setDerived(stat, value)).orElse(false);
    }

    public static void sync(ServerPlayer player) {
        mutable(player).ifPresent(data -> ModNetwork.sendStats(player, data.serializeNBT()));
    }

    static Optional<PlayerStats> mutable(ServerPlayer player) {
        return player.getCapability(ModCapabilities.PLAYER_STATS)
                .resolve()
                .filter(PlayerStats.class::isInstance)
                .map(PlayerStats.class::cast);
    }

    static Optional<StatSnapshot> setBase(
            Optional<PlayerStats> data, StatType stat, int value) {
        return data.map(stats -> {
            stats.setBase(stat, value);
            return stats.get(stat);
        });
    }

    static Optional<StatSnapshot> addBase(
            Optional<PlayerStats> data, StatType stat, int amount) {
        return data.map(stats -> {
            stats.addBase(stat, amount);
            return stats.get(stat);
        });
    }

    static Optional<Boolean> reset(Optional<PlayerStats> data) {
        return data.map(PlayerStats::reset);
    }
}
