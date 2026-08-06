package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Toggles Roland's .38 Special's conditional +1 {@link Attributes#ATTACK_DAMAGE}
 * addition, mirroring {@code StrengthEffects#updateAttackDamageModifier}: a
 * fixed-UUID transient modifier is removed and, only if the wearer is still
 * eligible ({@link RolandConditionalStrengthService#isEligible}), re-added.
 * Called from {@link com.casper.goetyarkham.stats.PlayerStatsService#refreshEffects}
 * (itself only invoked on an actual stat/equipment change, never
 * unconditionally every tick), so this never spams attribute sync.
 */
public final class RolandDamageBonusService {
    public static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString(
            "b6e29f14-7c3a-4d8e-b1f5-3a9c4e7d2b60");
    public static final double DAMAGE_BONUS = 1.0D;

    private static final String ATTACK_DAMAGE_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".rolands_38_special_attack_damage";

    private RolandDamageBonusService() {
    }

    public static void refresh(ServerPlayer player) {
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }
        attackDamage.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
        if (RolandConditionalStrengthService.isEligible(player)) {
            attackDamage.addTransientModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    DAMAGE_BONUS,
                    AttributeModifier.Operation.ADDITION));
        }
    }
}
