package com.casper.goetyarkham.item;

import com.casper.goetyarkham.client.ClientLoneliness;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Client-safe read of the local player's synced Loneliness count for tooltips. */
public final class LonelinessTooltipHelper {
    private LonelinessTooltipHelper() {
    }

    public static int currentLoneliness() {
        Integer value = DistExecutor.unsafeCallWhenOn(
                Dist.CLIENT,
                () -> () -> ClientLoneliness.snapshot().loneliness());
        return value == null ? 0 : value;
    }
}
