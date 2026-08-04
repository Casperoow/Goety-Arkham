package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * A Curio equippable in either the {@code hands} or {@code book} slot (not
 * both at once). While functionally worn, pressing the bound ability key
 * (see {@link MedicalTextsAbilityService}) performs a server-side Intellect
 * chaos check: success grants Instant Health I, failure deals 1 damage.
 * Either outcome starts a shared 100-tick item cooldown.
 *
 * <p>The tooltip references the ability key via {@link
 * Component#keybind(String)} instead of a client-only {@code KeyMapping}, so
 * this class stays safe to load on a dedicated server; the actual key
 * binding lives in the client-only {@code ModKeyMappings}.</p>
 */
public final class MedicalTextsItem extends Item implements ICurioItem {
    public static final String ABILITY_KEY_TRANSLATION_KEY =
            "key.goetyarkham.medical_texts_ability";

    public MedicalTextsItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return isSupportedSlot(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    private static boolean isSupportedSlot(String slotId) {
        return CurioSlotIds.HANDS.equals(slotId) || CurioSlotIds.BOOK.equals(slotId);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CurioTooltipHelper.appendSlot(tooltip, "tooltip.goetyarkham.medical_texts.slot");
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                Component.translatable(
                        "tooltip.goetyarkham.medical_texts.effect",
                        Component.keybind(ABILITY_KEY_TRANSLATION_KEY)));
    }
}
