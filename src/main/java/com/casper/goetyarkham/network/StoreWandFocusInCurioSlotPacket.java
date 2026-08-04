package com.casper.goetyarkham.network;

import com.casper.goetyarkham.curios.FocusCurioService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server: store the wand's current focus into the lowest-numbered
 * empty Curios {@code focus} slot. No payload; the server re-derives the
 * wand and the slot to use from the sender's own state.
 */
public record StoreWandFocusInCurioSlotPacket() {
    static void encode(StoreWandFocusInCurioSlotPacket message, FriendlyByteBuf buffer) {
    }

    static StoreWandFocusInCurioSlotPacket decode(FriendlyByteBuf buffer) {
        return new StoreWandFocusInCurioSlotPacket();
    }

    static void handle(
            StoreWandFocusInCurioSlotPacket message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                FocusCurioService.storeWandFocus(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
