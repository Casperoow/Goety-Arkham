package com.casper.goetyarkham.illager_treachery.encounter.type;

import com.casper.goetyarkham.illager_treachery.encounter.EncounterDefinitionException;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterExecutionContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterParseContext;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterType;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public final class MobEffectEncounterType
        implements EncounterType<MobEffectEncounterType.Data> {
    private static final int MAX_AMPLIFIER = 255;

    @Override
    public Data parse(
            ResourceLocation encounterId,
            JsonObject data,
            EncounterParseContext context) throws EncounterDefinitionException {
        ResourceLocation effectId =
                TypeFields.requiredId(encounterId, data, "effect");
        MobEffect effect = context.resolveEffect(effectId).orElseThrow(() ->
                TypeFields.invalid(
                        encounterId,
                        "effect",
                        "does not identify a registered mob effect: " + effectId));
        int duration = TypeFields.requiredInt(
                encounterId, data, "duration_ticks");
        if (duration <= 0) {
            throw TypeFields.invalid(
                    encounterId, "duration_ticks", "must be greater than zero");
        }
        int amplifier = TypeFields.requiredInt(
                encounterId, data, "amplifier");
        if (amplifier < 0 || amplifier > MAX_AMPLIFIER) {
            throw TypeFields.invalid(
                    encounterId,
                    "amplifier",
                    "must be between 0 and " + MAX_AMPLIFIER);
        }
        return new Data(
                effectId,
                effect,
                duration,
                amplifier,
                TypeFields.requiredBoolean(encounterId, data, "ambient"),
                TypeFields.requiredBoolean(encounterId, data, "show_particles"),
                TypeFields.requiredBoolean(encounterId, data, "show_icon"),
                TypeFields.optionalString(encounterId, data, "translation_key"));
    }

    @Override
    public void execute(Data data, EncounterExecutionContext context) {
        context.player().addEffect(new MobEffectInstance(
                data.effect(),
                data.durationTicks(),
                data.amplifier(),
                data.ambient(),
                data.showParticles(),
                data.showIcon()));
        if (data.translationKey() != null) {
            context.player().sendSystemMessage(
                    Component.translatable(data.translationKey()));
        }
    }

    public record Data(
            ResourceLocation effectId,
            MobEffect effect,
            int durationTicks,
            int amplifier,
            boolean ambient,
            boolean showParticles,
            boolean showIcon,
            String translationKey) {
    }
}
