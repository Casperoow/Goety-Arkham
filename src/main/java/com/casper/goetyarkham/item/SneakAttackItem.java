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
 * A {@link CurioSlotIds#ASSET} Curio. While functionally worn, doubles all
 * damage the wearer deals to a target that is at full health immediately
 * before that hit lands, regardless of whether the hit was melee, a
 * projectile, a TACZ bullet, or Goety spell/summon damage - see {@link
 * SneakAttackService} (attacker resolution + full-health eligibility) and
 * {@link SneakAttackEvents} (the single {@code LivingHurtEvent} hook that
 * actually applies the multiplier).
 */
public final class SneakAttackItem extends Item implements ICurioItem {
    public SneakAttackItem() {
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
        CurioTooltipHelper.appendSlot(tooltip, "tooltip.goetyarkham.sneak_attack.slot");
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                Component.translatable("tooltip.goetyarkham.sneak_attack.effect"));
    }
}
