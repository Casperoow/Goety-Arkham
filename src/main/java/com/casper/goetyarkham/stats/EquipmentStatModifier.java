package com.casper.goetyarkham.stats;

import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/** Supplies transient Player Stats bonuses from a functionally equipped Curio. */
public interface EquipmentStatModifier {
    int getEquipmentStatModifier(
            StatType stat, SlotContext slotContext, ItemStack stack);
}
