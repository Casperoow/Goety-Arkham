package com.casper.goetyarkham.sanity.weakness;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public final class WeaknessActivationService {
    private WeaknessActivationService() {
    }

    public static int activateAll(
            ServerPlayer player, WeaknessActivationCause cause) {
        int[] activated = {0};
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler
                .getStacksHandler(CurioSlotIds.WEAKNESS)
                .ifPresent(stacksHandler -> activated[0] = activateStacks(
                        player, stacksHandler, cause)));
        return activated[0];
    }

    static int activateStacks(
            ServerPlayer player,
            ICurioStacksHandler stacksHandler,
            WeaknessActivationCause cause) {
        int activated = 0;
        int slots = stacksHandler.getStacks().getSlots();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = stacksHandler.getStacks().getStackInSlot(slot);
            if (stack.isEmpty()
                    || !(stack.getItem() instanceof IWeaknessActivatable weakness)) {
                continue;
            }
            try {
                weakness.activateWeakness(player, stack, slot, cause);
                activated++;
            } catch (Throwable throwable) {
                GoetyArkham.LOGGER.error(
                        "[Sanity] Weakness activation failed: player={}, uuid={}, slot={}, item={}, cause={}",
                        player.getGameProfile().getName(),
                        player.getUUID(),
                        slot,
                        ForgeRegistries.ITEMS.getKey(stack.getItem()),
                        cause,
                        throwable);
            }
        }
        return activated;
    }
}
