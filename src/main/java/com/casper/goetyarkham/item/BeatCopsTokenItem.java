package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.SanityAttributeModifiers;
import com.casper.goetyarkham.stats.EquipmentStatModifier;
import com.casper.goetyarkham.stats.StatType;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * A token-slot Curio granting +1 Strength, +1 Agility, +6 Max Health, and +2
 * Max Sanity while worn. Taking damage builds up to 3 stacks of "Beat Cop's
 * Retaliation" on this exact stack's NBT; the wearer's next successful direct
 * melee attack consumes all stacks for +2 bonus damage each. See
 * {@link BeatCopsTokenEffectEvents} for the server-side stack/damage logic.
 */
public final class BeatCopsTokenItem extends Item
        implements ICurioItem, EquipmentStatModifier {
    public static final int STRENGTH_BONUS = 1;
    public static final int AGILITY_BONUS = 1;
    public static final int MAX_HEALTH_BONUS = 6;
    public static final int MAX_SANITY_BONUS = 2;
    public static final int MAX_STACKS = 3;
    public static final int DAMAGE_PER_STACK = 2;

    private static final String STACKS_TAG_KEY = "BeatCopStacks";
    private static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    GoetyArkham.MOD_ID, "beat_cops_token");

    public BeatCopsTokenItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.TOKEN.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public int getEquipmentStatModifier(
            StatType stat, SlotContext slotContext, ItemStack stack) {
        if (!CurioSlotIds.TOKEN.equals(slotContext.identifier())
                || slotContext.cosmetic()) {
            return 0;
        }
        return switch (stat) {
            case STRENGTH -> STRENGTH_BONUS;
            case AGILITY -> AGILITY_BONUS;
            case WILLPOWER, INTELLECT -> 0;
        };
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, UUID uuid, ItemStack stack) {
        if (!CurioSlotIds.TOKEN.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || !(slotContext.entity() instanceof Player)) {
            return ImmutableMultimap.of();
        }
        return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.MAX_HEALTH, maxHealthModifier(slotContext))
                .putAll(SanityAttributeModifiers.maxSanityModifierMap(
                        ITEM_ID, slotContext, MAX_SANITY_BONUS))
                .build();
    }

    /**
     * The modifier UUID is derived from the item id and the concrete Curios
     * slot identity (identifier + index), so repeated refreshes of the same
     * slot (equip-state sync, login, dimension change, respawn) reuse the
     * same id instead of stacking, and Curios removes the modifier cleanly
     * on unequip.
     */
    private static AttributeModifier maxHealthModifier(SlotContext slotContext) {
        String identity = GoetyArkham.MOD_ID
                + ":max_health/"
                + ITEM_ID
                + "/"
                + slotContext.identifier()
                + "/"
                + slotContext.index();
        UUID modifierId = UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8));
        return new AttributeModifier(
                modifierId,
                identity,
                MAX_HEALTH_BONUS,
                AttributeModifier.Operation.ADDITION);
    }

    /** Current Beat Cop's Retaliation stacks stored on this exact stack, clamped to [0, {@link #MAX_STACKS}]. */
    public static int getStacks(ItemStack stack) {
        if (!stack.hasTag()) {
            return 0;
        }
        return Mth.clamp(stack.getOrCreateTag().getInt(STACKS_TAG_KEY), 0, MAX_STACKS);
    }

    public static void setStacks(ItemStack stack, int stacks) {
        int clamped = Mth.clamp(stacks, 0, MAX_STACKS);
        if (clamped == 0) {
            if (stack.hasTag()) {
                stack.getTag().remove(STACKS_TAG_KEY);
            }
        } else {
            stack.getOrCreateTag().putInt(STACKS_TAG_KEY, clamped);
        }
    }

    public static int addStacks(ItemStack stack, int amount) {
        int updated = Mth.clamp(getStacks(stack) + amount, 0, MAX_STACKS);
        setStacks(stack, updated);
        return updated;
    }

    public static void clearStacks(ItemStack stack) {
        setStacks(stack, 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getStacks(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(getStacks(stack) * 13.0F / MAX_STACKS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3070E0;
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
                        MAX_HEALTH_BONUS, "attribute.name.generic.max_health"),
                CurioTooltipHelper.attributeBonus(
                        MAX_SANITY_BONUS, "attribute.name.goetyarkham.max_sanity")
        );
        if (ShiftTooltipHelper.isShiftDown()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.beat_cops_token.stack_gain")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.beat_cops_token.stack_consume")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.beat_cops_token.current_stacks",
                            getStacks(stack),
                            MAX_STACKS)
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.beat_cops_token.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
