package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.weakness.ILockedWeakness;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/** A locked weakness created and owned by Wendy's Amulet. */
public final class AbandonedAndAloneItem extends Item implements
        ICurioItem, ILockedWeakness {
    private static final String SOURCE_KEY =
            "GoetyArkhamAbandonedAndAloneSource";
    private static final String OWNER_KEY =
            "GoetyArkhamAbandonedAndAloneOwner";
    private static final String BINDING_KEY =
            "GoetyArkhamAbandonedAndAloneBinding";
    private static final String AMULET_SOURCE = "wendys_amulet";

    public AbandonedAndAloneItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.WEAKNESS.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return false;
    }

    @Override
    public ICurio.DropRule getDropRule(
            SlotContext slotContext,
            DamageSource source,
            int lootingLevel,
            boolean recentlyHit,
            ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (ShiftTooltipHelper.isShiftDown()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.abandoned_and_alone.effect",
                            LonelinessTooltipHelper.currentLoneliness())
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable(
                            SignatureWeaknessTooltipHelper.HOLD_SHIFT_TRANSLATION_KEY)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static ItemStack createBound(UUID owner, UUID binding) {
        ItemStack stack = new ItemStack(ModItems.ABANDONED_AND_ALONE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(SOURCE_KEY, AMULET_SOURCE);
        tag.putUUID(OWNER_KEY, owner);
        tag.putUUID(BINDING_KEY, binding);
        return stack;
    }

    public static boolean isAmuletBound(ItemStack stack, UUID owner) {
        if (!stack.is(ModItems.ABANDONED_AND_ALONE.get()) || stack.getTag() == null) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return AMULET_SOURCE.equals(tag.getString(SOURCE_KEY))
                && tag.hasUUID(OWNER_KEY)
                && owner.equals(tag.getUUID(OWNER_KEY));
    }

    public static boolean isAmuletBound(
            ItemStack stack, UUID owner, UUID binding) {
        return isAmuletBound(stack, owner)
                && stack.getTag() != null
                && stack.getTag().hasUUID(BINDING_KEY)
                && binding.equals(stack.getTag().getUUID(BINDING_KEY));
    }
}
