package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.effect.RitaChandlersAuraEffectService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side flat damage bonus for players currently blessed by a nearby
 * Rita Chandler's Token aura (see {@link RitaChandlersAuraEffectService}).
 * Uses {@link LivingHurtEvent} (fired once per {@code LivingEntity#hurt}
 * call, before armor/resistance reduction) so the bonus becomes part of
 * ordinary combat damage exactly once per hit, the same event BeatCopsToken
 * uses for its own melee bonus.
 *
 * <p>Attribution is generic rather than melee-only: {@link
 * DamageSource#getEntity()} is vanilla/Forge's own "who caused this damage"
 * resolution (the shooter of an arrow, the caster behind a spell, the
 * shooter of a TACZ bullet - as opposed to {@code getDirectEntity()}, which
 * for those cases is the projectile/bullet entity itself), so this covers
 * melee, ordinary projectiles, TACZ guns, and any Goety spell damage that
 * sets its caster as the source entity, without depending on TACZ's or
 * Goety's own APIs. Environmental damage, thorns/reflected damage, and
 * damage from a summon acting on its own (source entity would be the
 * summon, not a player) never satisfy the checks below and are left
 * untouched.</p>
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class RitaChandlersTokenEffectEvents {
    private RitaChandlersTokenEffectEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource source = event.getSource();
        if (source.is(DamageTypes.THORNS)) {
            // Reflected/retaliation damage must never re-trigger this bonus.
            return;
        }

        Entity causer = source.getEntity();
        if (!(causer instanceof ServerPlayer attacker) || causer == event.getEntity()) {
            // Excludes sourceless/environmental damage, non-player causers
            // (including a summon acting on its own), and self-inflicted
            // damage - including Aquinnah's Token's own redirect, which
            // reuses the original attacker's DamageSource to hurt that same
            // attacker, so its entity() and this event's entity are equal.
            return;
        }

        if (!RitaChandlersAuraEffectService.isBlessed(attacker)) {
            return;
        }

        event.setAmount(event.getAmount() + RitaChandlersAuraEffectService.DAMAGE_BONUS);
    }
}
