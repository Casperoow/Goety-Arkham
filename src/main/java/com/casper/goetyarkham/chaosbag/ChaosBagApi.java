package com.casper.goetyarkham.chaosbag;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * Stable server-side entry points for future scrolls, rituals, equipment and
 * other integrations. All mutations are global and persist in overworld data.
 */
public final class ChaosBagApi {
    private ChaosBagApi() {
    }

    public static ChaosBagLevel getLevel(MinecraftServer server) {
        return ChaosBagSavedData.get(server).level();
    }

    public static ChaosBagState.OperationResult setLevel(
            MinecraftServer server, ChaosBagLevel level) {
        return ChaosBagSavedData.get(server).setLevel(level);
    }

    public static ChaosBagState.OperationResult addTokens(
            MinecraftServer server,
            ChaosToken token,
            int count,
            ResourceLocation source) {
        return ChaosBagSavedData.get(server).add(token, count, source);
    }

    public static ChaosBagState.OperationResult removeTokens(
            MinecraftServer server,
            ChaosToken token,
            int count,
            ResourceLocation source) {
        return ChaosBagSavedData.get(server).remove(token, count, source);
    }

    public static ChaosBagState.OperationResult undoSource(
            MinecraftServer server, ResourceLocation source) {
        return ChaosBagSavedData.get(server).clearSource(source);
    }

    public static List<ChaosToken> getBaseConfiguration(MinecraftServer server) {
        return ChaosBagSavedData.get(server).baseTokens();
    }

    public static List<ChaosBagMutation> getMutations(MinecraftServer server) {
        return ChaosBagSavedData.get(server).mutations();
    }

    public static List<ChaosToken> getEffectiveConfiguration(
            MinecraftServer server) {
        return ChaosBagSavedData.get(server).effectiveTokens();
    }

    public static ChaosBagSnapshot snapshot(MinecraftServer server) {
        return ChaosBagSavedData.get(server).snapshot();
    }
}
