package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.command.CuriosCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class CuriosForgeEvents {
    private CuriosForgeEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CuriosCommand.register(event.getDispatcher());
    }
}
