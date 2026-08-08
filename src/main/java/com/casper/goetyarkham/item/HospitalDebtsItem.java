package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.weakness.ILockedWeakness;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.UUID;

/** A locked weakness created and owned by On the Lam. */
public final class HospitalDebtsItem extends Item implements
        ICurioItem, ILockedWeakness {
    /** Applied as a percentage against the summed maximum soul energy, not a flat amount. */
    public static final int MAX_SOUL_ENERGY_PERCENT_PENALTY = -60;

    private static final String SOURCE_KEY = "GoetyArkhamHospitalDebtsSource";
    private static final String OWNER_KEY = "GoetyArkhamHospitalDebtsOwner";
    private static final String BINDING_KEY = "GoetyArkhamHospitalDebtsBinding";
    private static final String ON_THE_LAM_SOURCE = "on_the_lam";

    public HospitalDebtsItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CurioSlotIds.WEAKNESS.equals(slotContext.identifier())
                && slotContext.entity() instanceof Player;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return false;
    }

    @Override
    public ICurio.DropRule getDropRule(
            SlotContext slotContext,
            DamageSource source,
            int lootingLevel,
            boolean recentlyHit,
            ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
                        "tooltip.goetyarkham.hospital_debts.effect")
                .withStyle(ChatFormatting.RED));
    }

    public static ItemStack createBound(UUID owner, UUID binding) {
        ItemStack stack = new ItemStack(ModItems.HOSPITAL_DEBTS.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(SOURCE_KEY, ON_THE_LAM_SOURCE);
        tag.putUUID(OWNER_KEY, owner);
        tag.putUUID(BINDING_KEY, binding);
        return stack;
    }

    public static boolean isOnTheLamBound(ItemStack stack, UUID owner) {
        if (!stack.is(ModItems.HOSPITAL_DEBTS.get()) || stack.getTag() == null) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return ON_THE_LAM_SOURCE.equals(tag.getString(SOURCE_KEY))
                && tag.hasUUID(OWNER_KEY)
                && owner.equals(tag.getUUID(OWNER_KEY));
    }

    public static boolean isOnTheLamBound(
            ItemStack stack, UUID owner, UUID binding) {
        return isOnTheLamBound(stack, owner)
                && stack.getTag() != null
                && stack.getTag().hasUUID(BINDING_KEY)
                && binding.equals(stack.getTag().getUUID(BINDING_KEY));
    }

    /** True while {@code player} has a Hospital Debts functionally equipped in {@link CurioSlotIds#WEAKNESS}. */
    public static boolean isWearing(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.WEAKNESS))
                .map(HospitalDebtsItem::hasHospitalDebts)
                .orElse(false);
    }

    private static boolean hasHospitalDebts(ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (stacks.getStackInSlot(slot).is(ModItems.HOSPITAL_DEBTS.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link #MAX_SOUL_ENERGY_PERCENT_PENALTY} while functionally equipped,
     * 0 otherwise. Consumed by {@link
     * com.casper.goetyarkham.soul.SoulEnergyPoolService} as a percentage
     * modifier against the summed maximum soul energy, not a flat deduction.
     */
    public static int soulCapacityPercentModifier(ServerPlayer player) {
        return isWearing(player) ? MAX_SOUL_ENERGY_PERCENT_PENALTY : 0;
    }
}
