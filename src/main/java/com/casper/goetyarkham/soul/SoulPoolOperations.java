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
        return maximum(
                hasContainer, physicalMaximum, willpowerContribution, 0);
    }

    static int maximum(
            boolean hasContainer,
            int physicalMaximum,
            int willpowerContribution,
            int directCapacityModifier) {
        return maximum(
                hasContainer,
                physicalMaximum,
                willpowerContribution,
                directCapacityModifier,
                0);
    }

    /**
     * {@code percentModifier} is applied as a percentage against the sum of
     * every other term (e.g. -60 yields 40% of that sum), not as a further
     * flat addition - see {@link
     * com.casper.goetyarkham.item.HospitalDebtsItem#soulCapacityPercentModifier}.
     * Negative sums are clamped to zero before the percentage is applied so a
     * heavy direct penalty can never flip the result positive again.
     */
    static int maximum(
            boolean hasContainer,
            int physicalMaximum,
            int willpowerContribution,
            int directCapacityModifier,
            int percentModifier) {
        if (!hasContainer) {
            return 0;
        }
        long sum = (long) physicalMaximum
                + willpowerContribution
                + directCapacityModifier;
        double multiplier = Math.max(0.0D, (100.0D + percentModifier) / 100.0D);
        long maximum = Math.round(Math.max(0L, sum) * multiplier);
        return saturatingInt(maximum);
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

    /**
     * Permanently discards soul above an effective maximum. This is distinct
     * from snapshot clamping: discarded soul must not reappear when capacity
     * later recovers.
     */
    static MutationResult truncateToMaximum(
            List<? extends SoulStorageHandle> handles,
            int virtualReserve,
            int maximum) {
        long rawBefore = (long) physicalCurrent(handles)
                + Math.max(0, virtualReserve);
        int excess = saturatingInt(Math.max(0L, rawBefore - Math.max(0, maximum)));
        int remaining = excess;
        int updatedVirtual = Math.max(0, virtualReserve);

        int fromVirtual = Math.min(updatedVirtual, remaining);
        updatedVirtual -= fromVirtual;
        remaining -= fromVirtual;

        for (int index = handles.size() - 1;
             index >= 0 && remaining > 0;
             index--) {
            SoulStorageHandle handle = handles.get(index);
            int current = clamp(
                    handle.getCurrentSoul(), 0, handle.getMaximumSoul());
            int removed = Math.min(current, remaining);
            if (removed > 0) {
                handle.setCurrentSoul(current - removed);
                remaining -= removed;
            }
        }
        return new MutationResult(
                updatedVirtual, excess - remaining, remaining);
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

    /**
     * Removes the full requested amount or leaves every storage handle and the
     * virtual reserve unchanged when the unified pool cannot afford it.
     */
    static MutationResult removeExact(
            List<? extends SoulStorageHandle> handles,
            int virtualReserve,
            int maximum,
            int amount) {
        int requested = Math.max(0, amount);
        int available = current(physicalCurrent(handles), virtualReserve, maximum);
        if (requested == 0 || available < requested) {
            return new MutationResult(
                    Math.max(0, virtualReserve), 0, requested);
        }

        int[] originalSoul = new int[handles.size()];
        for (int index = 0; index < handles.size(); index++) {
            SoulStorageHandle handle = handles.get(index);
            originalSoul[index] = clamp(
                    handle.getCurrentSoul(), 0, handle.getMaximumSoul());
        }

        MutationResult result = remove(
                handles, virtualReserve, maximum, requested);
        if (result.changedAmount() == requested && result.remainder() == 0) {
            return result;
        }

        for (int index = 0; index < handles.size(); index++) {
            handles.get(index).setCurrentSoul(originalSoul[index]);
        }
        return new MutationResult(
                Math.max(0, virtualReserve), 0, requested);
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
