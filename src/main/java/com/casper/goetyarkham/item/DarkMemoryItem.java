package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.illager_treachery.IllagerTreacheryApi;
import com.casper.goetyarkham.illager_treachery.TriggerSource;
import com.casper.goetyarkham.sanity.SanityChangeCause;
import com.casper.goetyarkham.sanity.SanityService;
import com.casper.goetyarkham.sanity.weakness.ILockedWeakness;
import com.casper.goetyarkham.sanity.weakness.IWeaknessActivatable;
import com.casper.goetyarkham.sanity.weakness.WeaknessActivationCause;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A locked weakness created and owned by the Heirloom of Hyperborea. */
public final class DarkMemoryItem extends Item implements
        ICurioItem, IWeaknessActivatable, ILockedWeakness {
    private static final String SOURCE_KEY =
            "GoetyArkhamDarkMemorySource";
    private static final String OWNER_KEY =
            "GoetyArkhamDarkMemoryOwner";
    private static final String BINDING_KEY =
            "GoetyArkhamDarkMemoryBinding";
    private static final String HEIRLOOM_SOURCE =
            "heirloom_of_hyperborea";

    public static final int SANITY_DAMAGE = 2;
    private static final ThreadLocal<Set<UUID>> ACTIVE_WEARERS =
            ThreadLocal.withInitial(HashSet::new);

    public DarkMemoryItem() {
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
    public void activateWeakness(
            ServerPlayer player,
            ItemStack stack,
            int slotIndex,
            WeaknessActivationCause cause) {
        Set<UUID> active = ACTIVE_WEARERS.get();
        if (!active.add(player.getUUID())) {
            // Losing the final sanity point activates equipped weaknesses. Do
            // not let this Dark Memory recursively submit or damage a second
            // time while its own activation is still being settled.
            return;
        }
        try {
            MinecraftServer server = player.getServer();
            if (server != null) {
                // One manager submission for the whole server. The manager
                // performs candidate filtering, same-tick merging, locking,
                // and per-player draws.
                IllagerTreacheryApi.submitTrigger(
                        server,
                        TriggerSource.ITEM,
                        server.getPlayerList().getPlayers());
            }
            SanityService.damageSanity(
                    player, SANITY_DAMAGE, SanityChangeCause.DARK_MEMORY);
        } finally {
            active.remove(player.getUUID());
            if (active.isEmpty()) {
                ACTIVE_WEARERS.remove();
            }
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (ShiftTooltipHelper.isShiftDown()) {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.dark_memory.effect.treachery")
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.dark_memory.effect.sanity")
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable(
                            "tooltip.goetyarkham.dark_memory.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static ItemStack createBound(UUID owner, UUID binding) {
        ItemStack stack = new ItemStack(ModItems.DARK_MEMORY.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(SOURCE_KEY, HEIRLOOM_SOURCE);
        tag.putUUID(OWNER_KEY, owner);
        tag.putUUID(BINDING_KEY, binding);
        return stack;
    }

    public static boolean isHeirloomBound(ItemStack stack, UUID owner) {
        if (!stack.is(ModItems.DARK_MEMORY.get()) || stack.getTag() == null) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return HEIRLOOM_SOURCE.equals(tag.getString(SOURCE_KEY))
                && tag.hasUUID(OWNER_KEY)
                && owner.equals(tag.getUUID(OWNER_KEY));
    }

    public static boolean isHeirloomBound(
            ItemStack stack, UUID owner, UUID binding) {
        return isHeirloomBound(stack, owner)
                && stack.getTag() != null
                && stack.getTag().hasUUID(BINDING_KEY)
                && binding.equals(stack.getTag().getUUID(BINDING_KEY));
    }
}
