package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.EquipmentStatModifier;
import com.casper.goetyarkham.stats.StatType;
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
 * A necklace Curio that grants +1 to the four Goety: Arkham stats and +1
 * vanilla Luck while functionally worn.
 */
public final class RabbitFootItem extends Item
        implements ICurioItem, EquipmentStatModifier {
    public static final int STRENGTH_BONUS = 1;
    public static final int AGILITY_BONUS = 1;
    public static final int WILLPOWER_BONUS = 1;
    public static final int INTELLECT_BONUS = 1;
    public static final double LUCK_BONUS = 1.0D;
    private static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    GoetyArkham.MOD_ID, "rabbit_foot");

    public RabbitFootItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.NECKLACE.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public int getEquipmentStatModifier(
            StatType stat, SlotContext slotContext, ItemStack stack) {
        if (!CurioSlotIds.NECKLACE.equals(slotContext.identifier())
                || slotContext.cosmetic()) {
            return 0;
        }
        return switch (stat) {
            case STRENGTH -> STRENGTH_BONUS;
            case AGILITY -> AGILITY_BONUS;
            case WILLPOWER -> WILLPOWER_BONUS;
            case INTELLECT -> INTELLECT_BONUS;
        };
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, UUID uuid, ItemStack stack) {
        if (!CurioSlotIds.NECKLACE.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || !(slotContext.entity() instanceof Player)) {
            return ImmutableMultimap.of();
        }
        return ImmutableMultimap.of(Attributes.LUCK, luckModifier(slotContext));
    }

    /**
     * The modifier UUID is derived from the item id and the concrete Curios
     * slot identity (identifier + index), so repeated refreshes of the same
     * slot reuse the same id instead of stacking, distinct necklace slots
     * stack independently, and Curios removes the modifier cleanly on
     * unequip.
     */
    private static AttributeModifier luckModifier(SlotContext slotContext) {
        String identity = GoetyArkham.MOD_ID
                + ":luck/"
                + ITEM_ID
                + "/"
                + slotContext.identifier()
                + "/"
                + slotContext.index();
        UUID luckId = UUID.nameUUIDFromBytes(
                identity.getBytes(StandardCharsets.UTF_8));
        return new AttributeModifier(
                luckId, identity, LUCK_BONUS, AttributeModifier.Operation.ADDITION);
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
                        WILLPOWER_BONUS, "attribute.name.goetyarkham.willpower"),
                CurioTooltipHelper.attributeBonus(
                        INTELLECT_BONUS, "attribute.name.goetyarkham.intellect"),
                CurioTooltipHelper.attributeBonus(
                        (int) LUCK_BONUS, "attribute.name.generic.luck")
        );
    }
}
