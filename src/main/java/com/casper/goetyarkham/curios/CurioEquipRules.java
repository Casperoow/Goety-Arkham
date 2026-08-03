package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * Shared duplicate-equip rule for every Goety: Arkham Curio: a player may
 * never have two equipped instances of the same mod-owned Curio item at
 * once, no matter which Curios slot(s) each instance occupies. This is the
 * single traversal used by all of the mod's Curios instead of each item
 * class repeating the same check.
 */
public final class CurioEquipRules {
    private CurioEquipRules() {
    }

    /** True for items registered by this mod; other mods' Curios are never restricted. */
    public static boolean isOwnedByThisMod(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && GoetyArkham.MOD_ID.equals(id.getNamespace());
    }

    /**
     * True when {@code candidate} is a mod-owned Curio that is already
     * equipped in some Curios slot other than the one described by
     * {@code target}. The target's own current slot is excluded so
     * revalidating an item already sitting in place is never treated as a
     * duplicate of itself.
     */
    public static boolean isDuplicateElsewhere(SlotContext target, ItemStack candidate) {
        if (candidate.isEmpty() || !isOwnedByThisMod(candidate.getItem())) {
            return false;
        }
        LivingEntity entity = target.entity();
        if (entity == null) {
            return false;
        }
        return CuriosApi.getCuriosInventory(entity)
                .resolve()
                .map(inventory -> inventory.findCurios(candidate.getItem()).stream()
                        .anyMatch(result -> !isSameSlot(result.slotContext(), target)))
                .orElse(false);
    }

    private static boolean isSameSlot(SlotContext a, SlotContext b) {
        return a.identifier().equals(b.identifier()) && a.index() == b.index();
    }
}
