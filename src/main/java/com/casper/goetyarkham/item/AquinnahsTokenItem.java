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
 * A token-slot Curio granting +2 Max Health, +4 Max Sanity, +1 Agility, and
 * +1 Willpower while worn. It also carries 4 points of vanilla durability:
 * whenever its wearer is directly attacked by a living entity, the token
 * consumes 1 durability (subject to Unbreaking) and redirects that attack to
 * the attacker instead. See {@link AquinnahsTokenEffectEvents} for the
 * server-side redirect logic.
 */
public final class AquinnahsTokenItem extends Item
        implements ICurioItem, EquipmentStatModifier {
    public static final int MAX_HEALTH_BONUS = 2;
    public static final int MAX_SANITY_BONUS = 4;
    public static final int AGILITY_BONUS = 1;
    public static final int WILLPOWER_BONUS = 1;
    public static final int MAX_DURABILITY = 4;
    private static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    GoetyArkham.MOD_ID, "aquinnahs_token");

    public AquinnahsTokenItem() {
        super(new Item.Properties().stacksTo(1).durability(MAX_DURABILITY));
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
            case AGILITY -> AGILITY_BONUS;
            case WILLPOWER -> WILLPOWER_BONUS;
            case STRENGTH, INTELLECT -> 0;
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
     * on unequip. The bonus is a flat addition in raw Max Health attribute
     * points (the same unit vanilla uses for effects like Health Boost),
     * not a percentage like the Leather Coat or Bulletproof Vest.
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

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CurioTooltipHelper.appendSlot(tooltip, "tooltip.goetyarkham.slot.token");
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                CurioTooltipHelper.attributeBonus(
                        MAX_HEALTH_BONUS, "attribute.name.generic.max_health"),
                CurioTooltipHelper.attributeBonus(
                        MAX_SANITY_BONUS, "attribute.name.goetyarkham.max_sanity"),
                CurioTooltipHelper.attributeBonus(
                        AGILITY_BONUS, "attribute.name.goetyarkham.agility"),
                CurioTooltipHelper.attributeBonus(
                        WILLPOWER_BONUS, "attribute.name.goetyarkham.willpower"),
                Component.translatable("tooltip.goetyarkham.aquinnahs_token.effect")
        );
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.aquinnahs_token.depleted")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
