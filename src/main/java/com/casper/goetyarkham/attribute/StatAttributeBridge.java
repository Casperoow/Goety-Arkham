package com.casper.goetyarkham.attribute;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.stats.IPlayerStats;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

/**
 * One-way display mirror from the authoritative PlayerStats capability to
 * syncable Forge attributes. These attributes must never feed stat calculations.
 */
public final class StatAttributeBridge {
    private StatAttributeBridge() {
    }

    public static void syncToAttributes(ServerPlayer player) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (capability.isEmpty()) {
            GoetyArkham.LOGGER.debug(
                    "[StatAttributeBridge] Skipped mirror sync because PlayerStats is unavailable: player={}, uuid={}",
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
            return;
        }

        IPlayerStats stats = capability.get();
        mirror(player, ModAttributes.STRENGTH, StatType.STRENGTH,
                stats.get(StatType.STRENGTH).finalValue());
        mirror(player, ModAttributes.AGILITY, StatType.AGILITY,
                stats.get(StatType.AGILITY).finalValue());
        mirror(player, ModAttributes.WILLPOWER, StatType.WILLPOWER,
                stats.get(StatType.WILLPOWER).finalValue());
        mirror(player, ModAttributes.INTELLECT, StatType.INTELLECT,
                stats.get(StatType.INTELLECT).finalValue());
    }

    private static void mirror(
            ServerPlayer player,
            RegistryObject<Attribute> attribute,
            StatType stat,
            int finalValue) {
        if (!attribute.isPresent()) {
            GoetyArkham.LOGGER.debug(
                    "[StatAttributeBridge] Skipped unregistered mirror attribute: player={}, uuid={}, stat={}",
                    player.getGameProfile().getName(),
                    player.getUUID(),
                    stat.serializedName()
            );
            return;
        }

        AttributeInstance instance = player.getAttribute(attribute.get());
        if (instance == null) {
            GoetyArkham.LOGGER.debug(
                    "[StatAttributeBridge] Player is missing mirror AttributeInstance: player={}, uuid={}, stat={}",
                    player.getGameProfile().getName(),
                    player.getUUID(),
                    stat.serializedName()
            );
            return;
        }

        double mirroredValue = finalValue;
        if (Double.compare(instance.getBaseValue(), mirroredValue) != 0) {
            instance.setBaseValue(mirroredValue);
        }
    }
}
