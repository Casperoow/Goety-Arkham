package com.casper.goetyarkham.illager_treachery.encounter.formal;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.chaosbag.ChaosCheckResult;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class TheYellowSignEncounter
        extends AbstractChaosCheckEncounter {
    public static final ResourceLocation ID =
            new ResourceLocation(GoetyArkham.MOD_ID, "the_yellow_sign");

    public TheYellowSignEncounter() {
        super(ID, FormalEncounterMetadata.APOSTLES_OF_HASTUR, 4);
    }

    @Override
    public void execute(EncounterExecutionContext context) {
        ServerPlayer player = context.player();
        player.sendSystemMessage(Component.translatable(
                "encounter.goetyarkham.the_yellow_sign.description"));
        ChaosCheckResult result = checkWillpower(context);
        if (!result.success()) {
            SoulEnergyPoolService.removeSoul(player, 200);
        }
    }
}
