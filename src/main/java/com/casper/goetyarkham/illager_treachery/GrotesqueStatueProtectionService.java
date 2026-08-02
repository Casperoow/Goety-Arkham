package com.casper.goetyarkham.illager_treachery;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.item.GrotesqueStatueItem;
import com.casper.goetyarkham.item.ModItems;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative, per-global-event protection for equipped statues. */
public final class GrotesqueStatueProtectionService {
    private GrotesqueStatueProtectionService() {
    }

    public static EventSession beginEvent() {
        return new EventSession();
    }

    public static final class EventSession {
        private final Set<UUID> protectedPlayers = new HashSet<>();

        private EventSession() {
        }

        /**
         * Returns true for every repeated query after this player paid in this
         * event, while charging at most one statue exactly once.
         */
        public boolean tryProtect(ServerPlayer player) {
            if (protectedPlayers.contains(player.getUUID())) {
                return true;
            }
            return CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.NECKLACE))
                    .map(handler -> consumeFirstAffordable(player, handler))
                    .orElse(false);
        }

        private boolean consumeFirstAffordable(
                ServerPlayer player,
                ICurioStacksHandler handler) {
            List<Integer> storedSouls = new ArrayList<>();
            List<Integer> statueSlots = new ArrayList<>();
            for (int slot = 0; slot < handler.getStacks().getSlots(); slot++) {
                ItemStack stack = handler.getStacks().getStackInSlot(slot);
                if (stack.is(ModItems.GROTESQUE_STATUE.get())) {
                    statueSlots.add(slot);
                    storedSouls.add(ITotem.currentSouls(stack));
                }
            }

            int statueIndex = GrotesqueStatuePaymentPolicy.firstAffordableIndex(
                    storedSouls, GrotesqueStatueItem.TREACHERY_COST);
            if (statueIndex < 0) {
                return false;
            }

            int slot = statueSlots.get(statueIndex);
            ItemStack stack = handler.getStacks().getStackInSlot(slot);
            ITotem.decreaseSouls(stack, GrotesqueStatueItem.TREACHERY_COST);
            handler.getStacks().setStackInSlot(slot, stack);
            protectedPlayers.add(player.getUUID());
            SoulEnergyPoolService.refresh(player);
            player.displayClientMessage(Component.translatable(
                    "message.goetyarkham.grotesque_statue.protected"), true);
            player.playNotifySound(
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.65F, 1.0F);
            return true;
        }
    }
}
