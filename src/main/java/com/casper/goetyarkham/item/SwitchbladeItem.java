package com.casper.goetyarkham.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main-hand melee weapon. Extends {@link SwordItem} for its melee/durability/
 * sweep behavior, but the tier passed to the super constructor is otherwise
 * inert: {@link #getDefaultAttributeModifiers(EquipmentSlot)} is fully
 * overridden below (mirroring {@link KnifeItem}) so the final main-hand
 * tooltip values are exact doubles and every other slot gets no bonus at
 * all. Its Agility bonus ({@link SwitchbladeAgilityBonusService}) and
 * conditional Strength-gated bonus damage ({@link
 * SwitchbladeDamageBonusService}) are both computed live from the wielder's
 * current main-hand item rather than stored on this item, so neither can
 * leave residue after a hand swap, drop, death, dimension change, or
 * relogin.
 */
public final class SwitchbladeItem extends SwordItem {
    public static final int MAX_DURABILITY = 250;
    public static final double FINAL_ATTACK_DAMAGE = 3.5D;
    public static final double FINAL_ATTACK_SPEED = 2.0D;
    public static final int AGILITY_BONUS = 2;

    /**
     * A fresh {@link net.minecraft.world.entity.player.Player}'s own base
     * Attack Damage/Speed - see {@link KnifeItem}'s identical constants for
     * why this must not be {@code Attributes.ATTACK_DAMAGE.getDefaultValue()}.
     */
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0D;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0D;

    private static final Multimap<Attribute, AttributeModifier> MAINHAND_MODIFIERS =
            ImmutableMultimap.of(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                            BASE_ATTACK_DAMAGE_UUID,
                            "Weapon modifier",
                            FINAL_ATTACK_DAMAGE - PLAYER_BASE_ATTACK_DAMAGE,
                            AttributeModifier.Operation.ADDITION),
                    Attributes.ATTACK_SPEED,
                    new AttributeModifier(
                            BASE_ATTACK_SPEED_UUID,
                            "Weapon modifier",
                            FINAL_ATTACK_SPEED - PLAYER_BASE_ATTACK_SPEED,
                            AttributeModifier.Operation.ADDITION));

    public SwitchbladeItem() {
        super(Tiers.IRON, 0, 0.0F, new Item.Properties().stacksTo(1).durability(MAX_DURABILITY));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? MAINHAND_MODIFIERS : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.goetyarkham.switchblade.main_hand")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.goetyarkham.switchblade.agility")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.goetyarkham.switchblade.strength_requirement")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.goetyarkham.switchblade.bonus_damage")
                .withStyle(ChatFormatting.GRAY));
    }
}
