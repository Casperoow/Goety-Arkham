package com.casper.goetyarkham.stats;

import com.casper.goetyarkham.agility.AgilityEffects;
import com.casper.goetyarkham.attribute.StatAttributeBridge;
import com.casper.goetyarkham.network.ModNetwork;
import com.casper.goetyarkham.strength.StrengthEffects;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class PlayerStatsService {
    private PlayerStatsService() {
    }

    public static Optional<IPlayerStats> get(ServerPlayer player) {
        return player.getCapability(ModCapabilities.PLAYER_STATS).resolve();
    }

    public static Optional<StatSnapshot> setBase(ServerPlayer player, StatType stat, int value) {
        Optional<StatSnapshot> result = setBase(mutable(player), stat, value);
        result.ifPresent(ignored -> refreshEffects(player));
        return result;
    }

    public static Optional<StatSnapshot> addBase(ServerPlayer player, StatType stat, int amount) {
        Optional<StatSnapshot> result = addBase(mutable(player), stat, amount);
        result.ifPresent(ignored -> refreshEffects(player));
        return result;
    }

    public static Optional<Boolean> reset(ServerPlayer player) {
        Optional<Boolean> result = reset(mutable(player));
        result.ifPresent(ignored -> refreshEffects(player));
        return result;
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
        StatAttributeBridge.syncToAttributes(player);
        refreshEffects(player);
    }

    public static void refreshEffects(ServerPlayer player) {
        StrengthEffects.refreshStrengthEffects(player);
        AgilityEffects.refreshAgilityEffects(player);
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
