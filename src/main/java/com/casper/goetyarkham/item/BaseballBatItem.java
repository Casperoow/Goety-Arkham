package com.casper.goetyarkham.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main-hand melee weapon extending {@link Item} directly rather than {@link
 * net.minecraft.world.item.SwordItem}: unlike {@link MacheteItem}, this
 * weapon must not pick up sweeping-attack damage, bonus knockback, or
 * cobweb-cutting speed, and must not gain pickaxe/axe/shovel-style mining
 * behavior - none of which a plain {@link Item} has. Attack Damage/Speed are
 * therefore applied by hand via {@link #getDefaultAttributeModifiers(EquipmentSlot)}
 * (mirroring {@link KnifeItem}/{@link SwitchbladeItem}'s exact-double
 * pattern), and ordinary melee durability loss - the one piece of vanilla
 * weapon behavior this item does need - is restored by hand in {@link
 * #hurtEnemy} exactly like {@code SwordItem.hurtEnemy} does, since {@link
 * Item}'s own default {@code hurtEnemy} is a no-op. Its +2 Strength ({@link
 * BaseballBatStrengthBonusService}) is computed live from the wielder's
 * current main-hand item, mirroring {@link MacheteStrengthBonusService}, so
 * it can never leave residue after a hand swap, drop, break, death, or
 * relogin.
 */
public final class BaseballBatItem extends Item {
    public static final int MAX_DURABILITY = 80;
    public static final double FINAL_ATTACK_DAMAGE = 8.0D;
    public static final double FINAL_ATTACK_SPEED = 1.0D;
    public static final int STRENGTH_BONUS = 2;

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

    public BaseballBatItem() {
        super(new Item.Properties().stacksTo(1).durability(MAX_DURABILITY));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? MAINHAND_MODIFIERS : super.getDefaultAttributeModifiers(slot);
    }

    /**
     * Restores ordinary melee durability loss (1 point per successful hit),
     * matching {@code SwordItem.hurtEnemy} exactly - {@link Item}'s own
     * default implementation returns {@code true} without touching
     * durability at all.
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.goetyarkham.baseball_bat.main_hand")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.goetyarkham.baseball_bat.strength")
                .withStyle(ChatFormatting.GRAY));
    }
}
