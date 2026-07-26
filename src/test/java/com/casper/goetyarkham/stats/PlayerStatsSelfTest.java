package com.casper.goetyarkham.stats;

import com.casper.goetyarkham.command.StatsCommandSelfTest;
import com.casper.goetyarkham.strength.StrengthEffectsSelfTest;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free verification entry point for the data model. Run with:
 * gradlew.bat statsSelfTest
 */
public final class PlayerStatsSelfTest {
    private PlayerStatsSelfTest() {
    }

    public static void main(String[] args) {
        PlayerStats stats = new PlayerStats();
        AtomicInteger changes = new AtomicInteger();
        stats.setOnChanged(changes::incrementAndGet);

        for (StatType type : StatType.values()) {
            assertEquals(0, stats.get(type).base(), type + " initial base");
            assertEquals(0, stats.get(type).equipment(), type + " initial equipment");
            assertEquals(0, stats.get(type).temporary(), type + " initial temporary");
            assertEquals(0, stats.get(type).derived(), type + " initial derived");
            assertEquals(0, stats.get(type).finalValue(), type + " initial final");
        }

        assertTrue(stats.setBase(StatType.STRENGTH, 10), "set reports a change");
        assertEquals(10, stats.get(StatType.STRENGTH).base(), "set base");
        assertFalse(stats.setBase(StatType.STRENGTH, 10), "same value is not a change");
        assertEquals(1, changes.get(), "unchanged values do not notify");

        assertTrue(stats.addBase(StatType.STRENGTH, -3), "add reports a change");
        assertEquals(7, stats.get(StatType.STRENGTH).base(), "add base");
        stats.setEquipment(StatType.STRENGTH, 2);
        stats.setTemporary(StatType.STRENGTH, -1);
        stats.setDerived(StatType.STRENGTH, 4);
        assertEquals(12, stats.get(StatType.STRENGTH).finalValue(), "final formula");

        stats.setBase(StatType.AGILITY, 2000);
        assertEquals(1000, stats.get(StatType.AGILITY).base(), "upper clamp");
        stats.setBase(StatType.WILLPOWER, -2000);
        assertEquals(-1000, stats.get(StatType.WILLPOWER).base(), "lower clamp");
        stats.setBase(StatType.AGILITY, 950);
        stats.addBase(StatType.AGILITY, 100);
        assertEquals(1000, stats.get(StatType.AGILITY).base(), "add upper clamp");

        PlayerStats loaded = new PlayerStats();
        loaded.deserializeNBT(stats.serializeNBT());
        assertEquals(stats.snapshot(), loaded.snapshot(), "NBT round trip");

        PlayerStats cloned = new PlayerStats();
        cloned.copyFrom(loaded);
        assertEquals(loaded.snapshot(), cloned.snapshot(), "clone copy");

        assertTrue(cloned.reset(), "reset reports a change");
        for (StatType type : StatType.values()) {
            assertEquals(0, cloned.get(type).finalValue(), type + " reset final");
        }
        assertFalse(cloned.reset(), "second reset is unchanged");

        for (String name : new String[]{"strength", "agility", "willpower", "intellect"}) {
            assertTrue(StatType.fromName(name).isPresent(), "command stat name " + name);
        }

        assertTrue(
                PlayerStatsService.setBase(Optional.empty(), StatType.STRENGTH, 1).isEmpty(),
                "set fails cleanly when capability is missing"
        );
        assertTrue(
                PlayerStatsService.addBase(Optional.empty(), StatType.AGILITY, 1).isEmpty(),
                "add fails cleanly when capability is missing"
        );
        assertTrue(
                PlayerStatsService.reset(Optional.empty()).isEmpty(),
                "reset fails cleanly when capability is missing"
        );
        StatsCommandSelfTest.run();
        StrengthEffectsSelfTest.run();

        System.out.println("PlayerStatsSelfTest: all checks passed");
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertFalse(boolean value, String label) {
        assertTrue(!value, label);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
