package com.casper.goetyarkham.gametest;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.chaosbag.ChaosBagApi;
import com.casper.goetyarkham.chaosbag.ChaosBagLevel;
import com.casper.goetyarkham.chaosbag.ChaosBagTags;
import com.casper.goetyarkham.effect.ModEffects;
import com.casper.goetyarkham.illager_treachery.config.EncounterConfigEntry;
import com.casper.goetyarkham.illager_treachery.config.EncounterConfigService;
import com.casper.goetyarkham.illager_treachery.data.IllagerTreacherySavedData;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterRegistry;
import com.casper.goetyarkham.illager_treachery.encounter.formal.DreamsOfRlyehEncounter;
import com.casper.goetyarkham.illager_treachery.encounter.formal.TheYellowSignEncounter;
import com.lion.graveyard.init.TGEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Minimal dedicated-runtime smoke test. Pure chaos-bag rules live in the
 * deterministic self-test; this test proves that registries, tags, commands,
 * server config and SavedData are wired after a real Forge server load.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChaosBagGameTests {
    private ChaosBagGameTests() {
    }

    @GameTest(template = "empty")
    public static void dedicatedRuntimeWiring(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        helper.assertTrue(
                new ResourceLocation(GoetyArkham.MOD_ID, "dreams_of_rlyeh")
                        .equals(ForgeRegistries.MOB_EFFECTS.getKey(
                                ModEffects.DREAMS_OF_RLYEH.get())),
                "Dreams of R'lyeh MobEffect was not registered");
        helper.assertTrue(
                EncounterRegistry.INSTANCE.get(DreamsOfRlyehEncounter.ID)
                        .isPresent(),
                "Dreams of R'lyeh encounter was not registered");
        helper.assertTrue(
                EncounterRegistry.INSTANCE.get(TheYellowSignEncounter.ID)
                        .isPresent(),
                "The Yellow Sign encounter was not registered");
        helper.assertTrue(
                ChaosBagApi.getLevel(server) == ChaosBagLevel.NORMAL,
                "A new server world did not start with the normal chaos-bag level");
        helper.assertTrue(
                TGEntities.GHOUL.get().is(ChaosBagTags.GHOULS),
                "graveyard:ghoul is missing from goetyarkham:ghouls");
        helper.assertTrue(
                TGEntities.GHOULING.get().is(ChaosBagTags.GHOULS),
                "graveyard:ghouling is missing from goetyarkham:ghouls");

        EncounterConfigService config = EncounterConfigService.get(server);
        EncounterConfigService.Operation initialization = config.initialize(
                IllagerTreacherySavedData.get(server));
        helper.assertTrue(
                initialization.success(),
                "Encounter config failed to initialize: "
                        + initialization.message());
        assertDefaultFormalEntry(helper, config, DreamsOfRlyehEncounter.ID);
        assertDefaultFormalEntry(helper, config, TheYellowSignEncounter.ID);

        var root = server.getCommands().getDispatcher().getRoot()
                .getChild("goetyarkham");
        helper.assertTrue(root != null, "/goetyarkham was not registered");
        helper.assertTrue(
                root.getChild("chaos_bag") != null,
                "/goetyarkham chaos_bag was not registered");
        helper.succeed();
    }

    private static void assertDefaultFormalEntry(
            GameTestHelper helper,
            EncounterConfigService config,
            ResourceLocation id) {
        EncounterConfigEntry entry = config.get(id).orElse(null);
        helper.assertTrue(entry != null, "Missing encounter config entry: " + id);
        helper.assertTrue(entry.enabled(), "Encounter is not enabled by default: " + id);
        helper.assertTrue(entry.weight() == 1L, "Encounter weight is not 1: " + id);
    }
}
