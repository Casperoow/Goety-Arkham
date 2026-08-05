package com.casper.goetyarkham.curios;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared mechanism for any Curio that grants a fixed number of extra slots
 * on some other Curios slot while worn (e.g. the Arcane Initiate's Token and
 * the Book of Shadows granting {@link CurioSlotIds#FOCUS} slots, or a future
 * Encyclopedia item granting {@link CurioSlotIds#ENCYCLOPEDIA_SKILL} slots).
 * Each contributing item keeps its own permanent slot modifier, identified
 * by its own {@link UUID}, on the target slot's {@code ICurioStacksHandler};
 * Curios sums every active modifier's amount itself, so multiple
 * contributors stack additively for free and each one can be added/removed
 * independently without touching the others' modifiers.
 *
 * <p>No persistent state is kept: whether a contributor's modifier should
 * exist is always recomputed live from whether a matching item is currently
 * equipped in one of that contributor's supported slots, so calling {@link
 * #reconcile} again on login, respawn, dimension change, or a duplicate
 * Curios change event never duplicates or loses the slot.</p>
 *
 * <p>Unlike Curios' own automatic resize recall ({@code
 * CurioStacksHandler#loseStacks}, which queues anything left in a shrinking
 * slot via {@code ICuriosItemHandler#loseInvalidStack} for the next Curios
 * tick), this service explicitly evacuates the about-to-vanish slots itself
 * <em>before</em> removing the modifier: it reads out the highest-indexed
 * slots (the ones Curios always trims from), clears them, and hands each
 * stack to {@link ItemHandlerHelper#giveItemToPlayer} (insert into the
 * player's inventory, or drop at their feet if it doesn't fit) right there.
 * By the time the modifier is actually removed and the handler resizes, the
 * vacating slots are already empty, so Curios' own recall path has nothing
 * left to do and never runs a second, duplicate hand-off.</p>
 */
public final class DynamicCurioSlotContributionService {
    private DynamicCurioSlotContributionService() {
    }

    public static void reconcile(
            ServerPlayer player,
            String targetSlotId,
            UUID modifierId,
            String modifierName,
            Supplier<? extends Item> item,
            List<String> wornSlots,
            double amount) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            if (isWearing(inventory, item.get(), wornSlots)) {
                ensureSlots(inventory, targetSlotId, modifierId, modifierName, amount);
            } else {
                removeSlotsSafely(player, inventory, targetSlotId, modifierId);
            }
        });
    }

    public static boolean isWearing(
            ServerPlayer player, Item item, List<String> wornSlots) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> isWearing(inventory, item, wornSlots))
                .orElse(false);
    }

    public static int equippedCount(
            ServerPlayer player, Item item, List<String> wornSlots) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> equippedCount(inventory, item, wornSlots))
                .orElse(0);
    }

    private static boolean ensureSlots(
            ICuriosItemHandler inventory,
            String targetSlotId,
            UUID modifierId,
            String modifierName,
            double amount) {
        ICurioStacksHandler handler = inventory
                .getStacksHandler(targetSlotId)
                .orElse(null);
        if (handler == null) {
            return false;
        }
        AttributeModifier existing = handler.getModifiers().get(modifierId);
        boolean changed = false;
        if (existing == null
                || existing.getAmount() != amount
                || existing.getOperation() != AttributeModifier.Operation.ADDITION) {
            if (existing != null) {
                inventory.removeSlotModifier(targetSlotId, modifierId);
            }
            inventory.addPermanentSlotModifier(
                    targetSlotId,
                    modifierId,
                    modifierName,
                    amount,
                    AttributeModifier.Operation.ADDITION);
            changed = true;
        }
        // getSlots applies a pending Curios resize synchronously.
        handler.getSlots();
        return changed;
    }

    private static boolean removeSlotsSafely(
            ServerPlayer player,
            ICuriosItemHandler inventory,
            String targetSlotId,
            UUID modifierId) {
        ICurioStacksHandler handler = inventory
                .getStacksHandler(targetSlotId)
                .orElse(null);
        if (handler == null) {
            return false;
        }
        AttributeModifier existing = handler.getModifiers().get(modifierId);
        if (existing == null) {
            return false;
        }
        int shrinkBy = (int) Math.round(existing.getAmount());
        evacuateTopSlots(player, handler, shrinkBy);
        inventory.removeSlotModifier(targetSlotId, modifierId);
        // getSlots applies the pending shrink synchronously. The vacated
        // slots were already cleared above, so Curios' own resize recall
        // has nothing left to queue and this can never double-return items.
        handler.getSlots();
        return true;
    }

    /**
     * Explicitly reads out and clears the {@code count} highest-indexed
     * slots of {@code handler} - the exact slots Curios itself would trim
     * on a shrink (see {@code CurioStacksHandler#loseStacks}) - and hands
     * any non-empty stack found there back to {@code player} immediately:
     * inserted into their inventory if it fits, or dropped as a normal item
     * entity at their feet otherwise. Never deletes or duplicates a stack.
     */
    private static void evacuateTopSlots(
            ServerPlayer player, ICurioStacksHandler handler, int count) {
        if (count <= 0) {
            return;
        }
        IDynamicStackHandler stacks = handler.getStacks();
        int total = stacks.getSlots();
        for (int offset = 0; offset < count; offset++) {
            int slot = total - 1 - offset;
            if (slot < 0) {
                return;
            }
            ItemStack stack = stacks.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            stacks.setStackInSlot(slot, ItemStack.EMPTY);
            ItemHandlerHelper.giveItemToPlayer(player, stack);
        }
    }

    private static boolean isWearing(
            ICuriosItemHandler inventory, Item item, List<String> wornSlots) {
        return equippedCount(inventory, item, wornSlots) > 0;
    }

    private static int equippedCount(
            ICuriosItemHandler inventory, Item item, List<String> wornSlots) {
        int count = 0;
        for (String slotId : wornSlots) {
            count += countInSlot(inventory, item, slotId);
        }
        return count;
    }

    private static int countInSlot(
            ICuriosItemHandler inventory, Item item, String slotId) {
        return inventory.getStacksHandler(slotId)
                .map(handler -> {
                    int count = 0;
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        if (stacks.getStackInSlot(slot).is(item)) {
                            count++;
                        }
                    }
                    return count;
                })
                .orElse(0);
    }
}
