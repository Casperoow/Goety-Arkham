package com.casper.goetyarkham.curios;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Pure focus-slot exchange rules, decoupled from Curios and ItemStack so they
 * can be exercised deterministically without a running game instance. The
 * real Curios/ItemStack integration lives in {@link FocusCurioService}, which
 * converts real slot contents to/from {@link Entry} and applies only
 * successful results back to the live handlers.
 */
public final class FocusSlotExchange {

    /** A single slot's (or the wand's) content, opaque to this class. */
    public record Entry(boolean empty, Object payload) {
        public static final Entry EMPTY = new Entry(true, null);

        public static Entry of(Object payload) {
            return payload == null ? EMPTY : new Entry(false, payload);
        }
    }

    /**
     * @param slotIndex the slot touched by a successful {@link #store}, or
     *                   the requested index for {@link #swap}; {@code -1} on failure.
     */
    public record Result(boolean success, List<Entry> slots, Entry wandFocus, int slotIndex) {
        static Result rejected(List<Entry> slots, Entry wandFocus) {
            return new Result(false, List.copyOf(slots), wandFocus, -1);
        }
    }

    private FocusSlotExchange() {
    }

    public static OptionalInt findFirstEmpty(List<Entry> slots) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).empty()) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public static boolean hasAny(List<Entry> slots) {
        return slots.stream().anyMatch(entry -> !entry.empty());
    }

    public static boolean hasEmpty(List<Entry> slots) {
        return slots.stream().anyMatch(Entry::empty);
    }

    /**
     * Swaps the wand's current focus with the focus in {@code slotIndex}.
     * Handles both directions uniformly: an empty wand simply extracts the
     * slot's focus, and an empty slot simply receives the wand's focus.
     */
    public static Result swap(List<Entry> slots, int slotIndex, Entry wandFocus, Predicate<Entry> eligible) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return Result.rejected(slots, wandFocus);
        }
        Entry slotFocus = slots.get(slotIndex);
        if (!eligible.test(slotFocus) || !eligible.test(wandFocus)) {
            return Result.rejected(slots, wandFocus);
        }
        List<Entry> updated = new ArrayList<>(slots);
        updated.set(slotIndex, wandFocus);
        return new Result(true, updated, slotFocus, slotIndex);
    }

    /** Stores the wand's current focus into the lowest-numbered empty slot. */
    public static Result store(List<Entry> slots, Entry wandFocus, Predicate<Entry> eligible) {
        if (wandFocus.empty() || !eligible.test(wandFocus)) {
            return Result.rejected(slots, wandFocus);
        }
        OptionalInt target = findFirstEmpty(slots);
        if (target.isEmpty()) {
            return Result.rejected(slots, wandFocus);
        }
        List<Entry> updated = new ArrayList<>(slots);
        updated.set(target.getAsInt(), wandFocus);
        return new Result(true, updated, Entry.EMPTY, target.getAsInt());
    }
}
