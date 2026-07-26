package com.casper.goetyarkham.agility;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class AgilityEffectsSelfTest {
    private static final double EPSILON = 1.0E-9D;

    private AgilityEffectsSelfTest() {
    }

    public static void run() {
        assertAgility(-2, 1.0D / 12.0D, 0.99D);
        assertAgility(-1, 1.0D / 9.0D, 0.995D);
        assertAgility(0, 1.0D / 6.0D, 1.0D);
        assertAgility(1, 0.25D, 1.005D);
        assertAgility(2, 0.30D, 1.01D);
        assertAgility(5, 0.375D, 1.025D);
        assertAgility(10, 11.0D / 26.0D, 1.05D);
        assertAgility(20, 21.0D / 46.0D, 1.10D);

        assertClose(0.0D, AgilityEffects.calculateMovementSpeedModifier(-1000),
                "agility -1000 movement modifier");
        assertClose(0.0D, AgilityEffects.calculateMovementSpeedModifier(-10),
                "agility -10 movement modifier");
        assertClose(0.0D, AgilityEffects.calculateMovementSpeedModifier(-1),
                "agility -1 movement modifier");
        assertClose(0.0D, AgilityEffects.calculateMovementSpeedModifier(0),
                "agility 0 movement modifier");
        assertClose(0.01D, AgilityEffects.calculateMovementSpeedModifier(1),
                "agility 1 movement modifier");
        assertClose(0.10D, AgilityEffects.calculateMovementSpeedModifier(10),
                "agility 10 movement modifier");
        assertClose(0.20D, AgilityEffects.calculateMovementSpeedModifier(20),
                "agility 20 movement modifier");
        assertClose(0.0D, AgilityEffects.calculateBulletDamageMultiplier(-1000),
                "bullet damage lower bound");

        AttributeInstance movementSpeed = new AttributeInstance(
                new RangedAttribute("test.movement_speed", 0.1D, 0.0D, 1024.0D),
                ignored -> {
                }
        );
        AgilityEffects.replaceTransientModifier(
                movementSpeed,
                AgilityEffects.MOVEMENT_SPEED_MODIFIER_ID,
                "test.agility_movement_speed",
                0.10D,
                AttributeModifier.Operation.MULTIPLY_TOTAL,
                true
        );
        assertClose(0.11D, movementSpeed.getValue(), "agility 10 movement speed");

        AgilityEffects.replaceTransientModifier(
                movementSpeed,
                AgilityEffects.MOVEMENT_SPEED_MODIFIER_ID,
                "test.agility_movement_speed",
                0.10D,
                AttributeModifier.Operation.MULTIPLY_TOTAL,
                true
        );
        assertClose(0.11D, movementSpeed.getValue(), "repeated refresh does not stack");

        AgilityEffects.replaceTransientModifier(
                movementSpeed,
                AgilityEffects.MOVEMENT_SPEED_MODIFIER_ID,
                "test.agility_movement_speed",
                AgilityEffects.calculateMovementSpeedModifier(-10),
                AttributeModifier.Operation.MULTIPLY_TOTAL,
                true
        );
        assertClose(0.1D, movementSpeed.getValue(),
                "changing from positive to negative agility removes movement modifier");
        if (movementSpeed.getModifier(AgilityEffects.MOVEMENT_SPEED_MODIFIER_ID) != null) {
            throw new AssertionError("negative agility movement modifier was not removed");
        }

        AgilityEffects.replaceTransientModifier(
                movementSpeed,
                AgilityEffects.MOVEMENT_SPEED_MODIFIER_ID,
                "test.agility_movement_speed",
                AgilityEffects.calculateMovementSpeedModifier(20),
                AttributeModifier.Operation.MULTIPLY_TOTAL,
                true
        );
        assertClose(0.12D, movementSpeed.getValue(),
                "changing from negative to positive agility restores movement modifier");

        AttributeInstance dodgeChance = new AttributeInstance(
                new RangedAttribute("test.dodge_chance", 0.0D, 0.0D, 1.0D),
                ignored -> {
                }
        );
        AgilityEffects.replaceTransientModifier(
                dodgeChance,
                AgilityEffects.DODGE_CHANCE_MODIFIER_ID,
                "test.agility_dodge_chance",
                AgilityEffects.calculateDodgeChance(0),
                AttributeModifier.Operation.ADDITION,
                false
        );
        assertClose(1.0D / 6.0D, dodgeChance.getValue(), "agility 0 dodge mirror");

        AttributeInstance bulletDamage = new AttributeInstance(
                new RangedAttribute("test.bullet_damage", 1.0D, 0.0D, 1024.0D),
                ignored -> {
                }
        );
        AgilityEffects.replaceTransientModifier(
                bulletDamage,
                AgilityEffects.BULLET_DAMAGE_MODIFIER_ID,
                "test.agility_bullet_damage",
                AgilityEffects.calculateBulletDamageMultiplier(0) - 1.0D,
                AttributeModifier.Operation.ADDITION,
                false
        );
        assertClose(1.0D, bulletDamage.getValue(), "agility 0 bullet mirror");
        AgilityEffects.replaceTransientModifier(
                bulletDamage,
                AgilityEffects.BULLET_DAMAGE_MODIFIER_ID,
                "test.agility_bullet_damage",
                AgilityEffects.calculateBulletDamageMultiplier(20) - 1.0D,
                AttributeModifier.Operation.ADDITION,
                false
        );
        assertClose(1.10D, bulletDamage.getValue(), "agility 20 bullet mirror");
    }

    private static void assertAgility(int agility, double dodgeChance, double bulletMultiplier) {
        assertClose(dodgeChance, AgilityEffects.calculateDodgeChance(agility),
                "dodge chance at agility " + agility);
        assertClose(bulletMultiplier, AgilityEffects.calculateBulletDamageMultiplier(agility),
                "bullet damage multiplier at agility " + agility);
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
