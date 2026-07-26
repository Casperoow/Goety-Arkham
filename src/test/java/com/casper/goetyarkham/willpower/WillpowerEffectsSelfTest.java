package com.casper.goetyarkham.willpower;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class WillpowerEffectsSelfTest {
    private WillpowerEffectsSelfTest() {
    }

    public static void run() {
        assertWillpower(-2, -4.0D, -20);
        assertWillpower(0, 0.0D, 0);
        assertWillpower(1, 2.0D, 10);
        assertWillpower(10, 20.0D, 100);

        AttributeInstance potency = new AttributeInstance(
                new RangedAttribute("test.spell_potency", 100.0D, 0.0D, 2048.0D),
                ignored -> {
                }
        );
        WillpowerEffects.updateSpellPotencyModifier(potency, 10);
        assertClose(120.0D, potency.getValue(), "willpower potency modifier");
        WillpowerEffects.updateSpellPotencyModifier(potency, 10);
        assertClose(120.0D, potency.getValue(), "repeated refresh does not stack potency");
        WillpowerEffects.updateSpellPotencyModifier(potency, -2);
        assertClose(96.0D, potency.getValue(), "negative willpower lowers potency");
        WillpowerEffects.updateSpellPotencyModifier(potency, 0);
        assertClose(100.0D, potency.getValue(), "zero willpower removes potency modifier");
    }

    private static void assertWillpower(
            int willpower, double expectedPotency, int expectedCapacity) {
        double potency = WillpowerEffects.calculateSpellPotencyContribution(willpower);
        int capacity = WillpowerEffects.calculateSoulCapacityContribution(willpower);
        if (Double.compare(expectedPotency, potency) != 0) {
            throw new AssertionError(
                    "potency at willpower " + willpower
                            + ": expected=" + expectedPotency + ", actual=" + potency);
        }
        if (expectedCapacity != capacity) {
            throw new AssertionError(
                    "capacity at willpower " + willpower
                            + ": expected=" + expectedCapacity + ", actual=" + capacity);
        }
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1.0E-9D) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
