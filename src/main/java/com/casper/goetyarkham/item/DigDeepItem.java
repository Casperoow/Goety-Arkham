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
 * A Curio equippable in {@link CurioSlotIds#ASSET}. While worn, ensures the
 * player has at least 3 {@link CurioSlotIds#RESOURCE} Curios slots via
 * {@link DigDeepService} / {@link DigDeepBonusProvider} - mirrors {@link
 * PhysicalTrainingItem} exactly, just with a different recognized-item set
 * (see {@link DigDeepBonusProvider}). Which items may occupy that slot is
 * decided entirely by Curios' own native item tag (see {@code
 * data/curios/tags/items/resource.json}); this item never writes to that
 * slot itself.
 */
public final class DigDeepItem extends Item implements ICurioItem {
    public DigDeepItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.ASSET.equals(slotContext.identifier())
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
        CurioTooltipHelper.appendSlot(tooltip, "tooltip.goetyarkham.dig_deep.slot");
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                Component.translatable("tooltip.goetyarkham.dig_deep.resource_bonus"));
        if (ShiftTooltipHelper.isShiftDown()) {
            ResourceSlotBonusTooltipHelper.append(
                    tooltip,
                    DigDeepService.currentClientBonus(),
                    "tooltip.goetyarkham.dig_deep.current_bonus_heading",
                    "tooltip.goetyarkham.dig_deep.when_equipped_heading",
                    "tooltip.goetyarkham.dig_deep.none");
        } else {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.dig_deep.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
