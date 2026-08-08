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
 * A {@link CurioSlotIds#ASSET} Curio that grants +4 Intellect and +1 Attack
 * Speed while functionally worn. Intellect is contributed through {@link
 * EquipmentStatModifier}, mixed into the Player Stats capability exactly
 * like {@link MagnifyingGlassItem}; Attack Speed is a real vanilla {@link
 * Attributes#ATTACK_SPEED} addition applied by Curios itself, with the
 * modifier UUID derived from the concrete {@link SlotContext} (identifier +
 * index) so repeated refreshes (equip-state sync, login, dimension change,
 * respawn) reuse the same id instead of stacking, and distinct Asset slot
 * indices stack independently.
 */
public final class WorkingAHunchItem extends Item
        implements ICurioItem, EquipmentStatModifier {
    public static final int INTELLECT_BONUS = 4;
    public static final double ATTACK_SPEED_BONUS = 1.0D;
    private static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(
                    GoetyArkham.MOD_ID, "working_a_hunch");

    public WorkingAHunchItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.ASSET.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public int getEquipmentStatModifier(
            StatType stat, SlotContext slotContext, ItemStack stack) {
        if (!CurioSlotIds.ASSET.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || stat != StatType.INTELLECT) {
            return 0;
        }
        return INTELLECT_BONUS;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, UUID uuid, ItemStack stack) {
        if (!CurioSlotIds.ASSET.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || !(slotContext.entity() instanceof Player)) {
            return ImmutableMultimap.of();
        }
        return ImmutableMultimap.of(
                Attributes.ATTACK_SPEED, attackSpeedModifier(slotContext));
    }

    /**
     * The modifier UUID is derived from the item id and the concrete Curios
     * slot identity (identifier + index), so repeated refreshes of the same
     * slot reuse the same id instead of stacking, distinct Asset slot
     * indices stack independently, and Curios removes the modifier cleanly
     * on unequip.
     */
    private static AttributeModifier attackSpeedModifier(SlotContext slotContext) {
        String identity = GoetyArkham.MOD_ID
                + ":attack_speed/"
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
                ATTACK_SPEED_BONUS,
                AttributeModifier.Operation.ADDITION);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CurioTooltipHelper.appendSlot(
                tooltip, "tooltip.goetyarkham.working_a_hunch.slot");
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                CurioTooltipHelper.attributeBonus(
                        INTELLECT_BONUS, "attribute.name.goetyarkham.intellect"),
                CurioTooltipHelper.attributeBonus(
                        (int) ATTACK_SPEED_BONUS, "attribute.name.generic.attack_speed")
        );
    }
}
