package com.casper.goetyarkham.network;

import com.casper.goetyarkham.client.ClientPlayerStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientboundStatsPacket(CompoundTag data) {
    public ClientboundStatsPacket {
        data = data.copy();
    }

    static void encode(ClientboundStatsPacket message, FriendlyByteBuf buffer) {
        buffer.writeNbt(message.data);
    }

    static ClientboundStatsPacket decode(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return new ClientboundStatsPacket(tag == null ? new CompoundTag() : tag);
    }

    static void handle(ClientboundStatsPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPlayerStats.acceptServerSnapshot(message.data)
        ));
        context.setPacketHandled(true);
    }
}
