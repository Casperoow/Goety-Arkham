package com.casper.goetyarkham.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main-hand melee weapon extending {@link SwordItem} so it fully reuses
 * vanilla sword behavior (sweeping attack, cobweb-cutting speed, durability
 * loss rules, Mending/Unbreaking). Its {@code (Tiers.IRON, 2, -2.6F)}
 * constructor arguments are the standard {@link SwordItem} attack-damage/
 * speed parameters (no attribute-modifier override needed, unlike {@link
 * KnifeItem}/{@link SwitchbladeItem}): with the player's own base Attack
 * Damage/Speed of 1.0/4.0, this yields exact final main-hand values of
 * {@link #FINAL_ATTACK_DAMAGE}/{@link #FINAL_ATTACK_SPEED}. Its +1 Strength
 * ({@link MacheteStrengthBonusService}) is computed live from the wielder's
 * current main-hand item, mirroring {@link SwitchbladeAgilityBonusService},
 * so it can never leave residue after a hand swap, drop, break, or death.
 * Its +2 damage against {@link net.minecraft.world.entity.monster.Enemy}
 * targets is applied by {@link MacheteMonsterDamageEvents} directly onto the
 * single {@link net.minecraftforge.event.entity.living.LivingHurtEvent} of
 * an ordinary melee (or vanilla sweep) hit, so it never triggers a second
 * {@code hurt()} call or a second point of durability loss.
 */
public final class MacheteItem extends SwordItem {
    public static final int MAX_DURABILITY = 250;
    public static final int ATTACK_DAMAGE_MODIFIER = 2;
    public static final float ATTACK_SPEED_MODIFIER = -2.6F;
    public static final double FINAL_ATTACK_DAMAGE = 5.0D;
    public static final double FINAL_ATTACK_SPEED = 1.4D;
    public static final int STRENGTH_BONUS = 1;
    public static final float MONSTER_BONUS_DAMAGE = 2.0F;

    public MacheteItem() {
        super(Tiers.IRON, ATTACK_DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER,
                new Item.Properties().stacksTo(1).durability(MAX_DURABILITY));
    }

    /**
     * Forge's per-item enchanting-table/anvil compatibility hook (mirroring
     * {@link KnifeItem}'s override for Power): Sweeping Edge is explicitly
     * rejected here regardless of the vanilla sword enchantment category,
     * while every other enchantment (Sharpness, Smite, Looting, Unbreaking,
     * Mending, ...) falls through to {@link SwordItem}'s own standard
     * category-based check, so nothing else needs to be re-implemented or
     * whitelisted by hand.
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.SWEEPING_EDGE) {
            return false;
        }
        return super.canApplyAtEnchantingTable(stack, enchantment);
    }

    /**
     * Final Attack Damage/Speed are already shown automatically by the
     * vanilla "when in main hand" attribute tooltip built from this item's
     * own {@link SwordItem} attribute modifiers, so only Strength (a custom
     * stat with no vanilla attribute display) and the monster-only bonus
     * damage (not a stored attribute at all) need custom lines here.
     */
    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.goetyarkham.machete.main_hand")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.goetyarkham.machete.strength")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.goetyarkham.machete.monster_bonus")
                .withStyle(ChatFormatting.GRAY));
    }
}
