package com.casper.goetyarkham.sanity.weakness;

import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * Server-authoritative contract for weakness items that ordinary inventory
 * operations must not remove. System-owned cleanup may bypass this contract
 * by writing the Curios stack handler directly.
 */
public interface ILockedWeakness {
    default boolean preventsManualUnequip(
            SlotContext slotContext, ItemStack stack) {
        return true;
    }
}
