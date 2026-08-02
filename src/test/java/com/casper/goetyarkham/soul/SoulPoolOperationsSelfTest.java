package com.casper.goetyarkham.soul;

import java.util.ArrayList;
import java.util.List;

public final class SoulPoolOperationsSelfTest {
    private SoulPoolOperationsSelfTest() {
    }

    public static void run() {
        testSnapshots();
        testEquipAndUnequip();
        testGainOrderAndClamping();
        testConsumptionOrder();
        testExactConsumptionIsAtomic();
        testCapacityDrop();
        testDirectCapacityPenaltyAndTruncation();
        testContainerRequirementAndArca();
        testMergedTotemAndArcaPool();
        testMergedTotemAndArcaMutationOrder();
    }

    private static void testSnapshots() {
        FakeHandle a = new FakeHandle("a", 100, 1000);
        assertPool(List.of(a), 0, 0, true, 100, 1000, "single container");

        FakeHandle b = new FakeHandle("b", 300, 500);
        assertPool(List.of(a, b), 0, 0, true, 400, 1500, "multiple containers");
        assertPool(List.of(a, b), 0, 100, true, 400, 1600, "willpower capacity");
    }

    private static void testEquipAndUnequip() {
        FakeHandle a = new FakeHandle("a", 100, 1000);
        FakeHandle b = new FakeHandle("b", 300, 500);

        assertPool(List.of(a), 0, 0, true, 100, 1000, "before equipping B");
        assertPool(List.of(a, b), 0, 0, true, 400, 1500, "after equipping B");
        assertPool(List.of(a), 0, 0, true, 100, 1000, "after unequipping B");
        assertEquals(300, b.current, "unequipped B retains its souls");
        assertEquals(500, b.maximum, "unequipped B retains its maximum");
        assertPool(List.of(a, b), 0, 0, true, 400, 1500, "B equipped again");
    }

    private static void testGainOrderAndClamping() {
        FakeHandle a = new FakeHandle("a", 100, 1000);
        SoulPoolOperations.MutationResult result = SoulPoolOperations.add(
                List.of(a), 0, 0, 1000, 200);
        assertEquals(300, a.current, "single container gain");
        assertEquals(200, result.changedAmount(), "single container changed amount");

        a.current = 900;
        FakeHandle b = new FakeHandle("b", 0, 500);
        result = SoulPoolOperations.add(List.of(a, b), 0, 0, 1500, 300);
        assertEquals(1000, a.current, "first physical container fills first");
        assertEquals(200, b.current, "gain spills into second physical container");

        a.current = 1000;
        b.current = 500;
        result = SoulPoolOperations.add(List.of(a, b), 0, 100, 1600, 50);
        assertEquals(50, result.virtualReserve(), "virtual reserve fills after physical stores");

        result = SoulPoolOperations.add(
                List.of(a, b), result.virtualReserve(), 100, 1600, 500);
        assertEquals(100, result.virtualReserve(), "virtual reserve clamps to capacity");
        assertEquals(50, result.changedAmount(), "gain above total maximum is discarded");
    }

    private static void testConsumptionOrder() {
        FakeHandle a = new FakeHandle("a", 100, 1000);
        FakeHandle b = new FakeHandle("b", 300, 500);

        SoulPoolOperations.MutationResult result = SoulPoolOperations.remove(
                List.of(a, b), 50, 1600, 40);
        assertEquals(10, result.virtualReserve(), "virtual reserve is consumed first");
        assertEquals(100, a.current, "physical A unchanged while virtual is sufficient");
        assertEquals(300, b.current, "physical B unchanged while virtual is sufficient");

        result = SoulPoolOperations.remove(
                List.of(a, b), result.virtualReserve(), 1600, 100);
        assertEquals(0, result.virtualReserve(), "remaining virtual reserve is consumed");
        assertEquals(100, a.current, "forward physical store remains untouched");
        assertEquals(210, b.current, "reverse-order physical store is consumed first");

        result = SoulPoolOperations.remove(List.of(a, b), 0, 1500, 1000);
        assertEquals(0, b.current, "second physical store never becomes negative");
        assertEquals(0, a.current, "first physical store never becomes negative");
        assertEquals(310, result.changedAmount(), "removal clamps to available souls");
    }

