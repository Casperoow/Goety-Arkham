package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.UUID;

/**
 * Owns the single +1 {@link CurioSlotIds#FOCUS} Curios slot granted by an
 * equipped Arcane Initiate's Token.
 *
 * <p>Unlike the Heirloom of Hyperborea / Wendy's Amulet weakness slot, this
 * extra slot is never auto-filled with a generated item, so the whole
 * lifecycle collapses to keeping one permanent slot modifier in sync with
 * whether the token is currently worn: no persistent NBT state is needed,
 * and {@link #reconcile} can simply be called again on every login,
 * respawn, dimension change, or player clone. Any focus left in the extra
 * slot when it shrinks is handed back by Curios' own resize path
 * ({@code CurioStacksHandler#loseStacks} queues it via
 * {@code ICuriosItemHandler#loseInvalidStack}, which the next Curios tick
 * hands to the wearer's inventory via {@code handleInvalidStacks}) — the
 * exact same safe-recall mechanism the weakness slot already relies on.
 * Never deleted directly.</p>
 */
public final class ArcaneInitiatesTokenService {
    public static final UUID FOCUS_SLOT_MODIFIER_ID = UUID.fromString(
            "a3c1f2e7-8b4d-4e6a-9f21-7d5c3a8b4e60");
    public static final String FOCUS_SLOT_MODIFIER_NAME =
            "goetyarkham:arcane_initiates_token";

    private ArcaneInitiatesTokenService() {
    }

    public static boolean equipTransition(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(ArcaneInitiatesTokenService::ensureFocusSlot)
                .orElse(false);
    }

    public static boolean unequipTransition(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(ArcaneInitiatesTokenService::removeFocusSlot)
                .orElse(false);
    }

    /**
     * Login/clone/dimension/sync-safe repair. Stateless: it only looks at
     * whether the token is currently worn, so calling it repeatedly (or out
     * of order with the direct equip/unequip calls) never duplicates or
     * loses the slot modifier.
     */
    public static void reconcile(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            if (isWearing(inventory)) {
                ensureFocusSlot(inventory);
            } else {
                removeFocusSlot(inventory);
            }
        });
    }

    public static boolean isWearing(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(ArcaneInitiatesTokenService::isWearing)
                .orElse(false);
    }

    public static int equippedCount(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(ArcaneInitiatesTokenService::equippedCount)
                .orElse(0);
    }

    private static boolean ensureFocusSlot(ICuriosItemHandler inventory) {
        ICurioStacksHandler handler = inventory
                .getStacksHandler(CurioSlotIds.FOCUS)
                .orElse(null);
        if (handler == null) {
            return false;
        }
        AttributeModifier existing = handler.getModifiers().get(FOCUS_SLOT_MODIFIER_ID);
        boolean changed = false;
        if (existing == null
                || existing.getAmount() != 1.0D
                || existing.getOperation() != AttributeModifier.Operation.ADDITION) {
            if (existing != null) {
                inventory.removeSlotModifier(
                        CurioSlotIds.FOCUS, FOCUS_SLOT_MODIFIER_ID);
            }
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.FOCUS,
                    FOCUS_SLOT_MODIFIER_ID,
                    FOCUS_SLOT_MODIFIER_NAME,
                    1.0D,
                    AttributeModifier.Operation.ADDITION);
            changed = true;
        }
        // getSlots applies a pending Curios resize synchronously.
        handler.getSlots();
        return changed;
    }

    private static boolean removeFocusSlot(ICuriosItemHandler inventory) {
        if (!hasFocusSlotModifier(inventory)) {
            return false;
        }
        inventory.removeSlotModifier(CurioSlotIds.FOCUS, FOCUS_SLOT_MODIFIER_ID);
        // getSlots applies the pending shrink synchronously; Curios itself
        // safely recalls anything left in the vacated slot (see class doc).
        inventory.getStacksHandler(CurioSlotIds.FOCUS)
                .ifPresent(ICurioStacksHandler::getSlots);
        return true;
    }

    private static boolean hasFocusSlotModifier(ICuriosItemHandler inventory) {
        return inventory.getStacksHandler(CurioSlotIds.FOCUS)
                .map(handler -> handler.getModifiers()
                        .containsKey(FOCUS_SLOT_MODIFIER_ID))
                .orElse(false);
    }

    private static boolean isWearing(ICuriosItemHandler inventory) {
        return equippedCount(inventory) > 0;
    }

    private static int equippedCount(ICuriosItemHandler inventory) {
        return inventory.getStacksHandler(CurioSlotIds.TOKEN)
                .map(handler -> {
                    int count = 0;
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        if (stacks.getStackInSlot(slot).is(
                                ModItems.ARCANE_INITIATES_TOKEN.get())) {
                            count++;
                        }
                    }
                    return count;
                })
                .orElse(0);
    }
}
