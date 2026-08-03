package com.casper.goetyarkham.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Kept in the client package so dedicated servers never load Screen. */
@OnlyIn(Dist.CLIENT)
public final class ClientShiftState {
    private ClientShiftState() {
    }

    public static boolean isShiftDown() {
        return Screen.hasShiftDown();
    }
}
