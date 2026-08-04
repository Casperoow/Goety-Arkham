package com.casper.goetyarkham.network;

import com.casper.goetyarkham.item.MedicalTextsAbilityService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server: request use of the Medical Texts ability. No payload; the
 * server re-derives equip state, cooldown, and the check outcome entirely
 * from the sender's own authoritative state.
 */
public record ServerboundUseMedicalTextsPacket() {
    static void encode(ServerboundUseMedicalTextsPacket message, FriendlyByteBuf buffer) {
    }

    static ServerboundUseMedicalTextsPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundUseMedicalTextsPacket();
    }

    static void handle(
            ServerboundUseMedicalTextsPacket message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                MedicalTextsAbilityService.tryUse(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
