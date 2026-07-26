package com.casper.goetyarkham.willpower;

import com.casper.goetyarkham.revelation.RevelationAttributeBridge;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.util.UUID;

public final class WillpowerEffectsSelfTest {
    private static final UUID THIRD_PARTY_MODIFIER_ID =
            UUID.fromString("6a66bd41-6630-43ab-a378-1c17e96195a9");

    private WillpowerEffectsSelfTest() {
    }

    public static void run() {
        assertWillpower(-2, -4.0D, -20);
        assertWillpower(0, 0.0D, 0);
        assertWillpower(1, 2.0D, 10);
        assertWillpower(10, 20.0D, 100);

        assertIntellect(-2, -0.02D);
        assertIntellect(0, 0.0D);
        assertIntellect(1, 0.01D);
        assertIntellect(10, 0.10D);

        AttributeInstance spellPower = new AttributeInstance(
                new RangedAttribute("test.spell_power", 0.0D, -32767.0D, 32767.0D),
                ignored -> {
                }
        );
        addThirdPartyModifier(spellPower, 7.0D);
        RevelationAttributeBridge.updateSpellPowerModifier(spellPower, 10);
        assertClose(27.0D, spellPower.getValue(), "willpower spell power modifier");
        RevelationAttributeBridge.updateSpellPowerModifier(spellPower, 10);
        assertClose(27.0D, spellPower.getValue(),
                "repeated refresh does not stack spell power");
        assertThirdPartyModifierPreserved(spellPower);
        RevelationAttributeBridge.updateSpellPowerModifier(spellPower, -2);
        assertClose(3.0D, spellPower.getValue(), "negative willpower lowers spell power");
        RevelationAttributeBridge.updateSpellPowerModifier(spellPower, 0);
        assertClose(7.0D, spellPower.getValue(),
                "zero willpower removes only the bridge spell power modifier");
        assertThirdPartyModifierPreserved(spellPower);

        AttributeInstance multiplier = new AttributeInstance(
                new RangedAttribute(
                        "test.spell_power_multiplier", 1.0D, 0.0D, 32767.0D),
                ignored -> {
                }
        );
        addThirdPartyModifier(multiplier, 0.25D);
        RevelationAttributeBridge.updateSpellPowerMultiplierModifier(multiplier, 10);
        assertClose(1.35D, multiplier.getValue(), "intellect multiplier modifier");
        RevelationAttributeBridge.updateSpellPowerMultiplierModifier(multiplier, 10);
        assertClose(1.35D, multiplier.getValue(),
                "repeated refresh does not stack multiplier");
        assertThirdPartyModifierPreserved(multiplier);
        RevelationAttributeBridge.updateSpellPowerMultiplierModifier(multiplier, 0);
        assertClose(1.25D, multiplier.getValue(),
                "zero intellect removes only the bridge multiplier modifier");
        assertThirdPartyModifierPreserved(multiplier);
    }

    private static void assertWillpower(
            int willpower, double expectedSpellPower, int expectedCapacity) {
        double spellPower =
                RevelationAttributeBridge.calculateSpellPowerContribution(willpower);
        int capacity = WillpowerEffects.calculateSoulCapacityContribution(willpower);
        if (Double.compare(expectedSpellPower, spellPower) != 0) {
            throw new AssertionError(
                    "spell power at willpower " + willpower
                            + ": expected=" + expectedSpellPower
                            + ", actual=" + spellPower);
        }
        if (expectedCapacity != capacity) {
            throw new AssertionError(
                    "capacity at willpower " + willpower
                            + ": expected=" + expectedCapacity + ", actual=" + capacity);
        }
    }

    private static void assertIntellect(
            int intellect, double expectedMultiplierContribution) {
        double contribution =
                RevelationAttributeBridge.calculateSpellPowerMultiplierContribution(intellect);
        assertClose(expectedMultiplierContribution, contribution,
                "multiplier contribution at intellect " + intellect);
    }

    private static void addThirdPartyModifier(
            AttributeInstance attribute, double amount) {
        attribute.addTransientModifier(new AttributeModifier(
                THIRD_PARTY_MODIFIER_ID,
                "third_party_modifier",
                amount,
                AttributeModifier.Operation.ADDITION
        ));
    }

    private static void assertThirdPartyModifierPreserved(AttributeInstance attribute) {
        if (attribute.getModifier(THIRD_PARTY_MODIFIER_ID) == null) {
            throw new AssertionError("bridge removed a third-party modifier");
        }
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1.0E-9D) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
