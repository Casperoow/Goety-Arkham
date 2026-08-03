package com.casper.goetyarkham.loneliness;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class PlayerLonelinessData implements IPlayerLoneliness {
    static final int MAX_LONELINESS = 4;
    private static final int SETTLE_THRESHOLD = MAX_LONELINESS + 1;
    private static final String LONELINESS_KEY = "loneliness";

    private int loneliness;
    private LonelinessSnapshot lastSyncedSnapshot;
    private boolean pendingOverflowSettle;

    @Override
    public int getLoneliness() {
        return loneliness;
    }

    /**
     * Returns true if this increment crossed the settlement threshold. The
     * counter is reset to zero atomically in the same call; callers can never
     * observe it sitting at the threshold value.
     */
    boolean addOne() {
        loneliness++;
        if (loneliness >= SETTLE_THRESHOLD) {
            loneliness = 0;
            return true;
        }
        return false;
    }

    void copyFrom(PlayerLonelinessData other) {
        loneliness = clamp(other.loneliness);
        pendingOverflowSettle = other.pendingOverflowSettle;
        resetTransientCache();
    }

    /**
     * Consumes a forced settlement flagged when loading anomalous saved data
     * (five or more). Idempotent: returns false on every call after the first.
     */
    boolean consumePendingOverflowSettle() {
        if (!pendingOverflowSettle) {
            return false;
        }
        pendingOverflowSettle = false;
        return true;
    }

    boolean needsClientSync(LonelinessSnapshot snapshot) {
        return !snapshot.equals(lastSyncedSnapshot);
    }

    void markClientSynced(LonelinessSnapshot snapshot) {
        lastSyncedSnapshot = snapshot;
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(LONELINESS_KEY, loneliness);
        return tag;
    }

    void deserializeNBT(CompoundTag tag) {
        int raw = tag.contains(LONELINESS_KEY, Tag.TAG_ANY_NUMERIC)
                ? tag.getInt(LONELINESS_KEY)
                : 0;
        if (raw >= SETTLE_THRESHOLD) {
            // Saved data should never reach the threshold; treat it as an
            // unresolved settlement rather than silently discarding it.
            pendingOverflowSettle = true;
            loneliness = MAX_LONELINESS;
        } else {
            loneliness = clamp(raw);
        }
        resetTransientCache();
    }

    private void resetTransientCache() {
        lastSyncedSnapshot = null;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_LONELINESS, value));
    }
}
