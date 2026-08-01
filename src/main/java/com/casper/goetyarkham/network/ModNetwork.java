package com.casper.goetyarkham.network;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.sanity.SanitySnapshot;
import com.casper.goetyarkham.soul.SoulPoolSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

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
        CHANNEL.registerMessage(
                messageId++,
                ClientboundSoulPoolPacket.class,
                ClientboundSoulPoolPacket::encode,
                ClientboundSoulPoolPacket::decode,
                ClientboundSoulPoolPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                messageId++,
                ClientboundSanityPacket.class,
                ClientboundSanityPacket::encode,
                ClientboundSanityPacket::decode,
                ClientboundSanityPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static boolean sendStats(
            @Nullable ServerPlayer player, @Nullable CompoundTag data) {
        if (!canSendTo(player) || data == null) {
            return false;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundStatsPacket(data));
        return true;
    }

    public static boolean sendSoulPool(
            @Nullable ServerPlayer player, @Nullable SoulPoolSnapshot snapshot) {
        if (!canSendTo(player) || snapshot == null) {
            return false;
        }
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ClientboundSoulPoolPacket(snapshot)
        );
        return true;
    }

    public static boolean sendSanity(
            @Nullable ServerPlayer player, @Nullable SanitySnapshot snapshot) {
        if (!canSendTo(player) || snapshot == null) {
            return false;
        }
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ClientboundSanityPacket(snapshot)
        );
        return true;
    }

    /**
     * A ServerPlayer exists before its play connection is installed while
     * capabilities are being restored. Targeted packets are only safe after
     * both layers of that connection are present and connected.
     */
    public static boolean canSendTo(@Nullable ServerPlayer player) {
        return player != null
                && player.connection != null
                && player.connection.connection != null
                && player.connection.connection.isConnected();
    }
}
