package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.EquipmentStatModifier;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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
 * A hands-slot Curio that grants a permanent +1 Strength while worn, and
 * accumulates up to +6 Intellect on this exact stack's own NBT as its wearer
 * defeats enemies (see {@link RolandsThirtyEightSpecialEvents}). Whenever the
 * wearer's final Intellect (base, equipment, this pistol's own kill bonus,
 * weaknesses, and any other live source) is at least 2, an additional +2
 * Strength ({@link RolandConditionalStrengthService}) and +1 Attack Damage
 * ({@link RolandDamageBonusService}) apply. Its signature weakness, Cover
 * Up, is auto-equipped and locked by {@link RolandsThirtyEightSpecialService}.
 */
public final class RolandsThirtyEightSpecialItem extends Item
        implements ICurioItem, EquipmentStatModifier {
    public static final int STRENGTH_BONUS = 1;
    public static final int INTELLECT_PER_KILL = 2;
    public static final int MAX_BONUS_INTELLECT = 6;

    private static final String BONUS_INTELLECT_TAG_KEY = "RolandBonusIntellect";

    public RolandsThirtyEightSpecialItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.HANDS.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public int getEquipmentStatModifier(
            StatType stat, SlotContext slotContext, ItemStack stack) {
        if (!CurioSlotIds.HANDS.equals(slotContext.identifier())
                || slotContext.cosmetic()) {
            return 0;
        }
        return switch (stat) {
            case STRENGTH -> STRENGTH_BONUS;
            case INTELLECT -> getBonusIntellect(stack);
            case AGILITY, WILLPOWER -> 0;
        };
    }

    /** Current kill-accumulated Intellect bonus stored on this exact stack, clamped to [0, {@link #MAX_BONUS_INTELLECT}]. */
    public static int getBonusIntellect(ItemStack stack) {
        if (!stack.hasTag()) {
            return 0;
        }
        return Mth.clamp(
                stack.getOrCreateTag().getInt(BONUS_INTELLECT_TAG_KEY),
                0,
                MAX_BONUS_INTELLECT);
    }

    public static void setBonusIntellect(ItemStack stack, int value) {
        int clamped = Mth.clamp(value, 0, MAX_BONUS_INTELLECT);
        if (clamped == 0) {
            if (stack.hasTag()) {
                stack.getTag().remove(BONUS_INTELLECT_TAG_KEY);
            }
        } else {
            stack.getOrCreateTag().putInt(BONUS_INTELLECT_TAG_KEY, clamped);
        }
    }

    /** Adds one kill's worth of bonus Intellect to this exact stack, clamped to the maximum. */
    public static int addKillBonus(ItemStack stack) {
        int updated = Mth.clamp(
                getBonusIntellect(stack) + INTELLECT_PER_KILL,
                0,
                MAX_BONUS_INTELLECT);
        setBonusIntellect(stack, updated);
        return updated;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY)
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(
                        "tooltip.goetyarkham.rolands_38_special.kill_bonus")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(CurioTooltipHelper.attributeBonus(
                        STRENGTH_BONUS, "attribute.name.goetyarkham.strength")
                .copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "tooltip.goetyarkham.rolands_38_special.current_bonus_intellect",
                        getBonusIntellect(stack))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "tooltip.goetyarkham.rolands_38_special.condition_heading")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(CurioTooltipHelper.attributeBonus(
                        RolandConditionalStrengthService.STRENGTH_BONUS,
                        "attribute.name.goetyarkham.strength")
                .copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(CurioTooltipHelper.attributeBonus(
                        (int) RolandDamageBonusService.DAMAGE_BONUS,
                        "attribute.name.generic.attack_damage")
                .copy().withStyle(ChatFormatting.GRAY));
        SignatureWeaknessTooltipHelper.append(
                tooltip, "item.goetyarkham.cover_up");
    }
}
