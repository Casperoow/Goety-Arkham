package com.casper.goetyarkham.sanity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class PlayerSanityData implements IPlayerSanity {
    private static final String CURRENT_KEY = "currentSanity";
    private static final String PERMANENT_LOSS_KEY = "permanentMaxLoss";
    private static final String COLLAPSE_ACTIVE_KEY = "collapseActive";
    private static final String COLLAPSE_COUNTER_KEY = "collapseTickCounter";

    private int currentSanity = SanityConstants.BASE_MAXIMUM;
    private int permanentMaxLoss;
    private boolean collapseActive;
    private int collapseTickCounter;

    private SanitySnapshot lastSyncedSnapshot;
    private int lastObservedMaximum = -1;
    private boolean collapseDeathRequested;
    private boolean cloneDeathSettled;
    private boolean pendingCollapseRespawnRefill;

    @Override
    public int getCurrentSanity() {
        return currentSanity;
    }

    @Override
    public int getPermanentMaxLoss() {
        return permanentMaxLoss;
    }

    @Override
    public boolean isCollapseActive() {
        return collapseActive;
    }

    @Override
    public int getCollapseTickCounter() {
        return collapseTickCounter;
    }

    SanityTransition setCurrentSanity(int value, int maximum) {
        int previous = currentSanity;
        boolean previouslyActive = collapseActive;
        currentSanity = SanityMath.clampCurrent(value, maximum);

        boolean entered = previous > 0 && currentSanity == 0 && !collapseActive;
        boolean exited = previous == 0 && currentSanity > 0 && collapseActive;
        if (entered) {
            collapseActive = true;
            collapseTickCounter = 0;
            collapseDeathRequested = false;
        } else if (currentSanity > 0) {
            collapseActive = false;
            collapseTickCounter = 0;
            collapseDeathRequested = false;
        } else if (!collapseActive) {
            // Old or manually edited data at zero is already an active collapse,
            // but loading/normalization must never count as a fresh activation.
            collapseActive = true;
        }
        return new SanityTransition(
                previous,
                currentSanity,
                entered,
                exited || (previouslyActive && !collapseActive));
    }

    int setPermanentMaxLoss(int value) {
        int previous = permanentMaxLoss;
        permanentMaxLoss = SanityMath.clampPermanentLoss(value);
        return permanentMaxLoss - previous;
    }

    boolean clampToMaximum(int maximum) {
        return setCurrentSanity(currentSanity, maximum).changed();
    }

    boolean advanceCollapseTick() {
        if (!collapseActive || currentSanity > 0) {
            collapseTickCounter = 0;
            return false;
        }
        collapseTickCounter++;
        if (collapseTickCounter < SanityConstants.COLLAPSE_INTERVAL_TICKS) {
            return false;
        }
        collapseTickCounter = 0;
        return true;
    }

    void resetCollapseAfterDeath() {
        collapseActive = false;
        collapseTickCounter = 0;
        collapseDeathRequested = false;
    }

    boolean markCollapseDeathRequested() {
        if (collapseDeathRequested) {
            return false;
        }
        collapseDeathRequested = true;
        return true;
    }

    boolean beginCloneDeathSettlement() {
        if (cloneDeathSettled) {
            return false;
        }
        cloneDeathSettled = true;
        return true;
    }

    boolean isCloneDeathSettled() {
        return cloneDeathSettled;
    }

    void markPendingCollapseRespawnRefill() {
        pendingCollapseRespawnRefill = true;
    }

    boolean consumePendingCollapseRespawnRefill() {
        if (!pendingCollapseRespawnRefill) {
            return false;
        }
        pendingCollapseRespawnRefill = false;
        return true;
    }

    int getLastObservedMaximum() {
        return lastObservedMaximum;
    }

    void setLastObservedMaximum(int maximum) {
        lastObservedMaximum = maximum;
    }

    boolean needsClientSync(SanitySnapshot snapshot) {
        return !snapshot.equals(lastSyncedSnapshot);
    }

    void markClientSynced(SanitySnapshot snapshot) {
        lastSyncedSnapshot = snapshot;
    }

    void copyFrom(PlayerSanityData other, int maximum) {
        boolean alreadySettled = cloneDeathSettled;
        currentSanity = other.currentSanity;
        permanentMaxLoss = SanityMath.clampPermanentLoss(other.permanentMaxLoss);
        collapseActive = other.collapseActive;
        collapseTickCounter = normalizeCounter(other.collapseTickCounter);
        resetTransientCache();
        cloneDeathSettled = alreadySettled;
        normalize(maximum);
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(CURRENT_KEY, currentSanity);
        tag.putInt(PERMANENT_LOSS_KEY, permanentMaxLoss);
        tag.putBoolean(COLLAPSE_ACTIVE_KEY, collapseActive);
        tag.putInt(COLLAPSE_COUNTER_KEY, collapseTickCounter);
        return tag;
    }

    void deserializeNBT(CompoundTag tag, int maximum) {
        currentSanity = tag.contains(CURRENT_KEY, Tag.TAG_ANY_NUMERIC)
                ? tag.getInt(CURRENT_KEY)
                : SanityConstants.BASE_MAXIMUM;
        permanentMaxLoss = SanityMath.clampPermanentLoss(
                tag.getInt(PERMANENT_LOSS_KEY));
        collapseActive = tag.getBoolean(COLLAPSE_ACTIVE_KEY);
        collapseTickCounter = normalizeCounter(tag.getInt(COLLAPSE_COUNTER_KEY));
        resetTransientCache();
        normalize(maximum);
    }

    private void normalize(int maximum) {
        currentSanity = SanityMath.clampCurrent(currentSanity, maximum);
        permanentMaxLoss = SanityMath.clampPermanentLoss(permanentMaxLoss);
        collapseTickCounter = normalizeCounter(collapseTickCounter);
        if (currentSanity == 0) {
            // Preserve progress and treat saved zero sanity as an existing
            // collapse. This prevents a login-time weakness reactivation.
            collapseActive = true;
        } else {
            collapseActive = false;
            collapseTickCounter = 0;
        }
    }

    private void resetTransientCache() {
        lastSyncedSnapshot = null;
        lastObservedMaximum = -1;
        collapseDeathRequested = false;
    }

    private static int normalizeCounter(int counter) {
        return Math.max(0, Math.min(
                SanityConstants.COLLAPSE_INTERVAL_TICKS - 1, counter));
    }

    record SanityTransition(
            int previous,
            int current,
            boolean enteredCollapse,
            boolean exitedCollapse) {
        boolean changed() {
            return previous != current;
        }
    }
}
