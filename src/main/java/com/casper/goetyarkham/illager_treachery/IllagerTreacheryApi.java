package com.casper.goetyarkham.illager_treachery;

import com.casper.goetyarkham.illager_treachery.encounter.EncounterRegistry;
import com.casper.goetyarkham.illager_treachery.encounter.IllagerTreacheryEncounter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * Stable server-side entry points for encounter registration and external
 * trigger submission. Trigger requests are merged and resolved at server-tick
 * end; callers must not execute encounters directly.
 */
public final class IllagerTreacheryApi {
    private IllagerTreacheryApi() {
    }

    public static void registerEncounter(IllagerTreacheryEncounter encounter) {
        EncounterRegistry.INSTANCE.register(encounter);
    }

    public static IllagerTreacheryManager.SubmitResult submitTrigger(
            MinecraftServer server,
            TriggerSource source,
            Collection<ServerPlayer> relatedPlayers) {
        if (source == TriggerSource.DAILY) {
            throw new IllegalArgumentException(
                    "DAILY is managed by the server day-boundary service");
        }
        return IllagerTreacheryManager.get(server).submit(
                server,
                TriggerRequest.of(
                        source,
                        relatedPlayers.stream().map(ServerPlayer::getUUID).toList()),
                null
        );
    }

    public static IllagerTreacheryManager.SubmitResult submitItemTrigger(
            ServerPlayer player) {
        return submitTrigger(
                player.getServer(), TriggerSource.ITEM, List.of(player));
    }

    public static IllagerTreacheryManager.SubmitResult submitDeduplicated(
            MinecraftServer server,
            TriggerSource source,
            Collection<ServerPlayer> relatedPlayers,
            String externalInstanceKey) {
        return IllagerTreacheryManager.get(server).submit(
                server,
                TriggerRequest.of(
                        source,
                        relatedPlayers.stream().map(ServerPlayer::getUUID).toList()),
                externalInstanceKey
        );
    }
}
