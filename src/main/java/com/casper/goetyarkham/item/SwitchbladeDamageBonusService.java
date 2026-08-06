package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Toggles the Switchblade's conditional +2 {@link Attributes#ATTACK_DAMAGE}
 * addition, mirroring {@code RolandDamageBonusService}: a fixed-UUID
 * transient modifier is removed and, only if the wielder is still eligible
 * ({@link #isEligible}), re-added. Because the bonus is baked directly into
 * the vanilla Attack Damage attribute, a normal melee attack already folds
 * it into the single {@code LivingEntity#hurt} call {@code Player#attack}
 * makes - there is no second hurt call, no extra hit/crit/knockback event,
 * and no extra durability loss, and ranged/spell/thorns/summon damage never
 * reads this attribute at all. Called from {@link
 * PlayerStatsService#refreshEffects} (on stat/equipment change) and from
 * {@link SwitchbladeEvents} (on main-hand change), so this never goes stale
 * whether Strength or the held item changes.
 */
public final class SwitchbladeDamageBonusService {
    public static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString(
            "c47c5a3e-0f8b-4b57-9dfe-1a5b9d3e2f60");
    public static final double DAMAGE_BONUS = 2.0D;
    public static final int STRENGTH_THRESHOLD = 5;

    private static final String ATTACK_DAMAGE_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".switchblade_attack_damage";

    private SwitchbladeDamageBonusService() {
    }

    public static void refresh(ServerPlayer player) {
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }
        attackDamage.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
        if (isEligible(player)) {
            attackDamage.addTransientModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    DAMAGE_BONUS,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    static boolean isEligible(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.SWITCHBLADE.get())
                && PlayerStatsService.getFinalValue(player, StatType.STRENGTH) >= STRENGTH_THRESHOLD;
    }
}
