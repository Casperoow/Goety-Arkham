package com.casper.goetyarkham.chaosbag;

import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Server-side orchestration around the pure engine. The request is already a
 * complete snapshot, so global changes made after construction affect only a
 * later check.
 */
public final class ChaosCheckService {
    private ChaosCheckService() {
    }

    public static ChaosCheckRequest createRequest(
            ServerPlayer player,
            ResourceLocation source,
            ChaosBaseValueSource baseValueSource,
            int fixedBaseValue,
            int targetValue,
            List<ChaosCheckModifier> modifiers,
            ChaosBagSnapshot bagSnapshot,
            List<ChaosToken> forcedTokens,
            ChaosRandom random) {
        int currentBaseValue = baseValueSource.stat()
                .map(stat -> PlayerStatsService.getFinalValue(player, stat))
                .orElse(fixedBaseValue);
        return ChaosCheckRequest.builder(
                        player.getUUID(),
                        source,
                        baseValueSource,
                        currentBaseValue,
                        targetValue,
                        bagSnapshot,
                        random)
                .modifiers(modifiers)
                .environment(ChaosGhoulService.capture(player))
                .forcedTokens(forcedTokens)
                .build();
    }

    /**
     * Sends the complete result before applying marker consequences.
     */
    public static ChaosCheckResult resolveAndApply(
            ServerPlayer player, ChaosCheckRequest request) {
        if (!player.getUUID().equals(request.playerId())) {
            throw new IllegalArgumentException(
                    "request player does not match executing player");
        }
        ChaosCheckResult result = ChaosCheckEngine.resolve(request);
        player.sendSystemMessage(ChaosCheckText.summary(result));
        ChaosCheckText.notices(result).forEach(player::sendSystemMessage);
        applyConsequences(player, result);
        return result;
    }

    public static void applyConsequences(
            ServerPlayer player, ChaosCheckResult result) {
        for (ChaosConsequence consequence : result.consequences()) {
            switch (consequence.kind()) {
                case REMOVE_SOUL -> SoulEnergyPoolService.removeSoul(
                        player, consequence.amount());
                case DAMAGE -> player.hurt(
                        player.damageSources().magic(),
                        consequence.amount());
                case SPAWN_GHOUL -> {
                    for (int count = 0; count < consequence.amount(); count++) {
                        ChaosGhoulService.spawnNearby(player);
                    }
                }
            }
        }
    }
}
