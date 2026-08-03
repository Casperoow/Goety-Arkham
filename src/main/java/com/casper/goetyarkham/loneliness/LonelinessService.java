package com.casper.goetyarkham.loneliness;

import com.casper.goetyarkham.network.ModNetwork;
import com.casper.goetyarkham.sanity.SanityChangeCause;
import com.casper.goetyarkham.sanity.SanityService;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Server-authoritative facade for every Loneliness read and mutation. */
public final class LonelinessService {
    public static final int SANITY_DAMAGE_ON_SETTLE = 2;

    private LonelinessService() {
    }

    public static int getLoneliness(ServerPlayer player) {
        return mutable(player).map(IPlayerLoneliness::getLoneliness).orElse(0);
    }

    /**
     * Adds one stack of Loneliness. If this crosses the five-stack threshold,
     * atomically resets Loneliness to zero and settles Abandoned and Alone's
     * effect (2 Sanity damage) through the existing sanity deduction API.
     */
    public static void addLoneliness(ServerPlayer player) {
        Optional<PlayerLonelinessData> optional = mutable(player);
        if (optional.isEmpty()) {
            return;
        }
        PlayerLonelinessData data = optional.get();
        boolean settle = data.addOne();
        sync(player);
        if (settle) {
            SanityService.damageSanity(
                    player,
                    SANITY_DAMAGE_ON_SETTLE,
                    SanityChangeCause.ABANDONED_AND_ALONE);
        }
    }

    /**
     * Login/clone/dimension-safe repair. Settles any anomalous saved value at
     * or above the threshold exactly once, then synchronizes the client.
     */
    public static void reconcile(ServerPlayer player) {
        Optional<PlayerLonelinessData> optional = mutable(player);
        if (optional.isEmpty()) {
            return;
        }
        PlayerLonelinessData data = optional.get();
        if (data.consumePendingOverflowSettle()) {
            SanityService.damageSanity(
                    player,
                    SANITY_DAMAGE_ON_SETTLE,
                    SanityChangeCause.ABANDONED_AND_ALONE);
        }
        sync(player);
    }

    public static boolean sync(ServerPlayer player) {
        Optional<PlayerLonelinessData> optional = mutable(player);
        if (optional.isEmpty()) {
            return false;
        }
        PlayerLonelinessData data = optional.get();
        LonelinessSnapshot snapshot = new LonelinessSnapshot(data.getLoneliness());
        if (!data.needsClientSync(snapshot)) {
            return false;
        }
        if (!ModNetwork.sendLoneliness(player, snapshot)) {
            return false;
        }
        data.markClientSynced(snapshot);
        return true;
    }

    private static Optional<PlayerLonelinessData> mutable(ServerPlayer player) {
        return player.getCapability(LonelinessCapabilities.PLAYER_LONELINESS)
                .resolve()
                .filter(PlayerLonelinessData.class::isInstance)
                .map(PlayerLonelinessData.class::cast);
    }
}
