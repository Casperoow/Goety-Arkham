package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.SanityAttributeModifiers;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
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
 * A token-slot Curio granting +0.1 Movement Speed, +2 Attack Speed,
 * +4 Max Health, and +2 Max Sanity while functionally worn.
 */
public final class LeoDeLucasTokenItem extends Item implements ICurioItem {
    public static final double MOVEMENT_SPEED_BONUS = 0.1D;
    public static final double ATTACK_SPEED_BONUS = 2.0D;
    public static final int MAX_HEALTH_BONUS = 4;
    public static final int MAX_SANITY_BONUS = 2;
    private static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    GoetyArkham.MOD_ID, "leo_de_lucas_token");

    public LeoDeLucasTokenItem() {
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
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, UUID uuid, ItemStack stack) {
        if (!CurioSlotIds.TOKEN.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || !(slotContext.entity() instanceof Player)) {
            return ImmutableMultimap.of();
        }
        return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.MOVEMENT_SPEED,
                        modifier(slotContext, "movement_speed", MOVEMENT_SPEED_BONUS))
                .put(Attributes.ATTACK_SPEED,
                        modifier(slotContext, "attack_speed", ATTACK_SPEED_BONUS))
                .put(Attributes.MAX_HEALTH,
                        modifier(slotContext, "max_health", MAX_HEALTH_BONUS))
                .putAll(SanityAttributeModifiers.maxSanityModifierMap(
                        ITEM_ID, slotContext, MAX_SANITY_BONUS))
                .build();
    }

    /**
     * The modifier UUID is derived from the item id, attribute key, and the
     * concrete Curios slot identity (identifier + index), so repeated
     * refreshes of the same slot (equip-state sync, login, dimension change,
     * respawn) reuse the same id instead of stacking, and Curios removes the
     * modifier cleanly on unequip.
     */
    private static AttributeModifier modifier(
            SlotContext slotContext, String attributeKey, double amount) {
        String identity = GoetyArkham.MOD_ID
                + ":"
                + attributeKey
                + "/"
                + ITEM_ID
                + "/"
                + slotContext.identifier()
                + "/"
                + slotContext.index();
        UUID modifierId = UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8));
        return new AttributeModifier(
                modifierId, identity, amount, AttributeModifier.Operation.ADDITION);
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
                        MOVEMENT_SPEED_BONUS, "attribute.name.generic.movement_speed"),
                CurioTooltipHelper.attributeBonus(
                        ATTACK_SPEED_BONUS, "attribute.name.generic.attack_speed"),
                CurioTooltipHelper.attributeBonus(
                        MAX_HEALTH_BONUS, "attribute.name.generic.max_health"),
                CurioTooltipHelper.attributeBonus(
                        MAX_SANITY_BONUS, "attribute.name.goetyarkham.max_sanity")
        );
    }
}
