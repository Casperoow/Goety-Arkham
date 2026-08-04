package com.casper.goetyarkham.network;

import com.casper.goetyarkham.curios.FocusCurioService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server: swap the wand's current focus with the focus in the
 * given Curios {@code focus} slot index. Carries only the slot index; the
 * server re-derives every stack from the sender's own wand and Curios
 * inventory, never trusting client-supplied item data.
 */
public record SwapWandFocusWithCurioSlotPacket(int slotIndex) {
    static void encode(SwapWandFocusWithCurioSlotPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.slotIndex);
    }

    static SwapWandFocusWithCurioSlotPacket decode(FriendlyByteBuf buffer) {
        return new SwapWandFocusWithCurioSlotPacket(buffer.readVarInt());
    }

    static void handle(
            SwapWandFocusWithCurioSlotPacket message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                FocusCurioService.swapWandFocus(sender, message.slotIndex);
            }
        });
        context.setPacketHandled(true);
    }
}
