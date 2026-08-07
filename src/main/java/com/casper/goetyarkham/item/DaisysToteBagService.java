package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.sanity.SanityChangeCause;
import com.casper.goetyarkham.sanity.SanityService;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns Daisy's Tote Bag's two independent, stateless-vs-stateful behaviors:
 *
 * <ul>
 *   <li>The {@code +2} {@link CurioSlotIds#BOOK} slot contribution (via
 *   {@link DynamicCurioSlotContributionService}, using its own fixed {@link
 *   #BOOK_SLOT_MODIFIER_ID}) - stateless and idempotent, recomputed purely
 *   from whether a Tote Bag is currently worn, exactly like {@link
 *   BookOfShadowsService}. Stacks additively with every other item's own
 *   independent book-slot modifier.</li>
 *   <li>The single {@code +1} weakness slot and its bound The Necronomicon
 *   (John Dee), and that Necronomicon's permanent maximum-Sanity loss,
 *   mirroring {@link RolandsThirtyEightSpecialService} / {@link
 *   WendysAmuletService} / {@link HeirloomOfHyperboreaService}'s
 *   active/binding/slot persistent state - plus one addition, {@code
 *   NecronomiconAppliedLoss}, the permanent loss this exact binding actually
 *   managed to apply (the shared permanent-loss pool caps at 9, so it can be
 *   less than {@link TheNecronomiconJohnDeeItem#SANITY_MAX_LOSS}). Unlike
 *   those simpler weaknesses, {@link #reconcileActive} does not
 *   unconditionally delete-and-recreate on every call: if a Tote Bag-owned
 *   Necronomicon is already present it is adopted in place (its own binding
 *   and applied-loss are read back into this state), so a stack that
 *   survived a login/clone/dimension-change is never destroyed and no
 *   second round of permanent loss is ever applied for the same binding.</li>
 * </ul>
 */
public final class DaisysToteBagService {
    public static final UUID WEAKNESS_SLOT_MODIFIER_ID = UUID.fromString(
            "6f1a2d3e-4b5c-4d6e-8f7a-9b0c1d2e3f4a");
    public static final String WEAKNESS_SLOT_MODIFIER_NAME =
            "goetyarkham:daisys_tote_bag_weakness";
    public static final UUID BOOK_SLOT_MODIFIER_ID = UUID.fromString(
            "7c2b3e4f-5d6a-4b7c-9d8e-0f1a2b3c4d5e");
    public static final String BOOK_SLOT_MODIFIER_NAME =
            "goetyarkham:daisys_tote_bag_book";
    private static final double BOOK_SLOT_AMOUNT = 2.0D;
    private static final List<String> WORN_SLOTS = List.of(CurioSlotIds.BELT);
    private static final TagKey<Item> BOOK_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("curios", "book"));

    private static final String STATE_KEY = "GoetyArkhamDaisysToteBag";
    private static final String ACTIVE_KEY = "Active";
    private static final String BINDING_KEY = "NecronomiconBinding";
    private static final String SLOT_KEY = "NecronomiconSlot";
    private static final String APPLIED_LOSS_KEY = "NecronomiconAppliedLoss";

    private DaisysToteBagService() {
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

        state = getState(player, true);
        state.putBoolean(ACTIVE_KEY, true);
        ensureWeaknessSlot(inventory);
        reconcileActive(player, inventory, state);
        return true;
    }

    /**
     * Called inside CurioChangeEvent, before any slot modifier is removed.
     * The bound Necronomicon is erased by direct handler write before the
     * weakness slot shrinks.
     */
    public static boolean unequipTransition(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> deactivate(player, inventory))
                .orElseGet(() -> {
                    clearState(player);
                    return false;
                });
    }

    /**
     * Login/clone/dimension/sync-safe repair for both the weakness/sanity
     * state and the (independent, stateless) book-slot contribution.
     */
    public static void reconcile(ServerPlayer player) {
        reconcileBookSlot(player);
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
            }
            reconcileActive(player, inventory, state);
        });
    }

    /**
     * Idempotently keeps the shared {@link CurioSlotIds#BOOK} slot's Tote
     * Bag-owned {@code +2} contribution in sync with whether a Tote Bag is
     * currently worn. Safe to call on every observed belt-slot change and on
     * every reconcile pass, exactly like {@link BookOfShadowsService#reconcile}.
     */
    public static void reconcileBookSlot(ServerPlayer player) {
        DynamicCurioSlotContributionService.reconcile(
                player,
                CurioSlotIds.BOOK,
                BOOK_SLOT_MODIFIER_ID,
                BOOK_SLOT_MODIFIER_NAME,
                ModItems.DAISYS_TOTE_BAG::get,
                WORN_SLOTS,
                BOOK_SLOT_AMOUNT);
    }

    public static boolean isWearing(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> isWearing(player, inventory))
                .orElse(false);
    }

    public static int equippedCount(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(DaisysToteBagService::equippedCount)
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

    /**
     * Counts every currently equipped Curio stack, in any Curios slot, that
     * carries the shared {@code curios:book} item tag - the same tag used to
     * identify the Book of Shadows, Medical Texts, the Old Book of Lore, and
     * the Encyclopedia. The Necronomicon (John Dee) also carries this tag
     * (see {@code data/curios/tags/items/book.json}), so it counts here too
     * while it sits in its bound weakness slot. Widened to {@link
     * LivingEntity} since this is a pure read of already-replicated Curios
     * contents, safe to call for the local client player as well.
     */
    public static int countEquippedBooks(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).resolve().map(inventory -> {
            int count = 0;
            for (ICurioStacksHandler handler : inventory.getCurios().values()) {
                IDynamicStackHandler stacks = handler.getStacks();
                for (int slot = 0; slot < stacks.getSlots(); slot++) {
                    ItemStack stack = stacks.getStackInSlot(slot);
                    if (!stack.isEmpty() && stack.is(BOOK_TAG)) {
                        count++;
                    }
                }
            }
            return count;
        }).orElse(0);
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

        List<Integer> owned = findOwnedSlots(player, weaknesses);
        if (!owned.isEmpty()) {
            // A Tote Bag-owned Necronomicon already exists (survived a
            // login/clone/dimension-change, or a duplicate transition event
            // fired while already active): adopt it in place rather than
            // deleting and recreating it, so its binding and already-applied
            // permanent loss are preserved exactly, with no second dose.
            int keepSlot = owned.get(0);
            ItemStack keepStack = weaknesses.getStacks().getStackInSlot(keepSlot);
            UUID binding = TheNecronomiconJohnDeeItem.getBinding(keepStack);
            state.putUUID(BINDING_KEY, binding != null ? binding : UUID.randomUUID());
            state.putInt(SLOT_KEY, keepSlot);
            state.putInt(APPLIED_LOSS_KEY,
                    TheNecronomiconJohnDeeItem.getAppliedPermanentLoss(keepStack));
            for (int i = 1; i < owned.size(); i++) {
                // Defensive de-duplication: never expected in normal play,
                // but if it ever happens, each extra copy's own contribution
                // is refunded individually before it is erased.
                int dupSlot = owned.get(i);
                ItemStack dup = weaknesses.getStacks().getStackInSlot(dupSlot);
                int dupApplied = TheNecronomiconJohnDeeItem.getAppliedPermanentLoss(dup);
                if (dupApplied > 0) {
                    SanityService.restorePermanentMaxLoss(
                            player, dupApplied, SanityChangeCause.NECRONOMICON);
                }
                weaknesses.getStacks().setStackInSlot(dupSlot, ItemStack.EMPTY);
            }
            return;
        }

        UUID binding = state.hasUUID(BINDING_KEY)
                ? state.getUUID(BINDING_KEY)
                : UUID.randomUUID();
        state.putUUID(BINDING_KEY, binding);

        int slot = findNewWeaknessSlot(weaknesses);
        if (slot < 0) {
            GoetyArkham.LOGGER.error(
                    "[DaisysToteBag] No empty weakness slot after applying modifier for player {}",
                    player.getUUID());
            return;
        }

        int appliedLoss;
        if (state.contains(APPLIED_LOSS_KEY, Tag.TAG_ANY_NUMERIC)) {
            // This exact binding already existed and had already applied
            // (some amount of) permanent loss - only its ItemStack
            // representation went missing. Restore that stack without
            // inflicting a second round of loss.
            appliedLoss = state.getInt(APPLIED_LOSS_KEY);
        } else {
            appliedLoss = SanityService.addPermanentMaxLoss(
                    player,
                    TheNecronomiconJohnDeeItem.SANITY_MAX_LOSS,
                    SanityChangeCause.NECRONOMICON);
            state.putInt(APPLIED_LOSS_KEY, appliedLoss);
        }

        ItemStack stack = TheNecronomiconJohnDeeItem.createBound(player.getUUID(), binding);
        TheNecronomiconJohnDeeItem.setAppliedPermanentLoss(stack, appliedLoss);
        weaknesses.getStacks().setStackInSlot(slot, stack);
        state.putInt(SLOT_KEY, slot);
    }

    private static boolean deactivate(
            ServerPlayer player, ICuriosItemHandler inventory) {
        ICurioStacksHandler weaknesses = inventory
                .getStacksHandler(CurioSlotIds.WEAKNESS)
                .orElse(null);
        if (weaknesses != null) {
            // Restore exactly what each owned stack itself contributed -
            // never a flat amount - before erasing it. Never touches any
            // other source's permanent loss.
            for (int slot : findOwnedSlots(player, weaknesses)) {
                ItemStack stack = weaknesses.getStacks().getStackInSlot(slot);
                int applied = TheNecronomiconJohnDeeItem.getAppliedPermanentLoss(stack);
                if (applied > 0) {
                    SanityService.restorePermanentMaxLoss(
                            player, applied, SanityChangeCause.NECRONOMICON);
                }
                weaknesses.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        // Only after the direct deletion may Curios shrink the handler.
        removeWeaknessSlot(inventory);
        clearState(player);
        return true;
    }

    private static List<Integer> findOwnedSlots(
            ServerPlayer player, ICurioStacksHandler weaknesses) {
        List<Integer> owned = new ArrayList<>();
        IDynamicStackHandler stacks = weaknesses.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (TheNecronomiconJohnDeeItem.isToteBagBound(
                    stacks.getStackInSlot(slot), player.getUUID())) {
                owned.add(slot);
            }
        }
        return owned;
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
        return inventory.getStacksHandler(CurioSlotIds.BELT)
                .map(handler -> {
                    int count = 0;
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        if (stacks.getStackInSlot(slot).is(
                                ModItems.DAISYS_TOTE_BAG.get())) {
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
