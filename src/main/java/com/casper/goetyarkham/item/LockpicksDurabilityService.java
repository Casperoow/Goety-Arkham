package com.casper.goetyarkham.item;

import com.casper.goetyarkham.chaosbag.ChaosBaseValueSource;
import com.casper.goetyarkham.chaosbag.ChaosCheckResult;
import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;

/**
 * Reacts to a fully-resolved chaos check on behalf of an equipped Lockpicks:
 * called once, from {@link com.casper.goetyarkham.chaosbag.ChaosCheckService
 * #resolveAndApply}, after every token draw, modifier, and override has
 * already been folded into {@link ChaosCheckResult#finalValue()} - so a
 * single check can never consume more than 1 durability point no matter how
 * many intermediate stages contributed to the result.
 */
public final class LockpicksDurabilityService {
    public static final int MAX_SUCCESS_MARGIN = 2;

    private LockpicksDurabilityService() {
    }

    public static void onCheckResolved(ServerPlayer player, ChaosCheckResult result) {
        if (result.baseValueSource() != ChaosBaseValueSource.INTELLECT
                || !result.success()) {
            return;
        }
        int margin = result.finalValue() - result.targetValue();
        if (margin < 0 || margin > MAX_SUCCESS_MARGIN) {
            return;
        }
        findEquipped(player).ifPresent(equipped -> equipped.stack().hurtAndBreak(
                1,
                player,
                broken -> CuriosApi.broadcastCurioBreakEvent(equipped.slotContext())));
    }

    private static Optional<Equipped> findEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.HANDS))
                .flatMap(handler -> findLockpicks(player, handler));
    }

    private static Optional<Equipped> findLockpicks(
            ServerPlayer player, ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (stack.is(ModItems.LOCKPICKS.get())) {
                return Optional.of(new Equipped(
                        stack,
                        new SlotContext(CurioSlotIds.HANDS, player, slot, false, true)));
            }
        }
        return Optional.empty();
    }

    private record Equipped(ItemStack stack, SlotContext slotContext) {
    }
}
