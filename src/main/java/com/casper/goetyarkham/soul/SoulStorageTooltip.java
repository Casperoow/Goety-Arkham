package com.casper.goetyarkham.soul;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared tooltip entry for items backed by Goety's per-stack soul storage. */
public final class SoulStorageTooltip {
    public static final String TRANSLATION_KEY =
            "info.goety.totem_of_souls.souls";

    private SoulStorageTooltip() {
    }

    public static void append(
            List<Component> tooltip, int currentSouls, int maximumSouls) {
        tooltip.add(Component.translatable(
                TRANSLATION_KEY, currentSouls, maximumSouls));
    }
}
