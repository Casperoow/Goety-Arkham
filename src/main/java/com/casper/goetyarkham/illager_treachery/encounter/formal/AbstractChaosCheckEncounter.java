package com.casper.goetyarkham.illager_treachery.encounter.formal;

import com.casper.goetyarkham.chaosbag.ChaosBaseValueSource;
import com.casper.goetyarkham.chaosbag.ChaosCheckModifier;
import com.casper.goetyarkham.chaosbag.ChaosCheckRequest;
import com.casper.goetyarkham.chaosbag.ChaosCheckResult;
import com.casper.goetyarkham.chaosbag.ChaosCheckService;
import com.casper.goetyarkham.chaosbag.ChaosGhoulService;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.IllagerTreacheryEncounter;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

abstract class AbstractChaosCheckEncounter
        implements IllagerTreacheryEncounter {
    private final ResourceLocation id;
    private final ResourceLocation group;
    private final int target;

    AbstractChaosCheckEncounter(
            ResourceLocation id, ResourceLocation group, int target) {
        this.id = id;
        this.group = group;
        this.target = target;
    }

    @Override
    public final ResourceLocation id() {
        return id;
    }

    @Override
    public final Set<ResourceLocation> encounterTags() {
        return Set.of(
                FormalEncounterMetadata.TREACHERY,
                FormalEncounterMetadata.OMEN);
    }

    @Override
    public final Optional<ResourceLocation> encounterGroup() {
        return Optional.of(group);
    }

    @Override
    public final Optional<String> nameTranslationKey() {
        return Optional.of("encounter." + id.getNamespace()
                + "." + id.getPath() + ".name");
    }

    @Override
    public final Optional<String> descriptionTranslationKey() {
        return Optional.of("encounter." + id.getNamespace()
                + "." + id.getPath() + ".description");
    }

    protected final ChaosCheckResult checkWillpower(
            EncounterExecutionContext context) {
        ServerPlayer player = context.player();
        int willpower =
                PlayerStatsService.getFinalValue(player, StatType.WILLPOWER);
        ChaosCheckRequest request = ChaosCheckRequest.builder(
                        player.getUUID(),
                        id,
                        ChaosBaseValueSource.WILLPOWER,
                        willpower,
                        target,
                        context.treachery().chaosBagSnapshot(),
                        player.getRandom()::nextInt)
                .modifiers(List.<ChaosCheckModifier>of())
                .environment(ChaosGhoulService.capture(player))
                .build();
        return ChaosCheckService.resolveAndApply(player, request);
    }
}
