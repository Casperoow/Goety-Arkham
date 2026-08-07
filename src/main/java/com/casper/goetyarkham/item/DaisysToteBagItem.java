package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.EquipmentStatModifier;
import com.casper.goetyarkham.stats.StatType;
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
 * A {@link CurioSlotIds#BELT} Curio. While worn, grants 2 additional {@link
 * CurioSlotIds#BOOK} Curios slots (see {@link DaisysToteBagService}) and +1
 * Intellect for every Curio currently equipped anywhere that carries the
 * shared {@code curios:book} item tag - recomputed live off actual equipped
 * contents each time {@link com.casper.goetyarkham.stats.EquipmentStatsService}
 * refreshes, never accumulated. Its signature weakness, The Necronomicon
 * (John Dee), is auto-equipped and locked by {@link DaisysToteBagService}.
 */
public final class DaisysToteBagItem extends Item implements
        ICurioItem, EquipmentStatModifier {
    public DaisysToteBagItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.BELT.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public int getEquipmentStatModifier(
            StatType stat, SlotContext slotContext, ItemStack stack) {
        if (!CurioSlotIds.BELT.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || stat != StatType.INTELLECT) {
            return 0;
        }
        return DaisysToteBagService.countEquippedBooks(slotContext.entity());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                Component.translatable(
                        "tooltip.goetyarkham.daisys_tote_bag.book_slot"),
                Component.translatable(
                        "tooltip.goetyarkham.daisys_tote_bag.book_bonus")
        );
        SignatureWeaknessTooltipHelper.append(
                tooltip, "item.goetyarkham.the_necronomicon_john_dee");
    }
}
