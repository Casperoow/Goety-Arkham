package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
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
 * A {@link CurioSlotIds#ASSET} Curio. While functionally worn and not on
 * cooldown, grants the wearer True Invisibility; see {@link
 * OnTheLamService} for the full activation/break/cooldown state machine and
 * {@link OnTheLamEvents} for the damage and AI-targeting hooks that enforce
 * it. Its signature weakness, Hospital Debts, is auto-equipped and locked
 * by {@link OnTheLamService}.
 */
public final class OnTheLamItem extends Item implements ICurioItem {
    public OnTheLamItem() {
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
        CurioTooltipHelper.appendSlot(tooltip, "tooltip.goetyarkham.on_the_lam.slot");
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                Component.translatable("tooltip.goetyarkham.on_the_lam.effect"));
        SignatureWeaknessTooltipHelper.append(
                tooltip, "item.goetyarkham.hospital_debts");
    }
}
