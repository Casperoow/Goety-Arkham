package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds the Machete's +2 damage against {@link Enemy} targets directly onto
 * the single {@link LivingHurtEvent} of an ordinary melee hit (fired before
 * armor/resistance reduction, mirroring {@code BeatCopsTokenEffectEvents}'s
 * use of the same event), so the bonus becomes part of ordinary melee damage
 * - subject to the target's armor like the rest of the hit - instead of
 * triggering a second, separate {@code hurt()} call. That is also what keeps
 * this from ever affecting ranged/spell/potion/summon damage: only {@link
 * DamageTypes#PLAYER_ATTACK} sources reach this branch at all, and requiring
 * both {@link DamageSource#getEntity()} and {@link DamageSource#getDirectEntity()}
 * to be the very same attacking player excludes anything thrown, shot, or
 * otherwise indirect (a thrown knife or fired bullet still has the player as
 * {@code getEntity()}, but not as {@code getDirectEntity()}). Vanilla's own
 * sweeping-edge secondary hits reuse this same damage type and are direct
 * melee too, so they are intentionally not excluded here.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class MacheteMonsterDamageEvents {
    private MacheteMonsterDamageEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource source = event.getSource();
        if (!source.is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }

        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attackerEntity) {
            return;
        }

        if (!attacker.getMainHandItem().is(ModItems.MACHETE.get())) {
            return;
        }

        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }

        event.setAmount(event.getAmount() + MacheteItem.MONSTER_BONUS_DAMAGE);
    }
}
