package com.casper.goetyarkham.stats;

import com.casper.goetyarkham.agility.AgilityEffects;
import com.casper.goetyarkham.attribute.StatAttributeBridge;
import com.casper.goetyarkham.network.ModNetwork;
import com.casper.goetyarkham.revelation.RevelationAttributeBridge;
import com.casper.goetyarkham.strength.StrengthEffects;
import com.casper.goetyarkham.willpower.WillpowerEffects;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

public final class PlayerStatsService {
    private PlayerStatsService() {
    }

    public static Optional<IPlayerStats> get(ServerPlayer player) {
        return player.getCapability(ModCapabilities.PLAYER_STATS).resolve();
    }

    /**
     * The authoritative final-value read path. Permanent values remain in the
     * capability; transient effects are composed here and never written back.
     */
    public static int getFinalValue(ServerPlayer player, StatType stat) {
        int storedFinal = get(player)
                .map(values -> values.get(stat).finalValue())
                .orElse(0);
        if (stat == StatType.WILLPOWER) {
            return saturatingAdd(
                    storedFinal,
                    com.casper.goetyarkham.effect.DreamsOfRlyehEffectService
                            .willpowerModifier(player));
        }
        if (stat == StatType.STRENGTH) {
            int withAura = saturatingAdd(
                    storedFinal,
                    com.casper.goetyarkham.effect.RitaChandlersAuraEffectService
                            .strengthModifier(player));
            int withRoland = saturatingAdd(
                    withAura,
                    com.casper.goetyarkham.item.RolandConditionalStrengthService
                            .strengthModifier(player));
            int withMachete = saturatingAdd(
                    withRoland,
                    com.casper.goetyarkham.item.MacheteStrengthBonusService
                            .strengthModifier(player));
            return saturatingAdd(
                    withMachete,
                    com.casper.goetyarkham.item.BaseballBatStrengthBonusService
                            .strengthModifier(player));
        }
        if (stat == StatType.INTELLECT) {
            return saturatingAdd(
                    storedFinal,
                    com.casper.goetyarkham.item.LockpicksIntellectBonusService
                            .intellectModifier(player));
        }
        if (stat == StatType.AGILITY) {
            return saturatingAdd(
                    storedFinal,
                    com.casper.goetyarkham.item.SwitchbladeAgilityBonusService
                            .agilityModifier(player));
        }
        return storedFinal;
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

    /** Atomically replaces every transient equipment component and syncs once. */
    public static boolean replaceEquipment(
            ServerPlayer player, Map<StatType, Integer> values) {
        return mutable(player).map(data -> data.replaceEquipment(values)).orElse(false);
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
        WillpowerEffects.refreshWillpowerEffects(player);
        RevelationAttributeBridge.refresh(player);
        com.casper.goetyarkham.item.RolandDamageBonusService.refresh(player);
        com.casper.goetyarkham.item.SwitchbladeDamageBonusService.refresh(player);
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

    private static int saturatingAdd(int left, int right) {
        long result = (long) left + right;
        return (int) Math.max(
                Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, result));
    }
}
