package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.effect.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.UUID;

/**
 * Owns two independent concerns for On the Lam:
 *
 * <ol>
 *   <li>The single +1 weakness slot and the Hospital Debts bound to an
 *   equipped On the Lam - persistent state distinguishes a real equip
 *   transition from capability loading, login, dimension synchronization,
 *   and player cloning. Mirrors {@link RolandsThirtyEightSpecialService}
 *   exactly, targeting {@link CurioSlotIds#ASSET} instead of {@link
 *   CurioSlotIds#HANDS}.</li>
 *   <li>The True Invisibility state machine: {@link #tickInvisibility}
 *   is the single authority, called unconditionally for every {@link
 *   ServerPlayer} each tick (see {@code CuriosForgeEvents#livingTick}) so
 *   that both "still equipped, cooldown just ended" and "no longer
 *   equipped" transitions are caught without depending on any other event
 *   firing. {@link OnTheLamEvents} calls {@link
 *   #breakTrueInvisibility(ServerPlayer)} directly from the damage hooks
 *   that must react immediately rather than waiting up to one tick.</li>
 * </ol>
 */
public final class OnTheLamService {
    public static final UUID WEAKNESS_SLOT_MODIFIER_ID = UUID.fromString(
            "b6e2f4a1-7c3d-4e9a-8f1b-2d5c6e7a9b3f");
    public static final String WEAKNESS_SLOT_MODIFIER_NAME =
            "goetyarkham:on_the_lam";

    /** 10 seconds. */
    public static final int COOLDOWN_TICKS = 200;

    /** Radius used to find mobs already targeting the wearer at the instant True Invisibility activates. */
    private static final double TARGET_CLEAR_RADIUS = 64.0D;

    private static final String STATE_KEY = "GoetyArkhamOnTheLam";
    private static final String ACTIVE_KEY = "Active";
    private static final String BINDING_KEY = "HospitalDebtsBinding";
    private static final String SLOT_KEY = "HospitalDebtsSlot";

    private OnTheLamService() {
    }

    // ------------------------------------------------------------------
    // Weakness slot / Hospital Debts lifecycle.
    // ------------------------------------------------------------------

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
                    "[OnTheLam] Missing weakness handler for player {}",
                    player.getUUID());
            clearState(player);
            removeWeaknessSlot(inventory);
            return false;
        }

        deleteOwnedWeaknesses(player, weaknesses, null);
        int slot = findNewWeaknessSlot(weaknesses);
        if (slot < 0) {
            GoetyArkham.LOGGER.error(
                    "[OnTheLam] No empty weakness slot after applying modifier for player {}",
                    player.getUUID());
            clearState(player);
            removeWeaknessSlot(inventory);
            return false;
        }

        ItemStack weakness = HospitalDebtsItem.createBound(player.getUUID(), binding);
        weaknesses.getStacks().setStackInSlot(slot, weakness);
        state.putInt(SLOT_KEY, slot);
        return true;
    }

    /**
     * Called inside CurioChangeEvent, before any slot modifier is removed.
     * The bound weakness is erased by direct handler write before the slot
     * shrinks. Also immediately clears True Invisibility - plain unequip
     * never starts the cooldown, unlike {@link
     * #breakTrueInvisibility(ServerPlayer)}.
     */
    public static boolean unequipTransition(ServerPlayer player) {
        deactivateTrueInvisibility(player);
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
                .map(OnTheLamService::equippedCount)
                .orElse(0);
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
                        HospitalDebtsItem.createBound(player.getUUID(), binding));
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
     * Keeps at most one exact binding and deletes every other On the
     * Lam-owned weakness for this player. Passing null deletes all owned
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
            if (!HospitalDebtsItem.isOnTheLamBound(stack, player.getUUID())) {
                continue;
            }
            if (bindingToKeep != null
                    && keptSlot < 0
                    && HospitalDebtsItem.isOnTheLamBound(
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
        return inventory.getStacksHandler(CurioSlotIds.ASSET)
                .map(handler -> {
                    int count = 0;
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        if (stacks.getStackInSlot(slot).is(
                                ModItems.ON_THE_LAM.get())) {
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

    // ------------------------------------------------------------------
    // True Invisibility state machine.
    // ------------------------------------------------------------------

    /**
     * The single per-tick authority: {@code equipped && !onCooldown} must
     * hold True Invisibility active, anything else must not. Called
     * unconditionally for every {@link ServerPlayer} each tick regardless of
     * whether any Curios change was observed, since a cooldown naturally
     * reaching zero fires no event of its own.
     */
    public static void tickInvisibility(ServerPlayer player) {
        boolean shouldBeActive = isWearing(player)
                && !player.getCooldowns().isOnCooldown(ModItems.ON_THE_LAM.get());
        boolean active = hasTrueInvisibility(player);
        if (shouldBeActive && !active) {
            activateTrueInvisibility(player);
        } else if (!shouldBeActive && active) {
            deactivateTrueInvisibility(player);
        }
    }

    public static boolean hasTrueInvisibility(ServerPlayer player) {
        return player.hasEffect(ModEffects.TRUE_INVISIBILITY.get());
    }

    /** Establishes True Invisibility and clears any hostile targets already locked onto the wearer. */
    public static void activateTrueInvisibility(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                MobEffectInstance.INFINITE_DURATION,
                0,
                true,
                false,
                true));
        player.addEffect(new MobEffectInstance(
                ModEffects.TRUE_INVISIBILITY.get(),
                MobEffectInstance.INFINITE_DURATION,
                0,
                true,
                false,
                false));
        clearHostileTargets(player);
    }

    /** Removes True Invisibility without side effects. Used for plain unequip and the tick safety net. */
    public static void deactivateTrueInvisibility(ServerPlayer player) {
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.TRUE_INVISIBILITY.get());
    }

    /**
     * Removes True Invisibility and starts On the Lam's cooldown. Called
     * directly (not merely left to {@link #tickInvisibility}) so dealing or
     * taking damage breaks stealth immediately rather than up to one tick
     * later. A no-op when the player is not currently True Invisible, so
     * damage taken while already on cooldown cannot refresh/extend it.
     */
    public static void breakTrueInvisibility(ServerPlayer player) {
        if (!hasTrueInvisibility(player)) {
            return;
        }
        deactivateTrueInvisibility(player);
        player.getCooldowns().addCooldown(ModItems.ON_THE_LAM.get(), COOLDOWN_TICKS);
    }

    /**
     * One-time scan (never a per-tick world scan) for mobs already targeting
     * {@code player} at the instant True Invisibility activates - vanilla AI
     * has no reverse index from a target back to the mobs targeting it, so
     * this is the only way to make already-acquired targets let go
     * immediately instead of merely blocking future acquisition (handled
     * separately by the {@code TargetingConditions} mixin).
     */
    private static void clearHostileTargets(ServerPlayer player) {
        player.serverLevel().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(TARGET_CLEAR_RADIUS),
                mob -> mob.getTarget() == player
        ).forEach(mob -> mob.setTarget(null));
    }
}