    private static void testExactConsumptionIsAtomic() {
        FakeHandle a = new FakeHandle("a", 400, 1000);
        FakeHandle b = new FakeHandle("b", 599, 1000);

        SoulPoolOperations.MutationResult result = SoulPoolOperations.removeExact(
                List.of(a, b), 0, 2000, 1000);
        assertEquals(0, result.changedAmount(),
                "underfunded exact payment changes nothing");
        assertEquals(400, a.current,
                "underfunded exact payment preserves first container");
        assertEquals(599, b.current,
                "underfunded exact payment preserves second container");

        b.current = 600;
        result = SoulPoolOperations.removeExact(
                List.of(a, b), 0, 2000, 1000);
        assertEquals(1000, result.changedAmount(),
                "exact payment removes the full fixed cost");
        assertEquals(0, a.current,
                "exact payment spills through the unified removal order");
        assertEquals(0, b.current,
                "exact payment consumes the reverse-order container first");

        a.current = 500;
        b.current = 0;
        result = SoulPoolOperations.removeExact(
                List.of(a, b), 500, 2500, 1000);
        assertEquals(1000, result.changedAmount(),
                "virtual and physical soul combine for exact payment");
        assertEquals(0, result.virtualReserve(),
                "exact payment consumes virtual reserve first");
        assertEquals(0, a.current,
                "exact payment consumes the remaining physical soul");

        RejectingHandle rejecting = new RejectingHandle("rejecting", 500, 500);
        FakeHandle mutable = new FakeHandle("mutable", 500, 500);
        result = SoulPoolOperations.removeExact(
                List.of(rejecting, mutable), 0, 1000, 1000);
        assertEquals(0, result.changedAmount(),
                "failed exact payment reports no charge");
        assertEquals(500, rejecting.getCurrentSoul(),
                "failed exact payment preserves rejecting container");
        assertEquals(500, mutable.current,
                "failed exact payment rolls back prior container changes");
    }

    private static void testCapacityDrop() {
        FakeHandle a = new FakeHandle("a", 900, 1000);
        SoulPoolOperations.MutationResult result =
                SoulPoolOperations.reconcileVirtualReserve(List.of(a), 100, 0);
        assertEquals(1000, a.current, "virtual overflow migrates into physical room");
        assertEquals(0, result.virtualReserve(), "virtual reserve clamps after willpower drop");
        assertEquals(0, result.remainder(), "all virtual overflow was migrated");

        a.current = 980;
        result = SoulPoolOperations.reconcileVirtualReserve(List.of(a), 100, 0);
        assertEquals(1000, a.current, "available physical room is filled");
        assertEquals(0, result.virtualReserve(), "virtual reserve is removed");
        assertEquals(80, result.remainder(), "unmigrated overflow is discarded");
    }

    private static void testDirectCapacityPenaltyAndTruncation() {
        FakeHandle a = new FakeHandle("a", 900, 1000);
        FakeHandle b = new FakeHandle("b", 600, 1000);
        int maximum = SoulPoolOperations.maximum(
                true, 2000, 40, -1000);
        assertEquals(1040, maximum, "direct capacity penalty");

        SoulPoolOperations.MutationResult result =
                SoulPoolOperations.truncateToMaximum(
                        List.of(a, b), 100, maximum);
        assertEquals(0, result.virtualReserve(), "truncation consumes virtual first");
        assertEquals(140, b.current, "truncation consumes reverse physical order");
        assertEquals(900, a.current, "truncation preserves earlier store");
        assertEquals(560, result.changedAmount(), "truncated amount");

        result = SoulPoolOperations.remove(
                List.of(a, b), result.virtualReserve(), maximum, 5000);
        assertEquals(0, a.current, "soul removal lower bound A");
        assertEquals(0, b.current, "soul removal lower bound B");
    }

    private static void testContainerRequirementAndArca() {
        int withoutContainer = SoulPoolOperations.maximum(false, 0, 100);
        assertEquals(0, withoutContainer,
                "positive willpower cannot create a pool without a container");
        assertEquals(0, SoulPoolOperations.maximum(true, 10, -20),
                "negative willpower cannot reduce maximum below zero");

        FakeHandle arca = new FakeHandle("arca", 250, 1000);
        assertPool(List.of(arca), 0, 100, true, 250, 1100,
                "Arca physical capacity plus willpower");
    }

