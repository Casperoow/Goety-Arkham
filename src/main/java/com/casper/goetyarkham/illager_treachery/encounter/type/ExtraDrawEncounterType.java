package com.casper.goetyarkham.illager_treachery.encounter.type;

import com.casper.goetyarkham.illager_treachery.encounter.EncounterDefinitionException;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterParseContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterType;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Predicate;

public final class ExtraDrawEncounterType
        implements EncounterType<ExtraDrawEncounterType.Data> {
    @Override
    public Data parse(
            ResourceLocation encounterId,
            JsonObject data,
            EncounterParseContext context) throws EncounterDefinitionException {
        return new Data(
                encounterId,
                TypeFields.requiredString(
                        encounterId, data, "translation_key"),
                TypeFields.requiredBoolean(
                        encounterId, data, "once_per_player_per_event"));
    }

    @Override
    public void execute(Data data, EncounterExecutionContext context) {
        context.player().sendSystemMessage(
                Component.translatable(data.translationKey()));
        if (shouldRequestExtraDraw(
                data.oncePerPlayerPerEvent(),
                data.encounterId(),
                context::claimOncePerPlayerEvent)) {
            context.requestExtraDraw();
        }
    }

    public static boolean shouldRequestExtraDraw(
            boolean oncePerPlayerPerEvent,
            ResourceLocation encounterId,
            Predicate<ResourceLocation> claim) {
        return !oncePerPlayerPerEvent || claim.test(encounterId);
    }

    public record Data(
            ResourceLocation encounterId,
            String translationKey,
            boolean oncePerPlayerPerEvent) {
    }
}
