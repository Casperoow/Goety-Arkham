package com.casper.goetyarkham.illager_treachery;

import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;

public final class PlayerEligibility {
    private PlayerEligibility() {
    }

    public static boolean isTriggerCandidate(
            ServerPlayer player, TreacherySettings settings) {
        return evaluate(facts(player), settings).candidate();
    }

    public static boolean isParticipant(ServerPlayer player) {
        PlayerFacts facts = facts(player);
        return facts.online()
                && !facts.creative()
                && !facts.spectator()
                && facts.dimensionAllowsRaids()
                && !facts.peaceful();
    }

    public static EligibilityResult evaluate(
            PlayerFacts facts, TreacherySettings settings) {
        if (!facts.online()) {
            return new EligibilityResult(false, false, Failure.OFFLINE);
        }
        if (facts.creative()) {
            return new EligibilityResult(false, false, Failure.CREATIVE);
        }
        if (facts.spectator()) {
            return new EligibilityResult(false, false, Failure.SPECTATOR);
        }
        if (!facts.dimensionAllowsRaids()) {
            return new EligibilityResult(false, false, Failure.DIMENSION);
        }
        if (facts.peaceful()) {
            return new EligibilityResult(false, false, Failure.PEACEFUL);
        }
        if (facts.maximumSoul() < settings.minimumSoul()) {
            return new EligibilityResult(false, true, Failure.SOUL_TOO_LOW);
        }
        return new EligibilityResult(true, true, Failure.NONE);
    }

    public static PlayerFacts facts(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        boolean online = server != null
                && server.getPlayerList().getPlayer(player.getUUID()) == player;
        boolean peaceful = server != null
                && server.getWorldData().getDifficulty() == Difficulty.PEACEFUL;
        return new PlayerFacts(
                online,
                player.isCreative(),
                player.isSpectator(),
                player.level().dimensionType().hasRaids(),
                peaceful,
                SoulEnergyPoolService.getMaximumSoul(player)
        );
    }

    public enum Failure {
        NONE,
        OFFLINE,
        CREATIVE,
        SPECTATOR,
        DIMENSION,
        PEACEFUL,
        SOUL_TOO_LOW
    }

    public record PlayerFacts(
            boolean online,
            boolean creative,
            boolean spectator,
            boolean dimensionAllowsRaids,
            boolean peaceful,
            int maximumSoul) {
    }

    public record EligibilityResult(
            boolean candidate,
            boolean participant,
            Failure failure) {
    }
}
