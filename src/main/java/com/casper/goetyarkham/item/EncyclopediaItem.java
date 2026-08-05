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
 * A Curio equippable in either the {@code hands} or {@code book} slot.
 * While worn, grants 1 additional {@link CurioSlotIds#ENCYCLOPEDIA_SKILL}
 * Curios slot via {@link EncyclopediaService}. This item never inspects the
 * contents of that slot itself - the {@code +2} skill bonus for whatever is
 * placed in it is already handled by the slot's own existing content policy
 * and stat contribution (see {@link
 * com.casper.goetyarkham.curios.EncyclopediaSkillContentPolicy} and {@link
 * com.casper.goetyarkham.stats.EncyclopediaSkillStatContribution}).
 */
public final class EncyclopediaItem extends Item implements ICurioItem {
    public EncyclopediaItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return isSupportedSlot(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    private static boolean isSupportedSlot(String slotId) {
        return CurioSlotIds.HANDS.equals(slotId) || CurioSlotIds.BOOK.equals(slotId);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CurioTooltipHelper.appendSlot(tooltip, "tooltip.goetyarkham.encyclopedia.slot");
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                Component.translatable("tooltip.goetyarkham.encyclopedia.skill_slot"),
                Component.translatable("tooltip.goetyarkham.encyclopedia.skill_bonus"));
        if (ShiftTooltipHelper.isShiftDown()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.encyclopedia.accepted_heading")
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.encyclopedia.accepted_items")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.encyclopedia.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
