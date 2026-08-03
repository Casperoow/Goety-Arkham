package com.casper.goetyarkham.client;

import com.casper.goetyarkham.loneliness.LonelinessSnapshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientLoneliness {
    private static LonelinessSnapshot snapshot = LonelinessSnapshot.UNAVAILABLE;

    private ClientLoneliness() {
    }

    public static LonelinessSnapshot snapshot() {
        return snapshot;
    }

    public static void acceptServerSnapshot(LonelinessSnapshot updated) {
        snapshot = updated;
    }

    public static void clear() {
        snapshot = LonelinessSnapshot.UNAVAILABLE;
    }
}
