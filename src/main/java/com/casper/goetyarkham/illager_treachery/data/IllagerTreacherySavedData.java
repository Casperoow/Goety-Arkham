package com.casper.goetyarkham.illager_treachery.data;

import com.casper.goetyarkham.illager_treachery.DailyProgress;
import com.casper.goetyarkham.illager_treachery.TreacherySettings;
import com.casper.goetyarkham.illager_treachery.encounter.EncounterSettings;
import com.casper.goetyarkham.illager_treachery.encounter.IllagerTreacheryEncounter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class IllagerTreacherySavedData extends SavedData {
    public static final String DATA_NAME = "goetyarkham_illager_treachery";

    private static final String VALID_DECISION_DAYS = "valid_decision_days";
    private static final String LAST_PROCESSED_MINECRAFT_DAY = "last_processed_minecraft_day";
    private static final String COOLDOWN_END_GAME_TICK = "cooldown_end_game_tick";
    private static final String HAS_ENABLED_OVERRIDE = "has_enabled_override";
    private static final String ENABLED_OVERRIDE = "enabled_override";
    private static final String ENCOUNTER_OVERRIDES = "encounter_overrides";
    private static final String ENCOUNTER_ID = "id";
    private static final String HAS_ENABLED = "has_enabled";
    private static final String ENABLED = "enabled";
    private static final String HAS_WEIGHT = "has_weight";
    private static final String WEIGHT = "weight";

    private int validDecisionDays;
    private long lastProcessedMinecraftDay = DailyProgress.UNINITIALIZED_DAY;
    private long cooldownEndGameTick;
    private Boolean enabledOverride;
    private final Map<ResourceLocation, EncounterOverride> encounterOverrides =
            new LinkedHashMap<>();

    public static IllagerTreacherySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                IllagerTreacherySavedData::load,
                IllagerTreacherySavedData::new,
                DATA_NAME
        );
    }

    public static IllagerTreacherySavedData load(CompoundTag tag) {
        IllagerTreacherySavedData data = new IllagerTreacherySavedData();
        data.validDecisionDays = Math.max(0, tag.getInt(VALID_DECISION_DAYS));
        if (tag.contains(LAST_PROCESSED_MINECRAFT_DAY, Tag.TAG_LONG)) {
            data.lastProcessedMinecraftDay = tag.getLong(LAST_PROCESSED_MINECRAFT_DAY);
        }
        data.cooldownEndGameTick = Math.max(0L, tag.getLong(COOLDOWN_END_GAME_TICK));
        if (tag.getBoolean(HAS_ENABLED_OVERRIDE)) {
            data.enabledOverride = tag.getBoolean(ENABLED_OVERRIDE);
        }

        ListTag overrides = tag.getList(ENCOUNTER_OVERRIDES, Tag.TAG_COMPOUND);
        for (int index = 0; index < overrides.size(); index++) {
            CompoundTag entry = overrides.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(ENCOUNTER_ID));
            if (id == null) {
                continue;
            }
            Boolean enabled = entry.getBoolean(HAS_ENABLED)
                    ? entry.getBoolean(ENABLED)
                    : null;
            Long weight = entry.getBoolean(HAS_WEIGHT)
                    ? Math.max(0L, entry.getLong(WEIGHT))
                    : null;
            if (enabled != null || weight != null) {
                data.encounterOverrides.put(id, new EncounterOverride(enabled, weight));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(VALID_DECISION_DAYS, validDecisionDays);
        tag.putLong(LAST_PROCESSED_MINECRAFT_DAY, lastProcessedMinecraftDay);
        tag.putLong(COOLDOWN_END_GAME_TICK, cooldownEndGameTick);
        tag.putBoolean(HAS_ENABLED_OVERRIDE, enabledOverride != null);
        if (enabledOverride != null) {
            tag.putBoolean(ENABLED_OVERRIDE, enabledOverride);
        }

        ListTag overrides = new ListTag();
        encounterOverrides.forEach((id, override) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(ENCOUNTER_ID, id.toString());
            entry.putBoolean(HAS_ENABLED, override.enabled() != null);
            if (override.enabled() != null) {
                entry.putBoolean(ENABLED, override.enabled());
            }
            entry.putBoolean(HAS_WEIGHT, override.weight() != null);
            if (override.weight() != null) {
                entry.putLong(WEIGHT, override.weight());
            }
            overrides.add(entry);
        });
        tag.put(ENCOUNTER_OVERRIDES, overrides);
        return tag;
    }

    public synchronized boolean observeMinecraftDay(long day) {
        DailyProgress progress = new DailyProgress(
                validDecisionDays, lastProcessedMinecraftDay);
        boolean changedDay = progress.enterDay(day);
        if (progress.lastProcessedDay() != lastProcessedMinecraftDay) {
            lastProcessedMinecraftDay = progress.lastProcessedDay();
            setDirty();
        }
        return changedDay;
    }

    public synchronized int recordValidDecisionDay() {
        if (validDecisionDays < Integer.MAX_VALUE) {
            validDecisionDays++;
            setDirty();
        }
        return validDecisionDays;
    }

    public synchronized void resetValidDecisionDays() {
        if (validDecisionDays != 0) {
            validDecisionDays = 0;
            setDirty();
        }
    }

    public synchronized int validDecisionDays() {
        return validDecisionDays;
    }

    public synchronized long lastProcessedMinecraftDay() {
        return lastProcessedMinecraftDay;
    }

    public synchronized long cooldownEndGameTick() {
        return cooldownEndGameTick;
    }

    public synchronized long cooldownRemaining(long currentGameTick) {
        return Math.max(0L, cooldownEndGameTick - currentGameTick);
    }

    public synchronized boolean isCoolingDown(long currentGameTick) {
        return cooldownRemaining(currentGameTick) > 0L;
    }

    public synchronized void restartCooldown(long currentGameTick, long duration) {
        long safeDuration = Math.max(0L, duration);
        cooldownEndGameTick = currentGameTick > Long.MAX_VALUE - safeDuration
                ? Long.MAX_VALUE
                : currentGameTick + safeDuration;
        setDirty();
    }

    public synchronized boolean effectiveEnabled(TreacherySettings settings) {
        return enabledOverride == null ? settings.enabled() : enabledOverride;
    }

    public synchronized Optional<Boolean> enabledOverride() {
        return Optional.ofNullable(enabledOverride);
    }

    public synchronized void setEnabledOverride(boolean enabled) {
        enabledOverride = enabled;
        setDirty();
    }

    public synchronized EncounterSettings effectiveSettings(
            IllagerTreacheryEncounter encounter) {
        EncounterOverride override = encounterOverrides.get(encounter.id());
        boolean enabled = override != null && override.enabled() != null
                ? override.enabled()
                : encounter.defaultEnabled();
        long weight = override != null && override.weight() != null
                ? override.weight()
                : Math.max(0L, encounter.defaultWeight());
        return new EncounterSettings(enabled, weight);
    }

    public synchronized void setEncounterEnabled(
            ResourceLocation id, boolean enabled) {
        EncounterOverride previous = encounterOverrides.get(id);
        encounterOverrides.put(id, new EncounterOverride(
                enabled, previous == null ? null : previous.weight()));
        setDirty();
    }

    public synchronized void setEncounterWeight(ResourceLocation id, long weight) {
        if (weight < 0L) {
            throw new IllegalArgumentException("Encounter weight cannot be negative");
        }
        EncounterOverride previous = encounterOverrides.get(id);
        encounterOverrides.put(id, new EncounterOverride(
                previous == null ? null : previous.enabled(), weight));
        setDirty();
    }

    public synchronized int encounterOverrideCount() {
        return encounterOverrides.size();
    }

    private record EncounterOverride(Boolean enabled, Long weight) {
    }
}
