package com.casper.goetyarkham.strength;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class StrengthEffectsSelfTest {
    private static final double EPSILON = 1.0E-9D;

    private StrengthEffectsSelfTest() {
    }

    public static void run() {
        assertStrength(0.0D, 0.0D, 50.0D, 1.0D);
        assertStrength(4.0D, 2.0D, 54.0D, 1.08D);
        assertStrength(10.0D, 5.0D, 60.0D, 1.20D);
        assertStrength(50.0D, 25.0D, 100.0D, 2.0D);
        assertStrength(60.0D, 30.0D, 100.0D, 2.70D);

        assertClose(0.0D, StrengthEffects.getCriticalChance(-100.0D), "critical chance lower clamp");
        assertClose(10.0D, StrengthEffects.getOverflowCriticalChance(60.0D), "overflow at strength 60");
        assertTrue(StrengthEffects.isCriticalHit(50.0D, 0.0D),
                "strength 50 crits at the minimum random roll");
        assertTrue(StrengthEffects.isCriticalHit(50.0D, Math.nextDown(1.0D)),
                "strength 50 crits at the maximum Random.nextDouble roll");
        assertFalse(StrengthEffects.isCriticalHit(-50.0D, 0.0D),
                "zero percent critical chance never crits");

        AttributeInstance attackDamage = new AttributeInstance(
                new RangedAttribute("test.attack_damage", 1.0D, -2048.0D, 2048.0D),
                ignored -> {
                }
        );
        StrengthEffects.updateAttackDamageModifier(attackDamage, 5.0D);
        StrengthEffects.updateAttackDamageModifier(attackDamage, 5.0D);
        assertEquals(1, attackDamage.getModifiers().size(), "repeated refresh does not stack");
        assertClose(6.0D, attackDamage.getValue(), "attack damage after repeated refresh");

        StrengthEffects.updateAttackDamageModifier(attackDamage, 30.0D);
        assertEquals(1, attackDamage.getModifiers().size(), "changed refresh replaces modifier");
        assertClose(31.0D, attackDamage.getValue(), "attack damage after changed refresh");
        AttributeModifier modifier = attackDamage.getModifier(StrengthEffects.ATTACK_DAMAGE_MODIFIER_ID);
        if (modifier == null || modifier.getOperation() != AttributeModifier.Operation.ADDITION) {
            throw new AssertionError("strength modifier must use ADDITION");
        }

        StrengthEffects.updateAttackDamageModifier(attackDamage, 0.0D);
        assertEquals(0, attackDamage.getModifiers().size(), "zero strength removes modifier");
        assertClose(1.0D, attackDamage.getValue(), "zero strength restores base attack damage");
    }

    private static void assertStrength(
            double strength,
            double attackBonus,
            double criticalChance,
            double criticalMultiplier) {
        assertClose(attackBonus, StrengthEffects.getAttackDamageBonus(strength),
                "attack bonus at strength " + strength);
        assertClose(criticalChance, StrengthEffects.getCriticalChance(strength),
                "critical chance at strength " + strength);
        assertClose(criticalMultiplier, StrengthEffects.getCriticalDamageMultiplier(strength),
                "critical multiplier at strength " + strength);
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean actual, String label) {
        if (!actual) {
            throw new AssertionError(label);
        }
    }

    private static void assertFalse(boolean actual, String label) {
        assertTrue(!actual, label);
    }
}
