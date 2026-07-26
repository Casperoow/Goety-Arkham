package com.casper.goetyarkham.soul;

import net.minecraft.nbt.CompoundTag;

public final class PlayerSoulPoolData {
    private int virtualSoulReserve;
    private long lastContainerSignature = Long.MIN_VALUE;
    private SoulPoolSnapshot lastSnapshot;
    private SoulPoolSnapshot lastSyncedSnapshot;

    public int getVirtualSoulReserve() {
        return virtualSoulReserve;
    }

    public void setVirtualSoulReserve(int virtualSoulReserve) {
        this.virtualSoulReserve = Math.max(0, virtualSoulReserve);
    }

    long getLastContainerSignature() {
        return lastContainerSignature;
    }

    SoulPoolSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    void updateCache(long signature, SoulPoolSnapshot snapshot) {
        lastContainerSignature = signature;
        lastSnapshot = snapshot;
    }

    boolean needsClientSync(SoulPoolSnapshot snapshot) {
        return !snapshot.equals(lastSyncedSnapshot);
    }

    void markClientSynced(SoulPoolSnapshot snapshot) {
        lastSyncedSnapshot = snapshot;
    }

    public void copyFrom(PlayerSoulPoolData other) {
        setVirtualSoulReserve(other.virtualSoulReserve);
        resetTransientCache();
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("virtualSoulReserve", virtualSoulReserve);
        return tag;
    }

    void deserializeNBT(CompoundTag tag) {
        setVirtualSoulReserve(tag.getInt("virtualSoulReserve"));
        resetTransientCache();
    }

    private void resetTransientCache() {
        lastContainerSignature = Long.MIN_VALUE;
        lastSnapshot = null;
        lastSyncedSnapshot = null;
    }
}
