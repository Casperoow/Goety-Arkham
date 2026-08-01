package com.casper.goetyarkham.client;

import com.casper.goetyarkham.sanity.SanitySnapshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSanity {
    private static SanitySnapshot snapshot = SanitySnapshot.UNAVAILABLE;

    private ClientSanity() {
    }

    public static SanitySnapshot snapshot() {
        return snapshot;
    }

    public static void acceptServerSnapshot(SanitySnapshot updated) {
        snapshot = updated;
    }

    public static void clear() {
        snapshot = SanitySnapshot.UNAVAILABLE;
    }
}
