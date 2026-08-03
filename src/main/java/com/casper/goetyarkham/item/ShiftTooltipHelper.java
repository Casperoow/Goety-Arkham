package com.casper.goetyarkham.item;

import com.casper.goetyarkham.client.ClientShiftState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Client-safe access to Minecraft's standard tooltip Shift state. */
public final class ShiftTooltipHelper {
    private ShiftTooltipHelper() {
    }

    public static boolean isShiftDown() {
        Boolean down = DistExecutor.unsafeCallWhenOn(
                Dist.CLIENT,
                () -> ClientShiftState::isShiftDown);
        return Boolean.TRUE.equals(down);
    }
}
