package com.casper.goetyarkham;

import com.casper.goetyarkham.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(GoetyArkham.MOD_ID)
public final class GoetyArkham {
    public static final String MOD_ID = "goetyarkham";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GoetyArkham() {
        ModNetwork.register();
    }
}
