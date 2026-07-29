package com.casper.goetyarkham.illager_treachery;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.command.IllagerTreacheryCommand;
import com.casper.goetyarkham.command.ChaosBagCommand;
import com.casper.goetyarkham.illager_treachery.config.EncounterConfigService;
import com.casper.goetyarkham.illager_treachery.data.IllagerTreacherySavedData;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
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
        ChaosBagCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EncounterReloadListener());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        EncounterConfigService.get(event.getServer())
                .initialize(IllagerTreacherySavedData.get(event.getServer()));
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        IllagerTreacheryManager.remove(event.getServer());
        EncounterConfigService.remove(event.getServer());
    }
}
