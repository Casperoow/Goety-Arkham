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
 * A charm Curio that grants +1 Strength, +1 Agility, and +2 Willpower while
 * functionally worn.
 */
public final class PoliceBadgeItem extends Item
        implements ICurioItem, EquipmentStatModifier {
    public static final int STRENGTH_BONUS = 1;
    public static final int AGILITY_BONUS = 1;
    public static final int WILLPOWER_BONUS = 2;

    public PoliceBadgeItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.CHARM.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public int getEquipmentStatModifier(
            StatType stat, SlotContext slotContext, ItemStack stack) {
        if (!CurioSlotIds.CHARM.equals(slotContext.identifier())
                || slotContext.cosmetic()) {
            return 0;
        }
        return switch (stat) {
            case STRENGTH -> STRENGTH_BONUS;
            case AGILITY -> AGILITY_BONUS;
            case WILLPOWER -> WILLPOWER_BONUS;
            case INTELLECT -> 0;
        };
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
                CurioTooltipHelper.attributeBonus(
                        STRENGTH_BONUS, "attribute.name.goetyarkham.strength"),
                CurioTooltipHelper.attributeBonus(
                        AGILITY_BONUS, "attribute.name.goetyarkham.agility"),
                CurioTooltipHelper.attributeBonus(
                        WILLPOWER_BONUS, "attribute.name.goetyarkham.willpower")
        );
    }
}
