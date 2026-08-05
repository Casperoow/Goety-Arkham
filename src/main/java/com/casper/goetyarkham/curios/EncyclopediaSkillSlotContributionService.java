package com.casper.goetyarkham.curios;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Thin, {@link CurioSlotIds#ENCYCLOPEDIA_SKILL}-specific wrapper around
 * {@link DynamicCurioSlotContributionService}, the shared mechanism for any
 * Curio that grants extra slots on another Curios slot while worn (e.g. the
 * Arcane Initiate's Token / Book of Shadows on {@link CurioSlotIds#FOCUS}).
 */
public final class EncyclopediaSkillSlotContributionService {
    private EncyclopediaSkillSlotContributionService() {
    }

    public static void reconcile(
            ServerPlayer player,
            UUID modifierId,
            String modifierName,
            Supplier<? extends Item> item,
            List<String> wornSlots) {
        DynamicCurioSlotContributionService.reconcile(
                player, CurioSlotIds.ENCYCLOPEDIA_SKILL, modifierId, modifierName,
                item, wornSlots, 1.0D);
    }

    public static boolean isWearing(
            ServerPlayer player, Item item, List<String> wornSlots) {
        return DynamicCurioSlotContributionService.isWearing(player, item, wornSlots);
    }

    public static int equippedCount(
            ServerPlayer player, Item item, List<String> wornSlots) {
        return DynamicCurioSlotContributionService.equippedCount(player, item, wornSlots);
    }
}
