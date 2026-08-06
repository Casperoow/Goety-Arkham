package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.UUID;

/**
 * Owns the single +1 weakness slot and the Cover Up bound to an equipped
 * Roland's .38 Special. Persistent state distinguishes a real equip
 * transition from capability loading, login, dimension synchronization, and
 * player cloning. Mirrors {@link WendysAmuletService} exactly, targeting the
 * {@link CurioSlotIds#HANDS} slot instead of {@link CurioSlotIds#CHARM}.
 */
public final class RolandsThirtyEightSpecialService {
    public static final UUID WEAKNESS_SLOT_MODIFIER_ID = UUID.fromString(
            "a3d17b2c-5e4f-4a1b-9c3d-8f2e6a7b1c40");
    public static final String WEAKNESS_SLOT_MODIFIER_NAME =
            "goetyarkham:rolands_38_special";

    private static final String STATE_KEY =
            "GoetyArkhamRolandsThirtyEightSpecial";
    private static final String ACTIVE_KEY = "Active";
    private static final String BINDING_KEY = "CoverUpBinding";
    private static final String SLOT_KEY = "CoverUpSlot";

    private RolandsThirtyEightSpecialService() {
    }

    /** Called only for an observed zero-to-one equipped transition. */
    public static boolean equipTransition(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> equipTransition(player, inventory))
                .orElse(false);
    }

    private static boolean equipTransition(
            ServerPlayer player, ICuriosItemHandler inventory) {
        CompoundTag state = getState(player, false);
        if (state != null && state.getBoolean(ACTIVE_KEY)) {
            reconcileActive(player, inventory, state);
            return false;
        }

        UUID binding = UUID.randomUUID();
        state = getState(player, true);
        state.putBoolean(ACTIVE_KEY, true);
        state.putUUID(BINDING_KEY, binding);
        ensureWeaknessSlot(inventory);

        ICurioStacksHandler weaknesses = inventory
                .getStacksHandler(CurioSlotIds.WEAKNESS)
                .orElse(null);
        if (weaknesses == null) {
            GoetyArkham.LOGGER.error(
                    "[RolandsThirtyEightSpecial] Missing weakness handler for player {}",
                    player.getUUID());
            clearState(player);
            removeWeaknessSlot(inventory);
            return false;
        }

        deleteOwnedWeaknesses(player, weaknesses, null);
        int slot = findNewWeaknessSlot(weaknesses);
        if (slot < 0) {
            GoetyArkham.LOGGER.error(
                    "[RolandsThirtyEightSpecial] No empty weakness slot after applying modifier for player {}",
                    player.getUUID());
            clearState(player);
            removeWeaknessSlot(inventory);
            return false;
        }

        ItemStack weakness = CoverUpItem.createBound(player.getUUID(), binding);
        weaknesses.getStacks().setStackInSlot(slot, weakness);
        state.putInt(SLOT_KEY, slot);
        return true;
    }

    /**
     * Called inside CurioChangeEvent, before any slot modifier is removed.
     * The bound weakness is erased by direct handler write before the slot
     * shrinks.
     */
    public static boolean unequipTransition(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> deactivate(player, inventory))
                .orElseGet(() -> {
                    clearState(player);
                    return false;
                });
    }

    /** Login/clone/dimension/sync-safe repair that never duplicates state. */
    public static void reconcile(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            boolean wearing = isWearing(player, inventory);
            CompoundTag state = getState(player, false);
            boolean active = state != null && state.getBoolean(ACTIVE_KEY);
            if (!wearing) {
                if (active || hasWeaknessSlotModifier(inventory)) {
                    deactivate(player, inventory);
                }
                return;
            }

            if (!active) {
                // The stack was already equipped when persistent data loaded.
                // Establish ownership without replaying the equip effect.
                state = getState(player, true);
                state.putBoolean(ACTIVE_KEY, true);
                state.putUUID(BINDING_KEY, UUID.randomUUID());
            }
            reconcileActive(player, inventory, state);
        });
    }

    public static boolean isWearing(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> isWearing(player, inventory))
                .orElse(false);
    }

    public static int equippedCount(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(RolandsThirtyEightSpecialService::equippedCount)
                .orElse(0);
    }

    public static boolean isActive(ServerPlayer player) {
        CompoundTag state = getState(player, false);
        return state != null && state.getBoolean(ACTIVE_KEY);
    }

    public static void copyPersistentState(Player oldPlayer, Player newPlayer) {
        CompoundTag oldRoot = oldPlayer.getPersistentData().getCompound(
                Player.PERSISTED_NBT_TAG);
        if (!oldRoot.contains(STATE_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag newRoot = persistedRoot(newPlayer);
        newRoot.put(STATE_KEY, oldRoot.getCompound(STATE_KEY).copy());
    }

    private static void reconcileActive(
            ServerPlayer player,
            ICuriosItemHandler inventory,
            CompoundTag state) {
        ensureWeaknessSlot(inventory);
        ICurioStacksHandler weaknesses = inventory
                .getStacksHandler(CurioSlotIds.WEAKNESS)
                .orElse(null);
        if (weaknesses == null) {
            return;
        }

        UUID binding = state.hasUUID(BINDING_KEY)
                ? state.getUUID(BINDING_KEY)
                : UUID.randomUUID();
        state.putUUID(BINDING_KEY, binding);

        int keptSlot = deleteOwnedWeaknesses(player, weaknesses, binding);
        if (keptSlot < 0) {
            keptSlot = findNewWeaknessSlot(weaknesses);
            if (keptSlot >= 0) {
                weaknesses.getStacks().setStackInSlot(
                        keptSlot,
                        CoverUpItem.createBound(player.getUUID(), binding));
            }
        }
        if (keptSlot >= 0) {
            state.putInt(SLOT_KEY, keptSlot);
        }
    }

    private static boolean deactivate(
            ServerPlayer player, ICuriosItemHandler inventory) {
        ICurioStacksHandler weaknesses = inventory
                .getStacksHandler(CurioSlotIds.WEAKNESS)
                .orElse(null);
        if (weaknesses != null) {
            // First erase only this player's generated weakness. If the exact
            // binding is absent, owner+source fallback safely handles old data.
            deleteOwnedWeaknesses(player, weaknesses, null);
        }
        // Only after the direct deletion may Curios shrink the handler.
        removeWeaknessSlot(inventory);
        clearState(player);
        return true;
    }

    /**
     * Keeps at most one exact binding and deletes every other Roland's .38
     * Special-owned weakness for this player. Passing null deletes all owned
     * weaknesses.
     */
    private static int deleteOwnedWeaknesses(
            ServerPlayer player,
            ICurioStacksHandler weaknesses,
            UUID bindingToKeep) {
        IDynamicStackHandler stacks = weaknesses.getStacks();
        int keptSlot = -1;
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (!CoverUpItem.isPistolBound(stack, player.getUUID())) {
                continue;
            }
            if (bindingToKeep != null
                    && keptSlot < 0
                    && CoverUpItem.isPistolBound(
                    stack, player.getUUID(), bindingToKeep)) {
                keptSlot = slot;
            } else {
                stacks.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        return keptSlot;
    }

    private static int findNewWeaknessSlot(ICurioStacksHandler weaknesses) {
        IDynamicStackHandler stacks = weaknesses.getStacks();
        // Slot modifiers append capacity, so search from the new end first.
        for (int slot = stacks.getSlots() - 1; slot >= 0; slot--) {
            if (stacks.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static void ensureWeaknessSlot(ICuriosItemHandler inventory) {
        ICurioStacksHandler handler = inventory
                .getStacksHandler(CurioSlotIds.WEAKNESS)
                .orElse(null);
        if (handler == null) {
            return;
        }
        AttributeModifier existing = handler.getModifiers()
                .get(WEAKNESS_SLOT_MODIFIER_ID);
        if (existing == null
                || existing.getAmount() != 1.0D
                || existing.getOperation()
                != AttributeModifier.Operation.ADDITION) {
            if (existing != null) {
                inventory.removeSlotModifier(
                        CurioSlotIds.WEAKNESS,
                        WEAKNESS_SLOT_MODIFIER_ID);
            }
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.WEAKNESS,
                    WEAKNESS_SLOT_MODIFIER_ID,
                    WEAKNESS_SLOT_MODIFIER_NAME,
                    1.0D,
                    AttributeModifier.Operation.ADDITION);
        }
        // getSlots applies a pending Curios resize synchronously.
        handler.getSlots();
    }

    private static void removeWeaknessSlot(ICuriosItemHandler inventory) {
        if (!hasWeaknessSlotModifier(inventory)) {
            return;
        }
        inventory.removeSlotModifier(
                CurioSlotIds.WEAKNESS,
                WEAKNESS_SLOT_MODIFIER_ID);
        inventory.getStacksHandler(CurioSlotIds.WEAKNESS)
                .ifPresent(ICurioStacksHandler::getSlots);
    }

    private static boolean hasWeaknessSlotModifier(
            ICuriosItemHandler inventory) {
        return inventory.getStacksHandler(CurioSlotIds.WEAKNESS)
                .map(handler -> handler.getModifiers().containsKey(
                        WEAKNESS_SLOT_MODIFIER_ID))
                .orElse(false);
    }

    private static boolean isWearing(
            ServerPlayer player, ICuriosItemHandler inventory) {
        return equippedCount(inventory) > 0;
    }

    private static int equippedCount(ICuriosItemHandler inventory) {
        return inventory.getStacksHandler(CurioSlotIds.HANDS)
                .map(handler -> {
                    int count = 0;
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        if (stacks.getStackInSlot(slot).is(
                                ModItems.ROLANDS_38_SPECIAL.get())) {
                            count++;
                        }
                    }
                    return count;
                })
                .orElse(0);
    }

    private static CompoundTag getState(Player player, boolean create) {
        CompoundTag root = persistedRoot(player);
        if (!root.contains(STATE_KEY, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            root.put(STATE_KEY, new CompoundTag());
        }
        return root.getCompound(STATE_KEY);
    }

    private static CompoundTag persistedRoot(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void clearState(Player player) {
        persistedRoot(player).remove(STATE_KEY);
    }
}
