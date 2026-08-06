package com.casper.goetyarkham.curios;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every currently-known {@link ResourceSlotProvider}, keyed by its
 * {@linkplain ResourceSlotProvider#providerId() provider ID} so that
 * registering the same provider more than once (e.g. its owning item's
 * class is touched again) never creates a duplicate entry. Registration is
 * expected once per provider, triggered by that provider's own owning class
 * being loaded (see {@code EncyclopediaService}'s static initializer);
 * nothing here is player-specific.
 */
public final class ResourceSlotProviderRegistry {
    private static final Map<String, ResourceSlotProvider> PROVIDERS =
            new ConcurrentHashMap<>();

    private ResourceSlotProviderRegistry() {
    }

    public static void register(ResourceSlotProvider provider) {
        PROVIDERS.put(provider.providerId(), provider);
    }

    /** Test-only escape hatch for registering and later removing throwaway provider stand-ins. */
    public static void unregister(String providerId) {
        PROVIDERS.remove(providerId);
    }

    public static List<ResourceSlotProvider> providers() {
        return List.copyOf(PROVIDERS.values());
    }
}
