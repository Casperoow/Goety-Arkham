package com.casper.goetyarkham.network;

import com.casper.goetyarkham.client.ClientSoulPool;
import com.casper.goetyarkham.soul.SoulPoolSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientboundSoulPoolPacket(SoulPoolSnapshot snapshot) {
    static void encode(ClientboundSoulPoolPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.snapshot.currentSoul());
        buffer.writeVarInt(message.snapshot.maximumSoul());
        buffer.writeBoolean(message.snapshot.hasContainer());
        buffer.writeBoolean(message.snapshot.arcaMode());
    }

    static ClientboundSoulPoolPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundSoulPoolPacket(new SoulPoolSnapshot(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean()
        ));
    }

    static void handle(
            ClientboundSoulPoolPacket message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientSoulPool.acceptServerSnapshot(message.snapshot)
        ));
        context.setPacketHandled(true);
    }
}
