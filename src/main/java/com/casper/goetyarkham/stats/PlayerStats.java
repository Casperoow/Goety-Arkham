package com.casper.goetyarkham.stats;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

final class PlayerStats implements IPlayerStats {
    static final int MIN_VALUE = -1000;
    static final int MAX_VALUE = 1000;

    private final EnumMap<StatType, MutableStat> stats = new EnumMap<>(StatType.class);
    private Runnable onChanged = () -> {
    };

    PlayerStats() {
        for (StatType stat : StatType.values()) {
            stats.put(stat, new MutableStat());
        }
    }

    void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public StatSnapshot get(StatType stat) {
        return stats.get(stat).snapshot();
    }

    @Override
    public Map<StatType, StatSnapshot> snapshot() {
        EnumMap<StatType, StatSnapshot> result = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) {
            result.put(stat, get(stat));
        }
        return Collections.unmodifiableMap(result);
    }

    boolean setBase(StatType stat, int value) {
        MutableStat entry = stats.get(stat);
        int clamped = clamp(value);
        if (entry.base == clamped) {
            return false;
        }
        entry.base = clamped;
        onChanged.run();
        return true;
    }

    boolean addBase(StatType stat, int amount) {
        MutableStat entry = stats.get(stat);
        long result = (long) entry.base + amount;
        return setBase(stat, clamp(result));
    }

    boolean setEquipment(StatType stat, int value) {
        return setComponent(stat, ComponentSource.EQUIPMENT, value);
    }

    boolean setTemporary(StatType stat, int value) {
        return setComponent(stat, ComponentSource.TEMPORARY, value);
    }

    boolean setDerived(StatType stat, int value) {
        return setComponent(stat, ComponentSource.DERIVED, value);
    }

    boolean reset() {
        boolean changed = false;
        for (MutableStat stat : stats.values()) {
            changed |= stat.reset();
        }
        if (changed) {
            onChanged.run();
        }
        return changed;
    }

    void copyFrom(PlayerStats other) {
        for (StatType stat : StatType.values()) {
            stats.get(stat).copyFrom(other.stats.get(stat));
        }
    }

    CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        for (StatType type : StatType.values()) {
            MutableStat stat = stats.get(type);
            CompoundTag value = new CompoundTag();
            value.putInt("base", stat.base);
            value.putInt("equipment", stat.equipment);
            value.putInt("temporary", stat.temporary);
            value.putInt("derived", stat.derived);
            root.put(type.serializedName(), value);
        }
        return root;
    }

    void deserializeNBT(CompoundTag root) {
        for (StatType type : StatType.values()) {
            if (!root.contains(type.serializedName(), CompoundTag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag value = root.getCompound(type.serializedName());
            MutableStat stat = stats.get(type);
            stat.base = clamp(value.getInt("base"));
            stat.equipment = clamp(value.getInt("equipment"));
            stat.temporary = clamp(value.getInt("temporary"));
            stat.derived = clamp(value.getInt("derived"));
        }
    }

    private boolean setComponent(StatType stat, ComponentSource source, int value) {
        MutableStat entry = stats.get(stat);
        int clamped = clamp(value);
        int previous = source.get(entry);
        if (previous == clamped) {
            return false;
        }
        source.set(entry, clamped);
        onChanged.run();
        return true;
    }

    private static int clamp(long value) {
        return (int) Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    private enum ComponentSource {
        EQUIPMENT {
            @Override
            int get(MutableStat stat) {
                return stat.equipment;
            }

            @Override
            void set(MutableStat stat, int value) {
                stat.equipment = value;
            }
        },
        TEMPORARY {
            @Override
            int get(MutableStat stat) {
                return stat.temporary;
            }

            @Override
            void set(MutableStat stat, int value) {
                stat.temporary = value;
            }
        },
        DERIVED {
            @Override
            int get(MutableStat stat) {
                return stat.derived;
            }

            @Override
            void set(MutableStat stat, int value) {
                stat.derived = value;
            }
        };

        abstract int get(MutableStat stat);

        abstract void set(MutableStat stat, int value);
    }

    private static final class MutableStat {
        private int base;
        private int equipment;
        private int temporary;
        private int derived;

        private StatSnapshot snapshot() {
            return new StatSnapshot(base, equipment, temporary, derived);
        }

        private boolean reset() {
            boolean changed = base != 0 || equipment != 0 || temporary != 0 || derived != 0;
            base = 0;
            equipment = 0;
            temporary = 0;
            derived = 0;
            return changed;
        }

        private void copyFrom(MutableStat other) {
            base = other.base;
            equipment = other.equipment;
            temporary = other.temporary;
            derived = other.derived;
        }
    }
}
