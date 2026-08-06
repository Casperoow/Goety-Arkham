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
 * While worn, ensures the player has at least 1 {@link
 * CurioSlotIds#RESOURCE} Curios slot via {@link EncyclopediaService} /
 * {@link EncyclopediaBonusProvider}. Which items may occupy that slot is
 * decided entirely by Curios' own native item tag (see {@code
 * data/curios/tags/items/resource.json}) - exactly like the {@code focus}
 * slot accepts foci via {@code data/curios/tags/items/focus.json} - and the
 * {@code +2} skill bonus for whatever is placed in it is scored by this
 * item's {@link EncyclopediaBonusProvider}; this item never writes to that
 * slot itself. Its Shift tooltip does read the slot's current contents
 * client-side (see {@link EncyclopediaService#currentClientBonus()} /
 * {@link EncyclopediaBonusTooltipHelper}), purely to display the same
 * numbers, never to change them.
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
                Component.translatable("tooltip.goetyarkham.encyclopedia.resource_slot"),
                Component.translatable("tooltip.goetyarkham.encyclopedia.resource_bonus"));
        if (ShiftTooltipHelper.isShiftDown()) {
            EncyclopediaBonusTooltipHelper.append(tooltip, EncyclopediaService.currentClientBonus());
        } else {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.encyclopedia.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
