package com.casper.goetyarkham.stats;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.SharedBonusSlotProvider;
import com.casper.goetyarkham.curios.SharedBonusSlotProviderRegistry;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Rebuilds the transient Player Stats equipment phase from equipped Curios. */
public final class EquipmentStatsService {
    private EquipmentStatsService() {
    }

    public static boolean refresh(ServerPlayer player) {
        EnumMap<StatType, Integer> totals = emptyTotals();
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(inventory ->
                inventory.getCurios().forEach((slotId, handler) ->
                        addContributions(player, slotId, handler, totals)));

        boolean changed = PlayerStatsService.replaceEquipment(player, totals);
        if (!changed) {
            // Lifecycle refreshes still need to synchronize the current values.
            PlayerStatsService.sync(player);
        }
        SoulEnergyPoolService.refresh(player);
        return changed;
    }

    static EnumMap<StatType, Integer> emptyTotals() {
        EnumMap<StatType, Integer> totals = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) {
            totals.put(stat, 0);
        }
        return totals;
    }

    static void addContribution(
            Map<StatType, Integer> totals,
            EquipmentStatModifier modifier,
            SlotContext slotContext,
            ItemStack stack) {
        for (StatType stat : StatType.values()) {
            int contribution = modifier.getEquipmentStatModifier(
                    stat, slotContext, stack);
            totals.compute(stat, (ignored, current) -> saturatingAdd(
                    current == null ? 0 : current,
                    contribution));
        }
    }

    private static void addContributions(
            ServerPlayer player,
            String slotId,
            ICurioStacksHandler handler,
            Map<StatType, Integer> totals) {
        if (CurioSlotIds.SKILL_BONUS.equals(slotId)) {
            addSharedBonusSlotContributions(player, handler, totals);
            return;
        }
        for (int slot = 0; slot < handler.getStacks().getSlots(); slot++) {
            ItemStack stack = handler.getStacks().getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof EquipmentStatModifier modifier) {
                addContribution(
                        totals,
                        modifier,
                        new SlotContext(slotId, player, slot, false, true),
                        stack);
            }
        }
    }

    /**
     * Every currently equipped {@link SharedBonusSlotProvider} independently
     * scans every item sitting in every {@link CurioSlotIds#SKILL_BONUS}
     * slot and scores it under its own rule; different providers' bonuses
     * for the same item stack additively, and a provider that does not
     * recognize an item contributes 0 for it.
     */
    private static void addSharedBonusSlotContributions(
            ServerPlayer player,
            ICurioStacksHandler handler,
            Map<StatType, Integer> totals) {
        List<SharedBonusSlotProvider> providers = SharedBonusSlotProviderRegistry.providers();
        if (providers.isEmpty()) {
            return;
        }
        for (int slot = 0; slot < handler.getStacks().getSlots(); slot++) {
            ItemStack stack = handler.getStacks().getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            for (SharedBonusSlotProvider provider : providers) {
                if (!provider.isEquipped(player)) {
                    continue;
                }
                for (StatType stat : StatType.values()) {
                    int contribution = provider.statBonus(stat, item);
                    totals.compute(stat, (ignored, current) -> saturatingAdd(
                            current == null ? 0 : current,
                            contribution));
                }
            }
        }
    }

    private static int saturatingAdd(int left, int right) {
        long result = (long) left + right;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
    }
}
