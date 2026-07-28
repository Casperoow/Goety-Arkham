package com.casper.goetyarkham.illager_treachery.encounter;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.illager_treachery.config.EncounterConfigService;
import com.casper.goetyarkham.illager_treachery.data.IllagerTreacherySavedData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;

/**
 * Loads the highest-priority JSON resource for every namespace under the
 * shared encounter directory. Invalid files are rejected individually; the
 * complete accepted map is then installed atomically.
 */
public final class EncounterReloadListener
        extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY =
            "goetyarkham/illager_treachery/encounters";
    private static final Gson GSON =
            new GsonBuilder().setLenient().create();

    public EncounterReloadListener() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        EncounterTypeRegistry types = EncounterTypeRegistry.INSTANCE;
        types.freeze();
        EncounterDefinitionSetLoader.Result result;
        try {
            result = EncounterDefinitionSetLoader.load(
                    resources,
                    types,
                    EncounterParseContext.SERVER,
                    EncounterRegistry.INSTANCE::isJavaEncounter);
        } catch (Throwable throwable) {
            GoetyArkham.LOGGER.error(
                    "[illager_treachery] Encounter JSON validation aborted; "
                            + "the previous complete definition set remains active",
                    throwable);
            return;
        }
        result.rejected().forEach(rejection ->
                GoetyArkham.LOGGER.error(
                        "[illager_treachery] Rejected encounter JSON {}: {}",
                        rejection.id(),
                        rejection.reason()));

        try {
            EncounterRegistry.INSTANCE.replaceDataDriven(result.accepted());
        } catch (RuntimeException exception) {
            GoetyArkham.LOGGER.error(
                    "[illager_treachery] Encounter data reload was not installed; "
                            + "the previous complete definition set remains active",
                    exception);
            return;
        }

        GoetyArkham.LOGGER.info(
                "[illager_treachery] Installed {} data-driven encounters; rejected {}",
                result.accepted().size(),
                result.rejected().size());
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.overworld() != null) {
            EncounterConfigService config = EncounterConfigService.get(server);
            config.initialize(IllagerTreacherySavedData.get(server));
            EncounterConfigService.Operation sync = config.sync();
            if (!sync.success()) {
                GoetyArkham.LOGGER.error(
                        "[illager_treachery] Data reload succeeded but TOML sync failed: {}",
                        sync.message());
            }
        }
    }
}
