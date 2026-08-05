package com.casper.goetyarkham.stats;

import com.Polarice3.Goety.common.items.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Attribute bonus granted by the single item sitting in {@link
 * com.casper.goetyarkham.curios.CurioSlotIds#ENCYCLOPEDIA_SKILL}. None of
 * the four permitted items (three vanilla, one from Goety) can implement
 * {@link EquipmentStatModifier} themselves, so {@link EquipmentStatsService}
 * special-cases this one slot and consults this mapping instead.
 */
public final class EncyclopediaSkillStatContribution {
    private static final Map<Item, StatType> BONUS_BY_ITEM = Map.of(
            Items.IRON_INGOT, StatType.STRENGTH,
            Items.RABBIT_FOOT, StatType.AGILITY,
            Items.BOOK, StatType.INTELLECT,
            ModItems.ECTOPLASM.get(), StatType.WILLPOWER);

    private static final int BONUS_AMOUNT = 2;

    private EncyclopediaSkillStatContribution() {
    }

    /** Zero for every stat except the one this item's type grants +2 to. */
    public static int getBonus(StatType stat, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        StatType granted = BONUS_BY_ITEM.get(stack.getItem());
        return granted == stat ? BONUS_AMOUNT : 0;
    }
}
