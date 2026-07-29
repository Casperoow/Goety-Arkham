package com.casper.goetyarkham.illager_treachery.encounter.formal;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.resources.ResourceLocation;

public final class FormalEncounterMetadata {
    public static final ResourceLocation TREACHERY = id("treachery");
    public static final ResourceLocation OMEN = id("omen");
    public static final ResourceLocation APOSTLES_OF_CTHULHU =
            id("apostles_of_cthulhu");
    public static final ResourceLocation APOSTLES_OF_HASTUR =
            id("apostles_of_hastur");

    private FormalEncounterMetadata() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(GoetyArkham.MOD_ID, path);
    }
}
