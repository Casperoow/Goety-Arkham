package com.casper.goetyarkham.network;

import com.casper.goetyarkham.client.ClientLoneliness;
import com.casper.goetyarkham.loneliness.LonelinessSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientboundLonelinessPacket(LonelinessSnapshot snapshot) {
    public static void encode(
            ClientboundLonelinessPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.snapshot.loneliness());
    }

    public static ClientboundLonelinessPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundLonelinessPacket(new LonelinessSnapshot(
                buffer.readVarInt()
        ));
    }

    static void handle(
            ClientboundLonelinessPacket message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientLoneliness.acceptServerSnapshot(message.snapshot)
        ));
        context.setPacketHandled(true);
    }
}
