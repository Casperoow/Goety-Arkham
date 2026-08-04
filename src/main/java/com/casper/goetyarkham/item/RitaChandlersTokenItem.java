package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.effect.RitaChandlersAuraEffectService;
import com.casper.goetyarkham.sanity.SanityAttributeModifiers;
import com.casper.goetyarkham.stats.EquipmentStatModifier;
import com.casper.goetyarkham.stats.StatType;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
 * A token-slot Curio granting +6 Max Health, +3 Max Sanity, and +1 Strength
 * while functionally worn, plus a continuous radius aura (see
 * {@link RitaChandlersAuraEffectService}): every {@code ServerPlayer} within
 * 10 blocks, the wearer included, gains +1 Strength and +2 flat bonus
 * damage on their own attacks (see {@link RitaChandlersTokenEffectEvents})
 * for as long as they stay in range.
 */
public final class RitaChandlersTokenItem extends Item
        implements ICurioItem, EquipmentStatModifier {
    public static final int MAX_HEALTH_BONUS = 6;
    public static final int MAX_SANITY_BONUS = 3;
    public static final int STRENGTH_BONUS = 1;
    private static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    GoetyArkham.MOD_ID, "rita_chandlers_token");

    public RitaChandlersTokenItem() {
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
            case AGILITY, WILLPOWER, INTELLECT -> 0;
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

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity().level().isClientSide
                || !CurioSlotIds.TOKEN.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || !(slotContext.entity() instanceof ServerPlayer player)) {
            return;
        }
        RitaChandlersAuraEffectService.pulseAuraFrom(player);
    }

    @Override
    public void onUnequip(
            SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (!CurioSlotIds.TOKEN.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || !(slotContext.entity() instanceof ServerPlayer player)) {
            return;
        }
        RitaChandlersAuraEffectService.clearOwnBlessing(player);
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
                        MAX_HEALTH_BONUS, "attribute.name.generic.max_health"),
                CurioTooltipHelper.attributeBonus(
                        MAX_SANITY_BONUS, "attribute.name.goetyarkham.max_sanity"),
                CurioTooltipHelper.attributeBonus(
                        STRENGTH_BONUS, "attribute.name.goetyarkham.strength")
        );
        if (ShiftTooltipHelper.isShiftDown()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.rita_chandlers_token.aura_effect")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.rita_chandlers_token.aura_self")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.rita_chandlers_token.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