    private static void testMergedTotemAndArcaPool() {
        FakeHandle totem = new FakeHandle("totem", 100, 200);
        FakeHandle arca = new FakeHandle("arca", 0, 100_000);

        assertPool(List.of(totem, arca), 0, 0, true, 100, 100_200,
                "empty Arca adds capacity to the Totem pool");

        arca.current = 500;
        assertPool(List.of(totem, arca), 0, 0, true, 600, 100_200,
                "Arca and Totem souls are summed");

        assertPool(List.of(totem, arca), 25, 25, true, 625, 100_225,
                "merged physical stores include the virtual reserve");
    }

    private static void testMergedTotemAndArcaMutationOrder() {
        FakeHandle totem = new FakeHandle("totem", 100, 200);
        FakeHandle arca = new FakeHandle("arca", 0, 100_000);

        SoulPoolOperations.MutationResult result = SoulPoolOperations.remove(
                List.of(totem, arca), 0, 100_200, 40);
        assertEquals(0, arca.current,
                "an empty Arca does not block consumption");
        assertEquals(60, totem.current,
                "an empty Arca spills consumption into the Totem");
        assertEquals(40, result.changedAmount(),
                "Totem souls remain usable while a valid Arca is empty");

        totem.current = 100;
        arca.current = 500;
        result = SoulPoolOperations.remove(
                List.of(totem, arca), 0, 100_200, 550);
        assertEquals(0, arca.current,
                "Arca at the end of the list is consumed first");
        assertEquals(50, totem.current,
                "consumption spills from Arca into Totems exactly once");
        assertEquals(550, result.changedAmount(),
                "merged consumption removes the requested total exactly once");

        totem.current = 150;
        arca.current = 0;
        result = SoulPoolOperations.add(
                List.of(totem, arca), 0, 0, 100_200, 100);
        assertEquals(200, totem.current,
                "restoration fills the Totem before the Arca");
        assertEquals(50, arca.current,
                "restoration spills into the Arca after Totems");
        assertEquals(100, result.changedAmount(),
                "merged restoration adds the requested total exactly once");
    }

    private static void assertPool(
            List<FakeHandle> handles,
            int virtualReserve,
            int willpowerContribution,
            boolean hasContainer,
            int expectedCurrent,
            int expectedMaximum,
            String label) {
        List<SoulStorageHandle> storage = new ArrayList<>(handles);
        int physicalMaximum = SoulPoolOperations.physicalMaximum(storage);
        int maximum = SoulPoolOperations.maximum(
                hasContainer, physicalMaximum, willpowerContribution);
        int current = SoulPoolOperations.current(
                SoulPoolOperations.physicalCurrent(storage), virtualReserve, maximum);
        assertEquals(expectedCurrent, current, label + " current");
        assertEquals(expectedMaximum, maximum, label + " maximum");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static final class FakeHandle implements SoulStorageHandle {
        private final String id;
        private int current;
        private final int maximum;

        private FakeHandle(String id, int current, int maximum) {
            this.id = id;
            this.current = current;
            this.maximum = maximum;
        }

        @Override
        public String slotId() {
            return id;
        }

        @Override
        public int getCurrentSoul() {
            return current;
        }

        @Override
        public int getMaximumSoul() {
            return maximum;
        }

        @Override
        public void setCurrentSoul(int amount) {
            current = Math.max(0, Math.min(maximum, amount));
        }
    }

    private static final class RejectingHandle implements SoulStorageHandle {
        private final String id;
        private final int current;
        private final int maximum;

        private RejectingHandle(String id, int current, int maximum) {
            this.id = id;
            this.current = current;
            this.maximum = maximum;
        }

        @Override
        public String slotId() {
            return id;
        }

        @Override
        public int getCurrentSoul() {
            return current;
        }

        @Override
        public int getMaximumSoul() {
            return maximum;
        }

        @Override
        public void setCurrentSoul(int amount) {
            // Simulates a storage backend refusing a mutation.
        }
    }
}
