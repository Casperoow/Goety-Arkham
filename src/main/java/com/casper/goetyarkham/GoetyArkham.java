package com.casper.goetyarkham;

import com.casper.goetyarkham.attribute.ModAttributes;
import com.casper.goetyarkham.effect.ModEffects;
import com.casper.goetyarkham.illager_treachery.config.IllagerTreacheryConfig;
import com.casper.goetyarkham.illager_treachery.encounter.formal.BuiltInFormalEncounters;
import com.casper.goetyarkham.illager_treachery.encounter.type.BuiltInEncounterTypes;
import com.casper.goetyarkham.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(GoetyArkham.MOD_ID)
public final class GoetyArkham {
    public static final String MOD_ID = "goetyarkham";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GoetyArkham() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BuiltInEncounterTypes.register();
        BuiltInFormalEncounters.register();
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                IllagerTreacheryConfig.SPEC,
                "goetyarkham-illager_treachery.toml"
        );
        ModAttributes.register(modEventBus);
        ModEffects.register(modEventBus);
        ModNetwork.register();
    }
}
