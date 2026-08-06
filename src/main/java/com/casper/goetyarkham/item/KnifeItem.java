package com.casper.goetyarkham.item;

import com.casper.goetyarkham.entity.ThrownKnifeEntity;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Main-hand melee weapon that can also be charge-thrown like a trident
 * ({@link UseAnim#SPEAR}, same 10-tick charge threshold as {@code
 * TridentItem}). Extends {@link SwordItem} for its melee/durability/sweep
 * behavior, but the tier passed to the super constructor is otherwise inert:
 * {@link #getDefaultAttributeModifiers(EquipmentSlot)} is fully overridden
 * below (mirroring {@code TridentItem}'s own override) so the final
 * main-hand tooltip values are exact doubles rather than whatever an
 * int-precision {@code SwordItem} constructor argument would round to, and
 * so every other slot gets no bonus at all.
 */
public final class KnifeItem extends SwordItem {
    public static final int MAX_DURABILITY = 250;
    public static final double FINAL_ATTACK_DAMAGE = 3.5D;
    public static final double FINAL_ATTACK_SPEED = 2.0D;
    public static final int THROW_CHARGE_THRESHOLD_TICKS = 10;
    public static final float THROWN_BASE_DAMAGE = 3.5F;
    private static final float THROW_VELOCITY = 2.6F;

    /**
     * A fresh {@link net.minecraft.world.entity.player.Player}'s own base
     * Attack Damage/Speed - {@code Player.createAttributes()} explicitly
     * overrides Attack Damage to 1.0, which is <em>not</em> the same as
     * {@code Attributes.ATTACK_DAMAGE.getDefaultValue()} (2.0, the generic
     * default used by non-player mobs like zombies). Attack Speed has no
     * such override (both are 4.0), but the constant is spelled out here too
     * so this modifier's math is self-contained and never silently drifts if
     * that ever changes.
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

    public KnifeItem() {
        super(Tiers.IRON, 0, 0.0F, new Item.Properties().stacksTo(1).durability(MAX_DURABILITY));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? MAINHAND_MODIFIERS : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof Player player) || level.isClientSide) {
            return;
        }
        int chargeDuration = getUseDuration(stack) - timeLeft;
        if (chargeDuration < THROW_CHARGE_THRESHOLD_TICKS) {
            return;
        }
        ItemStack thrownStack = stack.copy();
        thrownStack.setCount(1);
        boolean creativeThrow = player.getAbilities().instabuild;
        if (!creativeThrow) {
            stack.shrink(1);
        }
        ThrownKnifeEntity thrownKnife = new ThrownKnifeEntity(level, player, thrownStack);
        thrownKnife.setCreativeThrow(creativeThrow);
        thrownKnife.shootFromRotation(
                player, player.getXRot(), player.getYRot(), 0.0F, THROW_VELOCITY, 1.0F);
        level.addFreshEntity(thrownKnife);
        level.playSound(
                null, player, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /**
     * Forge's per-item enchanting-table compatibility hook: {@code
     * PowerEnchantment}'s category is {@code BOW} by default, so without
     * this override it could never legally land on a non-bow item. Every
     * other supported enchantment (Sharpness, Unbreaking, Mending) already
     * applies through the normal category checks {@link SwordItem}/{@code
     * TieredItem} inherit, so only Power needs a special case here.
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.POWER_ARROWS) {
            return true;
        }
        return super.canApplyAtEnchantingTable(stack, enchantment);
    }
}
