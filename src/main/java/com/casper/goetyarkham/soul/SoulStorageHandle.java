package com.casper.goetyarkham.soul;

/**
 * Mutable view of one physical soul store. Implementations must preserve the
 * underlying store's own capacity and identity.
 */
public interface SoulStorageHandle {
    String slotId();

    default String storageIdentity() {
        return slotId();
    }

    int getCurrentSoul();

    int getMaximumSoul();

    void setCurrentSoul(int amount);
}
