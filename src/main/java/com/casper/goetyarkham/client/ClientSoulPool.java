package com.casper.goetyarkham.client;

import com.casper.goetyarkham.soul.SoulPoolSnapshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSoulPool {
    private static SoulPoolSnapshot snapshot = SoulPoolSnapshot.EMPTY;

    private ClientSoulPool() {
    }

    public static SoulPoolSnapshot snapshot() {
        return snapshot;
    }

    public static void acceptServerSnapshot(SoulPoolSnapshot updated) {
        snapshot = updated;
    }

    public static void clear() {
        snapshot = SoulPoolSnapshot.EMPTY;
    }
}
