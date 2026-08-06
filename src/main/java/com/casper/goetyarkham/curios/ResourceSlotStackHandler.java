package com.casper.goetyarkham.curios;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.common.inventory.DynamicStackHandler;

import java.util.function.Function;

/**
 * Every {@link CurioSlotIds#RESOURCE} slot's own {@code
 * IDynamicStackHandler}, capped to holding exactly one item regardless of
 * that item's own stack size (Iron Ingot, Book, etc. all normally stack to
 * 64). {@link #getSlotLimit} is the single source of this cap: it is what
 * every transfer path Forge/Curios already funnels through - {@code
 * ItemStackHandler#insertItem}'s {@code getStackLimit} check, and {@code
 * SlotItemHandler#getMaxStackSize(ItemStack)}, which vanilla's own
 * mouse-pickup-and-place and shift-click quick-move logic both consult -
 * uses to decide how many units of a stack actually get taken. That means
 * a player holding 64 Iron Ingots and clicking (or shift-clicking, or
 * right-click-auto-equipping) into an empty slot here has exactly 1 unit
 * split off structurally, without this mod ever touching the source stack
 * itself. See {@link com.casper.goetyarkham.mixin.ResourceSlotHandlerMixin}
 * for how a player's {@code CurioStacksHandler} is made to construct this
 * subclass instead of a plain {@code DynamicStackHandler} for this one
 * slot identifier.
 */
public final class ResourceSlotStackHandler extends DynamicStackHandler {
    public ResourceSlotStackHandler(int size, Function<Integer, SlotContext> ctxBuilder) {
        super(size, ctxBuilder);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
