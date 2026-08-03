package com.casper.goetyarkham.item;

import com.Polarice3.Goety.utils.ModDamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.List;

/** Applies the Heirloom's one-pass focus-spend damage settlement. */
public final class HeirloomSoulSpendEffectService {
    /** Reuses the project's established nearby-enemy radius. */
    public static final double DAMAGE_RADIUS =
            DiscOfItzamnaEffectService.EFFECT_RADIUS;
    public static final double DAMAGE_RADIUS_SQUARED =
            DAMAGE_RADIUS * DAMAGE_RADIUS;

    private HeirloomSoulSpendEffectService() {
    }

    public static int onFocusSoulSpent(
            ServerPlayer caster, int actualSpent) {
        if (actualSpent <= 0
                || !HeirloomOfHyperboreaService.isWearing(caster)) {
            return 0;
        }
        return damageNearbyEnemies(caster, actualSpent);
    }

    /** Package-visible as a deterministic GameTest seam after wear validation. */
    static int damageNearbyEnemies(ServerPlayer caster, int actualSpent) {
        if (actualSpent <= 0 || caster.level().isClientSide) {
            return 0;
        }
        List<Mob> targets = caster.level().getEntitiesOfClass(
                Mob.class,
                caster.getBoundingBox().inflate(DAMAGE_RADIUS),
                target -> isEligibleEnemy(caster, target));
        if (targets.isEmpty()) {
            return 0;
        }

        // Goety magic damage with the caster as both direct and causing entity
        // preserves normal hurt events, death attribution, and mod integration.
        DamageSource source = ModDamageSource.magicBolt(caster, caster);
        float damage = actualSpent;
        int damaged = 0;
        for (Mob target : targets) {
            if (target.hurt(source, damage)) {
                damaged++;
            }
        }
        return damaged;
    }

    public static boolean isEligibleEnemy(ServerPlayer caster, Mob target) {
        return caster.isAlive()
                && target.isAlive()
                && target instanceof Enemy
                && caster.distanceToSqr(target) <= DAMAGE_RADIUS_SQUARED
                && !DiscOfItzamnaEffectService.isPlayerAllyOrServant(
                caster, target);
    }
}
