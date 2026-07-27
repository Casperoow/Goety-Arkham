package com.casper.goetyarkham.illager_treachery;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.command.IllagerTreacheryCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class IllagerTreacheryForgeEvents {
    private IllagerTreacheryForgeEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            IllagerTreacheryManager.get(event.getServer())
                    .processDailyBoundary(event.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            IllagerTreacheryManager.get(event.getServer())
                    .resolvePending(event.getServer());
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        IllagerTreacheryCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        IllagerTreacheryManager.remove(event.getServer());
    }
}
