package com.casper.goetyarkham.illager_treachery.encounter.type;

import com.casper.goetyarkham.illager_treachery.encounter.EncounterDefinitionException;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterParseContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterType;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public final class MessageEncounterType
        implements EncounterType<MessageEncounterType.Data> {
    @Override
    public Data parse(
            ResourceLocation encounterId,
            JsonObject data,
            EncounterParseContext context) throws EncounterDefinitionException {
        return new Data(TypeFields.requiredString(
                encounterId, data, "translation_key"));
    }

    @Override
    public void execute(Data data, EncounterExecutionContext context) {
        deliver(data, context.player()::sendSystemMessage);
    }

    public static void deliver(Data data, Consumer<Component> target) {
        target.accept(Component.translatable(data.translationKey()));
    }

    public record Data(String translationKey) {
    }
}
