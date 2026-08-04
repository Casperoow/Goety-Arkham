package com.casper.goetyarkham.curios;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Deterministic pure-logic tests for {@link FocusSlotExchange}. */
public final class FocusSlotExchangeSelfTest {
    private record FakeFocus(String id, String nbt) {
    }

    private static final FocusSlotExchange.Entry FOCUS_A =
            FocusSlotExchange.Entry.of(new FakeFocus("focus_a", "{power:3}"));
    private static final FocusSlotExchange.Entry FOCUS_B =
            FocusSlotExchange.Entry.of(new FakeFocus("focus_b", "{power:7}"));
    private static final FocusSlotExchange.Entry FOCUS_C =
            FocusSlotExchange.Entry.of(new FakeFocus("focus_c", "{power:9}"));
    private static final FocusSlotExchange.Entry NOT_A_FOCUS =
            FocusSlotExchange.Entry.of("plain_item");

    private static final Predicate<FocusSlotExchange.Entry> ELIGIBLE =
            entry -> entry.empty() || entry.payload() instanceof FakeFocus;

    private FocusSlotExchangeSelfTest() {
    }

    public static void main(String[] args) {
        testEmptyWandExtractsSlotFocus();
        testSwapWhenWandAlreadyHoldsFocus();
        testStoreIntoFirstEmptySlot();
        testStoreIntoSecondSlotWhenFirstOccupied();
        testStoreRejectedWhenBothSlotsFull();
        testSwapRejectedOnIllegalIndex();
        testSwapRejectedForNonFocusPayload();
        testNbtPayloadPreservedAcrossSwap();
        testFailureLeavesInputUntouchedNoDuplicationOrLoss();
        System.out.println("FocusSlotExchangeSelfTest: all checks passed");
    }

    private static void testEmptyWandExtractsSlotFocus() {
        List<FocusSlotExchange.Entry> slots = List.of(FOCUS_A, FocusSlotExchange.Entry.EMPTY);
        FocusSlotExchange.Result result =
                FocusSlotExchange.swap(slots, 0, FocusSlotExchange.Entry.EMPTY, ELIGIBLE);
        assertTrue(result.success(), "empty-wand extract should succeed");
        assertEquals(FocusSlotExchange.Entry.EMPTY, result.slots().get(0), "slot becomes empty");
        assertEquals(FOCUS_A, result.wandFocus(), "wand receives the slot's focus");
    }

    private static void testSwapWhenWandAlreadyHoldsFocus() {
        List<FocusSlotExchange.Entry> slots = List.of(FOCUS_A, FocusSlotExchange.Entry.EMPTY);
        FocusSlotExchange.Result result = FocusSlotExchange.swap(slots, 0, FOCUS_B, ELIGIBLE);
        assertTrue(result.success(), "full swap should succeed");
        assertEquals(FOCUS_B, result.slots().get(0), "slot now holds the previous wand focus");
        assertEquals(FOCUS_A, result.wandFocus(), "wand now holds the previous slot focus");
        assertEquals(FocusSlotExchange.Entry.EMPTY, result.slots().get(1), "other slot untouched");
    }

    private static void testStoreIntoFirstEmptySlot() {
        List<FocusSlotExchange.Entry> slots =
                List.of(FocusSlotExchange.Entry.EMPTY, FocusSlotExchange.Entry.EMPTY);
        FocusSlotExchange.Result result = FocusSlotExchange.store(slots, FOCUS_A, ELIGIBLE);
        assertTrue(result.success(), "store should succeed with an empty slot available");
        assertEquals(0, result.slotIndex(), "store targets slot 0 first");
        assertEquals(FOCUS_A, result.slots().get(0), "slot 0 now holds the stored focus");
        assertEquals(FocusSlotExchange.Entry.EMPTY, result.wandFocus(), "wand is emptied");
    }

