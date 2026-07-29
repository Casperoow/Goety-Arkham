package com.casper.goetyarkham.illager_treachery.encounter.formal;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.chaosbag.ChaosCheckResult;
import com.casper.goetyarkham.effect.DreamsOfRlyehEffectService;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class DreamsOfRlyehEncounter
        extends AbstractChaosCheckEncounter {
    public static final ResourceLocation ID =
            new ResourceLocation(GoetyArkham.MOD_ID, "dreams_of_rlyeh");

    public DreamsOfRlyehEncounter() {
        super(ID, FormalEncounterMetadata.APOSTLES_OF_CTHULHU, 3);
    }

    @Override
    public void execute(EncounterExecutionContext context) {
        ServerPlayer player = context.player();
        player.sendSystemMessage(Component.translatable(
                "encounter.goetyarkham.dreams_of_rlyeh.description"));
        DreamsOfRlyehEffectService.applyOrRefresh(player);
        ChaosCheckResult result = checkWillpower(context);
        if (result.success()) {
            DreamsOfRlyehEffectService.removeExplicitly(player);
        }
    }
}
