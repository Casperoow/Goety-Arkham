package com.casper.goetyarkham.curios;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import com.Polarice3.Goety.init.ModSounds;
import com.Polarice3.Goety.utils.WandUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Sole access point for the Curios {@link CurioSlotIds#FOCUS} slot.
 *
 * <p>The live {@link ICurioStacksHandler} is the only data source: nothing
 * here copies slot contents into a side capability or scans it on a fixed
 * tick cadence. Only the functional {@link ICurioStacksHandler#getStacks()}
 * handler is read/written; cosmetic stacks are never touched.</p>
 */
public final class FocusCurioService {
    private static final Predicate<FocusSlotExchange.Entry> FOCUS_OR_EMPTY =
            entry -> entry.empty() || (entry.payload() instanceof ItemStack stack && isFocus(stack));

    private FocusCurioService() {
    }

    public static Optional<ICurioStacksHandler> getStacksHandler(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.FOCUS));
    }

    public static int getSlotCount(Player player) {
        return getStacksHandler(player)
                .map(handler -> handler.getStacks().getSlots())
                .orElse(0);
    }

    public static boolean hasAnyFocus(Player player) {
        return getStacksHandler(player)
                .map(handler -> hasAnyFocus(handler.getStacks()))
                .orElse(false);
    }

    public static boolean hasEmptySlot(Player player) {
        return getStacksHandler(player)
                .map(handler -> findFirstEmptySlot(handler.getStacks()).isPresent())
                .orElse(false);
    }

    public static ItemStack getFocusAt(Player player, int index) {
        return getStacksHandler(player)
                .map(ICurioStacksHandler::getStacks)
                .filter(stacks -> index >= 0 && index < stacks.getSlots())
                .map(stacks -> stacks.getStackInSlot(index))
                .orElse(ItemStack.EMPTY);
    }

    public static OptionalInt findFirstEmptySlot(Player player) {
        return getStacksHandler(player)
                .map(handler -> findFirstEmptySlot(handler.getStacks()))
                .orElse(OptionalInt.empty());
    }

    public static boolean isFocus(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IFocus;
    }

    /**
     * Swaps the wand's current focus with the focus in {@code slotIndex}.
     * Validates the sender, the wand, and the slot index before touching
     * either handler; extraction, insertion, and the slot write only happen
     * once every check has passed, so a rejected request never mutates
     * anything. Plays {@code ModSounds.FOCUS_PICK} only on success.
     */
    public static boolean swapWandFocus(ServerPlayer player, int slotIndex) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        ItemStack wand = WandUtil.findWand(player);
        if (wand.isEmpty()) {
            return false;
        }
        SoulUsingItemHandler wandHandler = getWandFocusHandler(wand);
        if (wandHandler == null) {
            return false;
        }
        Optional<ICurioStacksHandler> handlerOpt = getStacksHandler(player);
        if (handlerOpt.isEmpty()) {
            return false;
        }
        IDynamicStackHandler slots = handlerOpt.get().getStacks();

        List<FocusSlotExchange.Entry> entries = toEntries(slots);
        FocusSlotExchange.Entry wandEntry = toEntry(wandHandler.getSlot());
        FocusSlotExchange.Result result =
                FocusSlotExchange.swap(entries, slotIndex, wandEntry, FOCUS_OR_EMPTY);
        if (!result.success()) {
            return false;
        }

        ItemStack newSlotStack = toStack(result.slots().get(slotIndex));
        ItemStack newWandStack = toStack(result.wandFocus());
        slots.setStackInSlot(slotIndex, newSlotStack);
        wandHandler.extractItem();
        if (!newWandStack.isEmpty()) {
            wandHandler.insertItem(newWandStack);
        }
        playFocusPickSound(player);
        return true;
    }

    /**
     * Stores the wand's current focus into the lowest-numbered empty focus
     * slot. Rejects (no-op, no sound) when the wand has no focus or every
     * slot is occupied.
     */
    public static boolean storeWandFocus(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        ItemStack wand = WandUtil.findWand(player);
        if (wand.isEmpty()) {
            return false;
        }
        SoulUsingItemHandler wandHandler = getWandFocusHandler(wand);
        if (wandHandler == null) {
            return false;
        }
        Optional<ICurioStacksHandler> handlerOpt = getStacksHandler(player);
        if (handlerOpt.isEmpty()) {
            return false;
        }
        IDynamicStackHandler slots = handlerOpt.get().getStacks();

        List<FocusSlotExchange.Entry> entries = toEntries(slots);
        FocusSlotExchange.Entry wandEntry = toEntry(wandHandler.getSlot());
        FocusSlotExchange.Result result = FocusSlotExchange.store(entries, wandEntry, FOCUS_OR_EMPTY);
        if (!result.success()) {
            return false;
        }

        ItemStack storedStack = toStack(result.slots().get(result.slotIndex()));
        slots.setStackInSlot(result.slotIndex(), storedStack);
        wandHandler.extractItem();
        playFocusPickSound(player);
        return true;
    }

    private static SoulUsingItemHandler getWandFocusHandler(ItemStack wand) {
        return wand.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .resolve()
                .filter(SoulUsingItemHandler.class::isInstance)
                .map(SoulUsingItemHandler.class::cast)
                .orElse(null);
    }

    private static void playFocusPickSound(ServerPlayer player) {
        player.playNotifySound(ModSounds.FOCUS_PICK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static boolean hasAnyFocus(IDynamicStackHandler stacks) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (isFocus(stacks.getStackInSlot(i))) {
                return true;
            }
        }
        return false;
    }

    private static OptionalInt findFirstEmptySlot(IDynamicStackHandler stacks) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).isEmpty()) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private static List<FocusSlotExchange.Entry> toEntries(IDynamicStackHandler stacks) {
        List<FocusSlotExchange.Entry> entries = new ArrayList<>(stacks.getSlots());
        for (int i = 0; i < stacks.getSlots(); i++) {
            entries.add(toEntry(stacks.getStackInSlot(i)));
        }
        return entries;
    }

    private static FocusSlotExchange.Entry toEntry(ItemStack stack) {
        return stack.isEmpty() ? FocusSlotExchange.Entry.EMPTY : FocusSlotExchange.Entry.of(stack);
    }

    private static ItemStack toStack(FocusSlotExchange.Entry entry) {
        if (entry.empty() || !(entry.payload() instanceof ItemStack stack)) {
            return ItemStack.EMPTY;
        }
        return stack;
    }
}
