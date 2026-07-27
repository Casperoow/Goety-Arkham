package com.casper.goetyarkham.agility;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.util.UUID;

public final class AgilityEffectsSelfTest {
    private static final double EPSILON = 1.0E-9D;
    private static final UUID THIRD_PARTY_MOVEMENT_MODIFIER_ID =
            UUID.fromString("d5cbfb4b-f35f-4d72-8944-39d9a921e57a");

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

        assertMovementSpeedModifier(-1, 0.0D);
        assertMovementSpeedModifier(0, 0.0D);
        assertMovementSpeedModifier(1, 0.01D);
        assertMovementSpeedModifier(49, 0.49D);
        assertMovementSpeedModifier(50, 0.50D);
        assertMovementSpeedModifier(51, 0.50D);
        assertMovementSpeedModifier(60, 0.50D);
        assertClose(0.0D, AgilityEffects.calculateBulletDamageMultiplier(-1000),
                "bullet damage lower bound");
        assertAgility(51, 52.0D / 108.0D, 1.255D);
        assertAgility(60, 61.0D / 126.0D, 1.30D);

        AttributeInstance movementSpeed = new AttributeInstance(
                new RangedAttribute("test.movement_speed", 0.1D, 0.0D, 1024.0D),
                ignored -> {
                }
        );
        movementSpeed.addTransientModifier(new AttributeModifier(
                THIRD_PARTY_MOVEMENT_MODIFIER_ID,
                "test.third_party_movement_speed",
                0.20D,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
        AgilityEffects.replaceTransientModifier(
                movementSpeed,
                AgilityEffects.MOVEMENT_SPEED_MODIFIER_ID,
                "test.agility_movement_speed",
                0.10D,
                AttributeModifier.Operation.MULTIPLY_TOTAL,
                true
        );
        assertClose(0.132D, movementSpeed.getValue(),
                "agility movement speed stacks with third-party movement speed");

        AgilityEffects.replaceTransientModifier(
                movementSpeed,
                AgilityEffects.MOVEMENT_SPEED_MODIFIER_ID,
                "test.agility_movement_speed",
                0.10D,
                AttributeModifier.Operation.MULTIPLY_TOTAL,
                true
        );
        assertClose(0.132D, movementSpeed.getValue(), "repeated refresh does not stack");

        AgilityEffects.replaceTransientModifier(
                movementSpeed,
                AgilityEffects.MOVEMENT_SPEED_MODIFIER_ID,
                "test.agility_movement_speed",
                AgilityEffects.calculateMovementSpeedModifier(-10),
                AttributeModifier.Operation.MULTIPLY_TOTAL,
                true
        );
        assertClose(0.12D, movementSpeed.getValue(),
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
        assertClose(0.144D, movementSpeed.getValue(),
                "changing from negative to positive agility restores movement modifier");
        if (movementSpeed.getModifier(THIRD_PARTY_MOVEMENT_MODIFIER_ID) == null) {
            throw new AssertionError("agility refresh removed third-party movement modifier");
        }

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

    private static void assertMovementSpeedModifier(int agility, double expected) {
        assertClose(expected, AgilityEffects.calculateMovementSpeedModifier(agility),
                "movement speed modifier at agility " + agility);
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
