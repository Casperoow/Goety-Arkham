package com.casper.goetyarkham.sanity;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.attribute.ModAttributes;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import top.theillusivec4.curios.api.SlotContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Curios-facing entry point for additive maximum-sanity bonuses.
 *
 * <p>The UUID includes the item/source id and concrete Curios slot identity,
 * so identical bonuses in different slots stack and are removed normally by
 * Curios when the corresponding stack is unequipped.</p>
 */
public final class SanityAttributeModifiers {
    private SanityAttributeModifiers() {
    }

    public static AttributeModifier maxSanityAddition(
            ResourceLocation sourceId, SlotContext slotContext, double amount) {
        String identity = GoetyArkham.MOD_ID
                + ":max_sanity/"
                + sourceId
                + "/"
                + slotContext.identifier()
                + "/"
                + slotContext.index();
        UUID uuid = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        return new AttributeModifier(
                uuid,
                identity,
                amount,
                AttributeModifier.Operation.ADDITION);
    }

    public static Multimap<Attribute, AttributeModifier> maxSanityModifierMap(
            ResourceLocation sourceId, SlotContext slotContext, double amount) {
        return ImmutableMultimap.of(
                ModAttributes.MAX_SANITY.get(),
                maxSanityAddition(sourceId, slotContext, amount));
    }
}
