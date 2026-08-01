package com.casper.goetyarkham.network;

import com.casper.goetyarkham.client.ClientSanity;
import com.casper.goetyarkham.sanity.SanitySnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientboundSanityPacket(SanitySnapshot snapshot) {
    public static void encode(
            ClientboundSanityPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.snapshot.currentSanity());
        buffer.writeVarInt(message.snapshot.maximumSanity());
        buffer.writeVarInt(message.snapshot.permanentMaxLoss());
    }

    public static ClientboundSanityPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundSanityPacket(new SanitySnapshot(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()));
    }

    static void handle(
            ClientboundSanityPacket message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientSanity.acceptServerSnapshot(message.snapshot)
        ));
        context.setPacketHandled(true);
    }
}
