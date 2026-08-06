package com.casper.goetyarkham.curios;

import com.Polarice3.Goety.common.items.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemHandlerHelper;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Set;

/**
 * The single source of truth for which items may occupy any {@link
 * CurioSlotIds#RESOURCE} slot, backing both the pre-equip {@code
 * CurioEquipEvent} gate ({@link CuriosForgeEvents}) and a periodic
 * server-side sweep that catches content placed by means that bypass Curios
 * events entirely (commands, direct save-data edits, other mods writing to
 * the handler). The data-pack item tag ({@code
 * data/curios/tags/items/resource.json}) mirrors this same item set for
 * Curios' own tag-based slot-type validity check.
 *
 * <p>This policy is intentionally agnostic to which - if any - {@link
 * ResourceSlotProvider} is currently equipped: the four resource items are
 * always legal content for any {@code resource} slot the player currently
 * has, regardless of which provider(s) granted that capacity or whether a
 * given provider even recognizes a particular item (see {@link
 * ResourceSlotProvider#statBonus}).</p>
 *
 * <p>The slot holds at most one <em>item</em>, not necessarily a stack of
 * count 1 taken from the source - a player holding a stack of 64 Iron
 * Ingots may still place one into an empty slot, exactly like an item
 * frame. That count split is enforced structurally, not here: {@link
 * ResourceSlotStackHandler#getSlotLimit} caps the underlying {@code
 * IDynamicStackHandler} to 1, so Forge's own {@code insertItem}/{@code
 * SlotItemHandler} machinery - which already backs every transfer path
 * (mouse pickup-and-place, right-click auto-equip, shift-click quick move,
 * and creative-mode placement all resolve to the same {@code Slot}/{@code
 * IItemHandler} contract) - naturally takes exactly one and leaves the
 * remainder in the source stack. Nothing here ever manually splits a
 * stack, so there is no risk of the source being decremented twice.</p>
 */
public final class ResourceSlotContentPolicy {
    private ResourceSlotContentPolicy() {
    }

    public static boolean isAllowedItem(Item item) {
        return allowedItems().contains(item);
    }

    /**
     * Item-type-only legality, ignoring both the source stack's count and
     * whether the target slot is currently occupied. Used by {@link
     * #enforce} to decide whether an already-resident stack's item type is
     * legal at all, independent of the count-of-1 structural cap.
     */
    private static boolean isAllowedContent(ItemStack stack) {
        return !stack.isEmpty() && isAllowedItem(stack.getItem());
    }

    /**
     * Full pre-equip gate for {@link CuriosForgeEvents}: the incoming
     * stack's item must be one of the four allowed items, and the specific
     * slot index being targeted must currently be empty. This is what
     * blocks a same-item overflow (a second copy on top of the one already
     * worn) and a cross-item swap while occupied - both must be fully
     * rejected, not silently truncated, matching an item frame's "one slot,
     * no swapping while occupied" behavior. {@code CurioStacksHandler}
     * never itself calls {@code isItemValid} to revalidate an already
     * -equipped stack, so there is no legitimate case where the target
     * index is already occupied and equipping must still be allowed. The
     * count of the incoming stack itself is deliberately not checked here:
     * any positive count is legal to *attempt*, since only one unit is ever
     * actually taken (see the class doc).
     */
    public static boolean canEquip(SlotContext slotContext, ItemStack incoming) {
        if (incoming.isEmpty() || !isAllowedItem(incoming.getItem())) {
            return false;
        }
        return CuriosApi.getCuriosInventory(slotContext.entity())
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.RESOURCE))
                .map(handler -> isSlotAvailableFor(handler, slotContext.index()))
                .orElse(false);
    }

    private static boolean isSlotAvailableFor(ICurioStacksHandler handler, int index) {
        IDynamicStackHandler stacks = handler.getStacks();
        if (index < 0 || index >= stacks.getSlots()) {
            return false;
        }
        return stacks.getStackInSlot(index).isEmpty();
    }

    private static Set<Item> allowedItems() {
        return Set.of(
                Items.IRON_INGOT,
                Items.RABBIT_FOOT,
                Items.BOOK,
                ModItems.ECTOPLASM.get());
    }

    /**
     * Server-side fallback for content that ends up in a slot without
     * passing through {@code CurioEquipEvent} at all (e.g. {@code /data
     * modify entity}, a corrupted save load, or another mod writing to the
     * handler directly). An illegal item type is pulled out entirely; a
     * legal item present in an illegally large count (only reachable by
     * such a bypass, since every normal transfer path is capped to 1 by
     * {@link ResourceSlotStackHandler}) is trimmed down to keep exactly
     * 1 in the slot. Whatever is removed is safely returned to the wearer -
     * inserted into their inventory, or dropped at their feet if it
     * doesn't fit - never deleted.
     */
    public static void enforce(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(inventory -> {
            ICurioStacksHandler handler = inventory
                    .getStacksHandler(CurioSlotIds.RESOURCE)
                    .orElse(null);
            if (handler == null) {
                return;
            }
            IDynamicStackHandler stacks = handler.getStacks();
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                ItemStack stack = stacks.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!isAllowedContent(stack)) {
                    stacks.setStackInSlot(slot, ItemStack.EMPTY);
                    ItemHandlerHelper.giveItemToPlayer(player, stack);
                } else if (stack.getCount() > 1) {
                    ItemStack excess = stack.copy();
                    excess.shrink(1);
                    stacks.setStackInSlot(slot, stack.copyWithCount(1));
                    ItemHandlerHelper.giveItemToPlayer(player, excess);
                }
            }
        });
    }
}
