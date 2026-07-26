package com.casper.goetyarkham.client;

import com.casper.goetyarkham.stats.StatSnapshot;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side display cache. It has no setters and is only replaced by a server packet.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPlayerStats {
    private static Map<StatType, StatSnapshot> current = zeroSnapshot();

    private ClientPlayerStats() {
    }

    public static StatSnapshot get(StatType stat) {
        return current.get(stat);
    }

    public static Map<StatType, StatSnapshot> snapshot() {
        return current;
    }

    public static void acceptServerSnapshot(CompoundTag root) {
        EnumMap<StatType, StatSnapshot> updated = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            CompoundTag value = root.getCompound(type.serializedName());
            updated.put(type, new StatSnapshot(
                    value.getInt("base"),
                    value.getInt("equipment"),
                    value.getInt("temporary"),
                    value.getInt("derived")
            ));
        }
        current = Collections.unmodifiableMap(updated);
    }

    private static Map<StatType, StatSnapshot> zeroSnapshot() {
        EnumMap<StatType, StatSnapshot> result = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            result.put(type, new StatSnapshot(0, 0, 0, 0));
        }
        return Collections.unmodifiableMap(result);
    }
}