    private static void testStoreIntoSecondSlotWhenFirstOccupied() {
        List<FocusSlotExchange.Entry> slots = List.of(FOCUS_B, FocusSlotExchange.Entry.EMPTY);
        FocusSlotExchange.Result result = FocusSlotExchange.store(slots, FOCUS_A, ELIGIBLE);
        assertTrue(result.success(), "store should skip the occupied slot 0");
        assertEquals(1, result.slotIndex(), "store targets slot 1");
        assertEquals(FOCUS_B, result.slots().get(0), "slot 0 is untouched");
        assertEquals(FOCUS_A, result.slots().get(1), "slot 1 now holds the stored focus");
    }

    private static void testStoreRejectedWhenBothSlotsFull() {
        List<FocusSlotExchange.Entry> slots = List.of(FOCUS_A, FOCUS_B);
        FocusSlotExchange.Result result = FocusSlotExchange.store(slots, FOCUS_C, ELIGIBLE);
        assertTrue(!result.success(), "store should be rejected when no slot is empty");
        assertEquals(slots, result.slots(), "rejected store leaves slots unchanged");
    }

    private static void testSwapRejectedOnIllegalIndex() {
        List<FocusSlotExchange.Entry> slots = List.of(FOCUS_A, FocusSlotExchange.Entry.EMPTY);
        assertTrue(!FocusSlotExchange.swap(slots, -1, FOCUS_B, ELIGIBLE).success(),
                "negative index rejected");
        assertTrue(!FocusSlotExchange.swap(slots, 2, FOCUS_B, ELIGIBLE).success(),
                "out-of-range index rejected");
    }

    private static void testSwapRejectedForNonFocusPayload() {
        List<FocusSlotExchange.Entry> slots = List.of(FOCUS_A, FocusSlotExchange.Entry.EMPTY);
        FocusSlotExchange.Result wandRejected =
                FocusSlotExchange.swap(slots, 1, NOT_A_FOCUS, ELIGIBLE);
        assertTrue(!wandRejected.success(), "non-focus wand payload rejected");

        List<FocusSlotExchange.Entry> slotsWithJunk =
                List.of(NOT_A_FOCUS, FocusSlotExchange.Entry.EMPTY);
        FocusSlotExchange.Result slotRejected = FocusSlotExchange.swap(slotsWithJunk, 0, FOCUS_B, ELIGIBLE);
        assertTrue(!slotRejected.success(), "non-focus slot payload rejected");
    }

    private static void testNbtPayloadPreservedAcrossSwap() {
        FakeFocus original = new FakeFocus("focus_nbt", "{enchant:[sharpness=3],durability:12}");
        FocusSlotExchange.Entry entry = FocusSlotExchange.Entry.of(original);
        List<FocusSlotExchange.Entry> slots = List.of(entry, FocusSlotExchange.Entry.EMPTY);
        FocusSlotExchange.Result result =
                FocusSlotExchange.swap(slots, 0, FocusSlotExchange.Entry.EMPTY, ELIGIBLE);
        FakeFocus roundTripped = (FakeFocus) result.wandFocus().payload();
        assertEquals(original, roundTripped, "NBT-bearing payload preserved exactly");
        assertTrue(original == roundTripped, "payload is passed through, never copied or reconstructed");
    }

    private static void testFailureLeavesInputUntouchedNoDuplicationOrLoss() {
        List<FocusSlotExchange.Entry> slots = List.of(FOCUS_A, FOCUS_B);
        List<FocusSlotExchange.Entry> beforeSnapshot = List.copyOf(slots);
        FocusSlotExchange.Result result = FocusSlotExchange.swap(slots, 9, FOCUS_C, ELIGIBLE);
        assertTrue(!result.success(), "invalid swap fails");
        assertEquals(beforeSnapshot, slots, "original slot list reference untouched");
        assertEquals(beforeSnapshot, result.slots(), "failure result mirrors input, no phantom item");
        assertEquals(FOCUS_C, result.wandFocus(), "wand focus reported unchanged on failure");
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
