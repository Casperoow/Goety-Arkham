package com.casper.goetyarkham.curios;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.Comparator;
import java.util.List;

/** Runtime view of every Curios slot currently bound to players. */
public final class CurioSlotReport {
    private CurioSlotReport() {
    }

    public static List<ISlotType> playerSlots(Level level) {
        return CuriosApi.getEntitySlots(EntityType.PLAYER, level).values().stream()
                .sorted(Comparator.comparingInt(ISlotType::getOrder)
                        .thenComparing(ISlotType::getIdentifier))
                .toList();
    }

    public static String translationKey(ISlotType slot) {
        return "curios.identifier." + slot.getIdentifier();
    }

    public static Component commandLine(ISlotType slot) {
        return Component.literal(slot.getIdentifier())
                .append(" — ")
                .append(Component.translatable(translationKey(slot)))
                .append(" — ")
                .append(Integer.toString(slot.getSize()));
    }
}
