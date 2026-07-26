package com.casper.goetyarkham.soul;

import java.util.List;

final class SoulPoolOperations {
    private SoulPoolOperations() {
    }

    static int physicalCurrent(List<? extends SoulStorageHandle> handles) {
        long total = 0L;
        for (SoulStorageHandle handle : handles) {
            total += clamp(handle.getCurrentSoul(), 0, handle.getMaximumSoul());
        }
        return saturatingInt(total);
    }

    static int physicalMaximum(List<? extends SoulStorageHandle> handles) {
        long total = 0L;
        for (SoulStorageHandle handle : handles) {
            total += Math.max(0, handle.getMaximumSoul());
        }
        return saturatingInt(total);
    }

    static int maximum(
            boolean hasContainer, int physicalMaximum, int willpowerContribution) {
        if (!hasContainer) {
            return 0;
        }
        long maximum = (long) physicalMaximum + willpowerContribution;
        return saturatingInt(Math.max(0L, maximum));
    }

    static int current(int physicalCurrent, int virtualReserve, int maximum) {
        long current = (long) physicalCurrent + Math.max(0, virtualReserve);
        return (int) Math.max(0L, Math.min(maximum, current));
    }

    static int virtualCapacity(int physicalMaximum, int maximum) {
        return Math.max(0, maximum - physicalMaximum);
    }

    static MutationResult reconcileVirtualReserve(
            List<? extends SoulStorageHandle> handles,
            int virtualReserve,
            int virtualCapacity) {
        int normalized = Math.max(0, virtualReserve);
        int overflow = Math.max(0, normalized - Math.max(0, virtualCapacity));
        int updatedVirtual = normalized - overflow;

        for (SoulStorageHandle handle : handles) {
            if (overflow <= 0) {
                break;
            }
            int current = clamp(handle.getCurrentSoul(), 0, handle.getMaximumSoul());
            int room = Math.max(0, handle.getMaximumSoul() - current);
            int moved = Math.min(room, overflow);
            if (moved > 0) {
                handle.setCurrentSoul(current + moved);
                overflow -= moved;
            }
        }
        return new MutationResult(updatedVirtual, normalized - updatedVirtual, overflow);
    }

    static MutationResult add(
            List<? extends SoulStorageHandle> handles,
            int virtualReserve,
            int virtualCapacity,
            int maximum,
            int amount) {
        int requested = Math.max(0, amount);
        int before = current(physicalCurrent(handles), virtualReserve, maximum);
        int remaining = Math.min(requested, Math.max(0, maximum - before));

        for (SoulStorageHandle handle : handles) {
            if (remaining <= 0) {
                break;
            }
            int max = Math.max(0, handle.getMaximumSoul());
            int current = clamp(handle.getCurrentSoul(), 0, max);
            int added = Math.min(max - current, remaining);
            if (added > 0) {
                handle.setCurrentSoul(current + added);
                remaining -= added;
            }
        }

        int updatedVirtual = Math.max(0, virtualReserve);
        if (remaining > 0) {
            int room = Math.max(0, virtualCapacity - updatedVirtual);
            int added = Math.min(room, remaining);
            updatedVirtual += added;
            remaining -= added;
        }

        int after = current(physicalCurrent(handles), updatedVirtual, maximum);
        return new MutationResult(updatedVirtual, after - before, remaining);
    }

    static MutationResult remove(
            List<? extends SoulStorageHandle> handles,
            int virtualReserve,
            int maximum,
            int amount) {
        int requested = Math.max(0, amount);
        int before = current(physicalCurrent(handles), virtualReserve, maximum);
        int remaining = Math.min(requested, before);
        int updatedVirtual = Math.max(0, virtualReserve);

        int fromVirtual = Math.min(updatedVirtual, remaining);
        updatedVirtual -= fromVirtual;
        remaining -= fromVirtual;

        for (int index = handles.size() - 1; index >= 0 && remaining > 0; index--) {
            SoulStorageHandle handle = handles.get(index);
            int current = clamp(handle.getCurrentSoul(), 0, handle.getMaximumSoul());
            int removed = Math.min(current, remaining);
            if (removed > 0) {
                handle.setCurrentSoul(current - removed);
                remaining -= removed;
            }
        }

        int after = current(physicalCurrent(handles), updatedVirtual, maximum);
        return new MutationResult(updatedVirtual, before - after, remaining);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(Math.max(minimum, maximum), value));
    }

    private static int saturatingInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    record MutationResult(int virtualReserve, int changedAmount, int remainder) {
    }
}
