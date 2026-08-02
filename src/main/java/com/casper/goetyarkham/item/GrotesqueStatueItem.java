package com.casper.goetyarkham.item;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/** A necklace-only Curio backed by Goety's standard per-stack soul NBT. */
public final class GrotesqueStatueItem extends Item implements ITotem, ICurioItem {
    public static final int MAX_SOULS = 5_000;
    public static final int TREACHERY_COST = 1_000;

    public GrotesqueStatueItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public int getMaxSouls() {
        return MAX_SOULS;
    }

    @Override
    public void setTagTick(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        int souls = Math.max(0, Math.min(MAX_SOULS, tag.getInt(ITotem.SOULS_AMOUNT)));
        tag.putInt(ITotem.SOULS_AMOUNT, souls);
        tag.putInt(ITotem.MAX_SOUL_AMOUNT, MAX_SOULS);
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slot,
            boolean selected) {
        if (!level.isClientSide) {
            setTagTick(stack);
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!slotContext.entity().level().isClientSide) {
            setTagTick(stack);
        }
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.NECKLACE.equals(slotContext.identifier());
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
        int souls = Math.max(0, Math.min(MAX_SOULS, ITotem.currentSouls(stack)));
        tooltip.add(Component.translatable(
                "tooltip.goetyarkham.grotesque_statue.souls", souls, MAX_SOULS)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(
                "tooltip.goetyarkham.grotesque_statue.when_equipped")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.goetyarkham.grotesque_statue.effect")
                .withStyle(ChatFormatting.GRAY));
    }
}
