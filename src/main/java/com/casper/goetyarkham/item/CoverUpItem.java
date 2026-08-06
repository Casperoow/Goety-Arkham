package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.weakness.ILockedWeakness;
import com.casper.goetyarkham.stats.EquipmentStatModifier;
import com.casper.goetyarkham.stats.StatType;
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

/** A locked weakness created and owned by Roland's .38 Special. */
public final class CoverUpItem extends Item implements
        ICurioItem, EquipmentStatModifier, ILockedWeakness {
    public static final int INTELLECT_PENALTY = -6;

    private static final String SOURCE_KEY = "GoetyArkhamCoverUpSource";
    private static final String OWNER_KEY = "GoetyArkhamCoverUpOwner";
    private static final String BINDING_KEY = "GoetyArkhamCoverUpBinding";
    private static final String PISTOL_SOURCE = "rolands_38_special";

    public CoverUpItem() {
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
    public int getEquipmentStatModifier(
            StatType stat, SlotContext slotContext, ItemStack stack) {
        if (!CurioSlotIds.WEAKNESS.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || stat != StatType.INTELLECT) {
            return 0;
        }
        return INTELLECT_PENALTY;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
                        "tooltip.goetyarkham.cover_up.intellect")
                .withStyle(ChatFormatting.GRAY));
        if (ShiftTooltipHelper.isShiftDown()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.cover_up.effect")
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable(
                            SignatureWeaknessTooltipHelper.HOLD_SHIFT_TRANSLATION_KEY)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static ItemStack createBound(UUID owner, UUID binding) {
        ItemStack stack = new ItemStack(ModItems.COVER_UP.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(SOURCE_KEY, PISTOL_SOURCE);
        tag.putUUID(OWNER_KEY, owner);
        tag.putUUID(BINDING_KEY, binding);
        return stack;
    }

    public static boolean isPistolBound(ItemStack stack, UUID owner) {
        if (!stack.is(ModItems.COVER_UP.get()) || stack.getTag() == null) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return PISTOL_SOURCE.equals(tag.getString(SOURCE_KEY))
                && tag.hasUUID(OWNER_KEY)
                && owner.equals(tag.getUUID(OWNER_KEY));
    }

    public static boolean isPistolBound(
            ItemStack stack, UUID owner, UUID binding) {
        return isPistolBound(stack, owner)
                && stack.getTag() != null
                && stack.getTag().hasUUID(BINDING_KEY)
                && binding.equals(stack.getTag().getUUID(BINDING_KEY));
    }
}
