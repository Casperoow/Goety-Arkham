package com.casper.goetyarkham.curios;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared mechanism for any Curio that grants +1 {@link CurioSlotIds#FOCUS}
 * Curios slot while worn (e.g. the Arcane Initiate's Token, the Book of
 * Shadows). Each contributing item keeps its own permanent slot modifier,
 * identified by its own {@link UUID}, on the focus slot's {@code
 * ICurioStacksHandler}; Curios sums every active modifier's amount itself,
 * so multiple contributors stack additively for free and each one can be
 * added/removed independently without touching the others' modifiers.
 *
 * <p>No persistent state is kept: whether a contributor's modifier should
 * exist is always recomputed live from whether a matching item is currently
 * equipped in one of that contributor's supported slots, so calling {@link
 * #reconcile} again on login, respawn, dimension change, or a duplicate
 * Curios change event never duplicates or loses the slot. Anything left in
 * a slot that shrinks away is safely recalled by Curios' own resize path
 * ({@code CurioStacksHandler#loseStacks} queues it via {@code
 * ICuriosItemHandler#loseInvalidStack}, which the next Curios tick hands to
 * the wearer's inventory via {@code handleInvalidStacks}) — never deleted
 * directly.</p>
 */
public final class FocusSlotContributionService {
    private FocusSlotContributionService() {
    }

    public static void reconcile(
            ServerPlayer player,
            UUID modifierId,
            String modifierName,
            Supplier<? extends Item> item,
            List<String> wornSlots) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            if (isWearing(inventory, item.get(), wornSlots)) {
                ensureFocusSlot(inventory, modifierId, modifierName);
            } else {
                removeFocusSlot(inventory, modifierId);
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

    private static boolean ensureFocusSlot(
            ICuriosItemHandler inventory, UUID modifierId, String modifierName) {
        ICurioStacksHandler handler = inventory
                .getStacksHandler(CurioSlotIds.FOCUS)
                .orElse(null);
        if (handler == null) {
            return false;
        }
        AttributeModifier existing = handler.getModifiers().get(modifierId);
        boolean changed = false;
        if (existing == null
                || existing.getAmount() != 1.0D
                || existing.getOperation() != AttributeModifier.Operation.ADDITION) {
            if (existing != null) {
                inventory.removeSlotModifier(CurioSlotIds.FOCUS, modifierId);
            }
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.FOCUS,
                    modifierId,
                    modifierName,
                    1.0D,
                    AttributeModifier.Operation.ADDITION);
            changed = true;
        }
        // getSlots applies a pending Curios resize synchronously.
        handler.getSlots();
        return changed;
    }

    private static boolean removeFocusSlot(ICuriosItemHandler inventory, UUID modifierId) {
        ICurioStacksHandler handler = inventory
                .getStacksHandler(CurioSlotIds.FOCUS)
                .orElse(null);
        if (handler == null || !handler.getModifiers().containsKey(modifierId)) {
            return false;
        }
        inventory.removeSlotModifier(CurioSlotIds.FOCUS, modifierId);
        // getSlots applies the pending shrink synchronously; Curios itself
        // safely recalls anything left in the vacated slot (see class doc).
        handler.getSlots();
        return true;
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
