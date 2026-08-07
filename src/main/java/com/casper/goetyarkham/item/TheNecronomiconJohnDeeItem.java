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

/**
 * A locked weakness created and owned by Daisy's Tote Bag. Also carries the
 * {@code book} Curios tag (see {@code data/curios/tags/items/book.json}) so
 * it counts toward Daisy's Tote Bag's own "+1 Intellect per equipped book"
 * bonus while it sits in its bound {@link CurioSlotIds#WEAKNESS} slot - but
 * {@link #canEquip} only ever admits the weakness slot, so having that tag
 * never lets it be equipped into {@link CurioSlotIds#BOOK} itself.
 *
 * <p>Unlike {@link CoverUpItem} or {@link DarkMemoryItem}, this weakness's
 * effect is a permanent maximum-Sanity loss ({@link
 * com.casper.goetyarkham.sanity.SanityService#addPermanentMaxLoss}) applied
 * once by {@link DaisysToteBagService} when this exact bound stack is
 * created. Because the permanent loss pool is capped at 9, the amount this
 * specific stack actually managed to apply can be less than {@link
 * #SANITY_MAX_LOSS} (or zero) - that real, possibly-partial contribution is
 * tracked on this stack's own NBT via {@link #getAppliedPermanentLoss} /
 * {@link #setAppliedPermanentLoss} so that removing this exact stack later
 * restores exactly (and only) what it actually contributed, never a flat
 * {@link #SANITY_MAX_LOSS}.</p>
 */
public final class TheNecronomiconJohnDeeItem extends Item implements
        ICurioItem, ILockedWeakness {
    public static final int SANITY_MAX_LOSS = 3;

    private static final String SOURCE_KEY =
            "GoetyArkhamNecronomiconSource";
    private static final String OWNER_KEY =
            "GoetyArkhamNecronomiconOwner";
    private static final String BINDING_KEY =
            "GoetyArkhamNecronomiconBinding";
    private static final String APPLIED_LOSS_KEY =
            "GoetyArkhamNecronomiconAppliedLoss";
    private static final String TOTE_BAG_SOURCE = "daisys_tote_bag";

    public TheNecronomiconJohnDeeItem() {
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
        CurioTooltipHelper.appendSlot(
                tooltip, "tooltip.goetyarkham.the_necronomicon_john_dee.slot");
        if (ShiftTooltipHelper.isShiftDown()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.the_necronomicon_john_dee.effect")
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable(
                            SignatureWeaknessTooltipHelper.HOLD_SHIFT_TRANSLATION_KEY)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static ItemStack createBound(UUID owner, UUID binding) {
        ItemStack stack = new ItemStack(ModItems.THE_NECRONOMICON_JOHN_DEE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(SOURCE_KEY, TOTE_BAG_SOURCE);
        tag.putUUID(OWNER_KEY, owner);
        tag.putUUID(BINDING_KEY, binding);
        return stack;
    }

    public static boolean isToteBagBound(ItemStack stack, UUID owner) {
        if (!stack.is(ModItems.THE_NECRONOMICON_JOHN_DEE.get()) || stack.getTag() == null) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return TOTE_BAG_SOURCE.equals(tag.getString(SOURCE_KEY))
                && tag.hasUUID(OWNER_KEY)
                && owner.equals(tag.getUUID(OWNER_KEY));
    }

    public static boolean isToteBagBound(
            ItemStack stack, UUID owner, UUID binding) {
        return isToteBagBound(stack, owner)
                && stack.getTag() != null
                && stack.getTag().hasUUID(BINDING_KEY)
                && binding.equals(stack.getTag().getUUID(BINDING_KEY));
    }

    public static UUID getBinding(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().hasUUID(BINDING_KEY)
                ? stack.getTag().getUUID(BINDING_KEY)
                : null;
    }

    /** The permanent maximum-Sanity loss this exact bound stack actually applied, clamped to [0, {@link #SANITY_MAX_LOSS}]. */
    public static int getAppliedPermanentLoss(ItemStack stack) {
        if (!stack.hasTag()) {
            return 0;
        }
        return Math.max(0, Math.min(
                SANITY_MAX_LOSS, stack.getOrCreateTag().getInt(APPLIED_LOSS_KEY)));
    }

    public static void setAppliedPermanentLoss(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(
                APPLIED_LOSS_KEY, Math.max(0, Math.min(SANITY_MAX_LOSS, value)));
    }
}
