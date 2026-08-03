package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.weakness.WeaknessActivationCause;
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
 * Owns the single +1 weakness slot and the Dark Memory bound to an equipped
 * Heirloom. Persistent state distinguishes a real equip transition from
 * capability loading, login, dimension synchronization, and player cloning.
 */
public final class HeirloomOfHyperboreaService {
    public static final UUID WEAKNESS_SLOT_MODIFIER_ID = UUID.fromString(
            "8b87b113-8ad9-4df5-8c79-b1afe9dba2da");
    public static final String WEAKNESS_SLOT_MODIFIER_NAME =
            "goetyarkham:heirloom_of_hyperborea";

    private static final String STATE_KEY =
            "GoetyArkhamHeirloomOfHyperborea";
    private static final String ACTIVE_KEY = "Active";
    private static final String BINDING_KEY = "DarkMemoryBinding";
    private static final String SLOT_KEY = "DarkMemorySlot";

    private HeirloomOfHyperboreaService() {
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
                    "[Hyperborea] Missing weakness handler for player {}",
                    player.getUUID());
            clearState(player);
            removeWeaknessSlot(inventory);
            return false;
        }

        deleteOwnedMemories(player, weaknesses, null);
        int slot = findNewWeaknessSlot(weaknesses);
        if (slot < 0) {
            GoetyArkham.LOGGER.error(
                    "[Hyperborea] No empty weakness slot after applying modifier for player {}",
                    player.getUUID());
            clearState(player);
            removeWeaknessSlot(inventory);
            return false;
        }

        ItemStack memory = DarkMemoryItem.createBound(
                player.getUUID(), binding);
        weaknesses.getStacks().setStackInSlot(slot, memory);
        state.putInt(SLOT_KEY, slot);

        // This is the only activation call on the equip transition. Reconcile
        // paths may recreate a missing bound stack but never invoke it.
        ModItems.DARK_MEMORY.get().activateWeakness(
                player,
                memory,
                slot,
                WeaknessActivationCause.HEIRLOOM_EQUIPPED);
        return true;
    }

    /**
     * Called inside CurioChangeEvent, before any slot modifier is removed.
     * The bound memory is erased by direct handler write before the slot shrinks.
     */
    public static boolean unequipTransition(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> deactivate(player, inventory))
                .orElseGet(() -> {
                    clearState(player);
                    return false;
                });
    }

    /** Login/clone/dimension/sync-safe repair that never activates a weakness. */
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
                .map(HeirloomOfHyperboreaService::equippedCount)
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

        int keptSlot = deleteOwnedMemories(player, weaknesses, binding);
        if (keptSlot < 0) {
            keptSlot = findNewWeaknessSlot(weaknesses);
            if (keptSlot >= 0) {
                weaknesses.getStacks().setStackInSlot(
                        keptSlot,
                        DarkMemoryItem.createBound(player.getUUID(), binding));
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
            // First erase only this player's generated memory. If the exact
            // binding is absent, owner+source fallback safely handles old data.
            deleteOwnedMemories(player, weaknesses, null);
        }
        // Only after the direct deletion may Curios shrink the handler.
        removeWeaknessSlot(inventory);
        clearState(player);
        return true;
    }

    /**
     * Keeps at most one exact binding and deletes every other Heirloom-owned
     * memory for this player. Passing null deletes all owned memories.
     */
    private static int deleteOwnedMemories(
            ServerPlayer player,
            ICurioStacksHandler weaknesses,
            UUID bindingToKeep) {
        IDynamicStackHandler stacks = weaknesses.getStacks();
        int keptSlot = -1;
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (!DarkMemoryItem.isHeirloomBound(stack, player.getUUID())) {
                continue;
            }
            if (bindingToKeep != null
                    && keptSlot < 0
                    && DarkMemoryItem.isHeirloomBound(
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
        return inventory.getStacksHandler(CurioSlotIds.NECKLACE)
                .map(handler -> {
                    int count = 0;
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        if (stacks.getStackInSlot(slot).is(
                                ModItems.HEIRLOOM_OF_HYPERBOREA.get())) {
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
