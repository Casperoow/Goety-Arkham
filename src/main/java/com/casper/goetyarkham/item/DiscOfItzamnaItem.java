package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** A charm-only Curio that drives nearby non-boss hostiles away. */
public final class DiscOfItzamnaItem extends Item implements ICurioItem {
    public static final int MAX_DURABILITY = 600;
    public static final int DURABILITY_INTERVAL_TICKS = 20;

    private final Map<ItemStack, Integer> activeTicks = new WeakHashMap<>();

    public DiscOfItzamnaItem() {
        super(new Item.Properties().durability(MAX_DURABILITY));
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity().level().isClientSide
                || !CurioSlotIds.CHARM.equals(slotContext.identifier())
                || slotContext.cosmetic()
                || !(slotContext.entity() instanceof ServerPlayer player)) {
            activeTicks.remove(stack);
            return;
        }

        updateDurability(
                slotContext,
                stack,
                player,
                DiscOfItzamnaEffectService.activateFor(player)
        );
    }

    void updateDurability(
            SlotContext slotContext,
            ItemStack stack,
            ServerPlayer player,
            boolean effectActive) {
        if (!effectActive) {
            activeTicks.remove(stack);
            return;
        }

        int ticks = activeTicks.getOrDefault(stack, 0) + 1;
        if (ticks < DURABILITY_INTERVAL_TICKS) {
            activeTicks.put(stack, ticks);
            return;
        }

        activeTicks.remove(stack);
        stack.hurtAndBreak(
                1,
                player,
                brokenWearer -> CuriosApi.broadcastCurioBreakEvent(slotContext)
        );
    }

    @Override
    public void onEquip(
            SlotContext slotContext,
            ItemStack previousStack,
            ItemStack stack) {
        activeTicks.remove(stack);
    }

    @Override
    public void onUnequip(
            SlotContext slotContext,
            ItemStack newStack,
            ItemStack stack) {
        activeTicks.remove(stack);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.CHARM.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
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
                "tooltip.goetyarkham.disc_of_itzamna.effect"
        );
    }
}
