package com.casper.goetyarkham.network;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(GoetyArkham.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int messageId;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                messageId++,
                ClientboundStatsPacket.class,
                ClientboundStatsPacket::encode,
                ClientboundStatsPacket::decode,
                ClientboundStatsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void sendStats(ServerPlayer player, CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundStatsPacket(data));
    }
}
