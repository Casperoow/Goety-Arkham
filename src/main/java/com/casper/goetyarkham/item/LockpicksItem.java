package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * A hands-slot Curio that adds the wearer's current Agility to their
 * Intellect while functionally worn (see {@link LockpicksIntellectBonusService}),
 * and consumes 1 of its 3 durability points whenever the wearer's Intellect
 * check succeeds by no more than 2 (see {@link LockpicksDurabilityService}).
 */
public final class LockpicksItem extends Item implements ICurioItem {
    public static final int MAX_DURABILITY = 3;

    public LockpicksItem() {
        super(new Item.Properties().stacksTo(1).durability(MAX_DURABILITY));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.HANDS.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CurioTooltipHelper.appendSlot(tooltip, "tooltip.goetyarkham.lockpicks.slot");
        CurioTooltipHelper.appendWhenWorn(
                tooltip, "tooltip.goetyarkham.lockpicks.effect");
        if (ShiftTooltipHelper.isShiftDown()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.lockpicks.durability_effect")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.lockpicks.durability",
                            stack.getMaxDamage() - stack.getDamageValue())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.lockpicks.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
