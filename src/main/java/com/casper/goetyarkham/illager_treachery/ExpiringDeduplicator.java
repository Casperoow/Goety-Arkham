package com.casper.goetyarkham.illager_treachery;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExpiringDeduplicator {
    private final int maximumEntries;
    private final long lifetimeTicks;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

    public ExpiringDeduplicator(int maximumEntries, long lifetimeTicks) {
        this.maximumEntries = Math.max(1, maximumEntries);
        this.lifetimeTicks = Math.max(1L, lifetimeTicks);
    }

    public synchronized boolean first(String key, long currentTick) {
        prune(currentTick);
        Long previous = seen.get(key);
        if (previous != null && currentTick - previous <= lifetimeTicks) {
            return false;
        }
        seen.put(key, currentTick);
        while (seen.size() > maximumEntries) {
            Iterator<String> iterator = seen.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    public synchronized int size() {
        return seen.size();
    }

    private void prune(long currentTick) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            long recordedTick = iterator.next().getValue();
            if (currentTick - recordedTick > lifetimeTicks
                    || currentTick < recordedTick) {
                iterator.remove();
            }
        }
    }
}
