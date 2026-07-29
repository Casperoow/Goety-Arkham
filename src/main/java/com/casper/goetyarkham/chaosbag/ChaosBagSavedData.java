package com.casper.goetyarkham.chaosbag;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public final class ChaosBagSavedData extends SavedData {
    public static final String DATA_NAME = "goetyarkham_chaos_bag";

    private static final String LEVEL = "level";
    private static final String MUTATIONS = "mutations";
    private static final String OPERATION = "operation";
    private static final String TOKEN_KIND = "token_kind";
    private static final String TOKEN_VALUE = "token_value";
    private static final String COUNT = "count";
    private static final String SOURCE = "source";

    private final ChaosBagState state;

    public ChaosBagSavedData() {
        this(new ChaosBagState());
    }

    ChaosBagSavedData(ChaosBagState state) {
        this.state = state;
    }

    public static ChaosBagSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ChaosBagSavedData::load,
                ChaosBagSavedData::new,
                DATA_NAME);
    }

    public static ChaosBagSavedData load(CompoundTag tag) {
        ChaosBagLevel level = ChaosBagLevel.fromName(tag.getString(LEVEL))
                .orElse(ChaosBagLevel.NORMAL);
        List<ChaosBagMutation> mutations = new ArrayList<>();
        ListTag list = tag.getList(MUTATIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            try {
                ChaosBagMutation.Operation operation =
                        ChaosBagMutation.Operation.valueOf(entry.getString(OPERATION));
                ChaosToken.Kind kind =
                        ChaosToken.Kind.valueOf(entry.getString(TOKEN_KIND));
                ChaosToken token = kind == ChaosToken.Kind.NUMBER
                        ? ChaosToken.number(entry.getInt(TOKEN_VALUE))
                        : ChaosToken.named(kind);
                int count = entry.getInt(COUNT);
                ResourceLocation source =
                        ResourceLocation.tryParse(entry.getString(SOURCE));
                if (source == null
                        || count <= 0
                        || count > ChaosBagState.MAX_MUTATION_COUNT) {
                    throw new IllegalArgumentException("invalid source or count");
                }
                mutations.add(new ChaosBagMutation(
                        operation, token, count, source));
            } catch (RuntimeException exception) {
                GoetyArkham.LOGGER.warn(
                        "[chaos_bag] Ignored invalid persisted mutation at index {}",
                        index,
                        exception);
            }
        }
        try {
            return new ChaosBagSavedData(new ChaosBagState(level, mutations));
        } catch (IllegalArgumentException exception) {
            GoetyArkham.LOGGER.error(
                    "[chaos_bag] Persisted state produced an empty bag; "
                            + "restoring normal base configuration",
                    exception);
            return new ChaosBagSavedData();
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        tag.putString(LEVEL, state.level().serializedName());
        ListTag list = new ListTag();
        for (ChaosBagMutation mutation : state.mutations()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(OPERATION, mutation.operation().name());
            entry.putString(TOKEN_KIND, mutation.token().kind().name());
            entry.putInt(TOKEN_VALUE, mutation.token().value());
            entry.putInt(COUNT, mutation.count());
            entry.putString(SOURCE, mutation.source().toString());
            list.add(entry);
        }
        tag.put(MUTATIONS, list);
        return tag;
    }

    public synchronized ChaosBagLevel level() {
        return state.level();
    }

    public synchronized List<ChaosToken> baseTokens() {
        return state.baseTokens();
    }

    public synchronized List<ChaosBagMutation> mutations() {
        return state.mutations();
    }

    public synchronized List<ChaosToken> effectiveTokens() {
        return state.effectiveTokens();
    }

    public synchronized ChaosBagSnapshot snapshot() {
        return state.snapshot();
    }

    public synchronized ChaosBagState.OperationResult setLevel(ChaosBagLevel level) {
        return dirtyIfChanged(state.setLevel(level));
    }

    public synchronized ChaosBagState.OperationResult add(
            ChaosToken token, int count, ResourceLocation source) {
        return dirtyIfChanged(state.add(token, count, source));
    }

    public synchronized ChaosBagState.OperationResult remove(
            ChaosToken token, int count, ResourceLocation source) {
        return dirtyIfChanged(state.remove(token, count, source));
    }

    public synchronized ChaosBagState.OperationResult clearSource(
            ResourceLocation source) {
        return dirtyIfChanged(state.clearSource(source));
    }

    private ChaosBagState.OperationResult dirtyIfChanged(
            ChaosBagState.OperationResult result) {
        if (result.success() && result.changed()) {
            setDirty();
        }
        return result;
    }
}
