package com.casper.goetyarkham.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Radius aura for a worn Rita Chandler's Token. While functionally equipped,
 * the token's {@code curioTick} calls {@link #pulseAuraFrom} every server
 * tick: every {@link ServerPlayer} within {@link #RADIUS} blocks of the
 * wearer (the wearer included) is (re)blessed with
 * {@link RitaChandlersBlessingEffect}, a short, continuously-refreshed
 * {@link MobEffectInstance} rather than a permanent attribute write. Its
 * presence is what grants +1 Strength (mixed into
 * {@code PlayerStatsService#getFinalValue}, mirroring
 * {@link DreamsOfRlyehEffectService}'s Willpower hook) and the flat +2
 * damage bonus (see {@code RitaChandlersTokenEffectEvents}).
 *
 * <p>Because the bonus is keyed off a single named vanilla effect per
 * target, overlapping auras from multiple tokens - and repeated per-tick
 * pulses from the very same token - are naturally non-stacking: whichever
 * pulse arrives first (re)applies the shared instance, amplifier always 0,
 * and later pulses in the same refresh window are no-ops.</p>
 */
public final class RitaChandlersAuraEffectService {
    public static final double RADIUS = 10.0D;
    public static final double RADIUS_SQUARED = RADIUS * RADIUS;
    public static final int STRENGTH_BONUS = 1;
    public static final float DAMAGE_BONUS = 2.0F;

    /**
     * Duration handed out on every (re)application. Chosen to comfortably
     * outlast the gap between two consecutive per-tick pulses while still
     * decaying within about a second of the last pulse actually landing
     * (wearer leaves range, unequips without triggering {@code onUnequip},
     * logs out, or changes dimension away from every wearer), which is the
     * "temporary/dynamic source instead of a permanent attribute write"
     * mechanism this feature is required to use.
     */
    static final int BLESSING_DURATION_TICKS = 20;

    /**
     * Only reissues the effect once its remaining time drops to/below this
     * threshold, instead of on every single pulse. Reapplying every tick
     * would still be idempotent gameplay-wise, but would also re-fire
     * {@code MobEffectEvent.Added} (and the {@code PlayerStatsService} sync
     * it triggers) up to 20 times a second per blessed player for no
     * behavioral benefit.
     */
    static final int REFRESH_THRESHOLD_TICKS = 10;

    private RitaChandlersAuraEffectService() {
    }

    /**
     * Blesses every player currently within range of {@code wearer}, itself
     * included. Iterates {@code Level#players()} - the same
     * always-accurate online-player list {@code DiscOfItzamnaEffectService}
     * already uses to find wearers - rather than a chunk-indexed
     * {@code getEntitiesOfClass} query, since not every player entity is
     * guaranteed to be spatially registered in chunk storage (notably,
     * GameTest stand-in players are only ever added to this list).
     */
    public static void pulseAuraFrom(ServerPlayer wearer) {
        for (Player candidate : wearer.level().players()) {
            if (candidate instanceof ServerPlayer serverCandidate
                    && isInRange(wearer, serverCandidate)) {
                bless(serverCandidate);
            }
        }
    }

    /** The single authoritative definition of "inside the aura": actual entity distance, radius 10. */
    public static boolean isInRange(ServerPlayer wearer, ServerPlayer candidate) {
        return candidate.isAlive() && wearer.distanceToSqr(candidate) <= RADIUS_SQUARED;
    }

    static void bless(ServerPlayer player) {
        MobEffectInstance current =
                player.getEffect(ModEffects.RITA_CHANDLERS_BLESSING.get());
        if (current != null && current.getDuration() > REFRESH_THRESHOLD_TICKS) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                ModEffects.RITA_CHANDLERS_BLESSING.get(),
                BLESSING_DURATION_TICKS,
                0,
                true,
                false,
                true));
    }

    /**
     * Explicitly clears the wearer's own blessing the instant their token is
     * unequipped, rather than waiting out the natural decay window. If the
     * same player also happens to be standing in a different wearer's still
     * active aura at that exact moment, that wearer's own next per-tick
     * pulse (at most 1 tick later) reblesses them; this is treated as "still
     * covered by another source" rather than a residual leftover from this
     * token.
     */
    public static void clearOwnBlessing(ServerPlayer player) {
        player.removeEffect(ModEffects.RITA_CHANDLERS_BLESSING.get());
    }

    public static boolean isBlessed(ServerPlayer player) {
        return ModEffects.RITA_CHANDLERS_BLESSING.isPresent()
                && player.hasEffect(ModEffects.RITA_CHANDLERS_BLESSING.get());
    }

    public static int strengthModifier(ServerPlayer player) {
        return isBlessed(player) ? STRENGTH_BONUS : 0;
    }
}
