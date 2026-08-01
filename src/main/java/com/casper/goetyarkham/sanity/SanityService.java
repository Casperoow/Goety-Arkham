package com.casper.goetyarkham.sanity;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.attribute.ModAttributes;
import com.casper.goetyarkham.network.ModNetwork;
import com.casper.goetyarkham.sanity.weakness.WeaknessActivationCause;
import com.casper.goetyarkham.sanity.weakness.WeaknessActivationService;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.soul.SoulPoolSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.Optional;

/** Server-authoritative facade for every sanity read and mutation. */
public final class SanityService {
    private SanityService() {
    }

    public static Optional<IPlayerSanity> get(ServerPlayer player) {
        return player.getCapability(SanityCapabilities.PLAYER_SANITY).resolve();
    }

    public static int getCurrentSanity(ServerPlayer player) {
        // A modifier can change outside this service. Clamp on the read path as
        // well as the end-of-tick refresh so callers never observe current > max.
        refreshMaximum(player);
        return get(player).map(IPlayerSanity::getCurrentSanity).orElse(0);
    }

    public static int getMaximumSanity(ServerPlayer player) {
        int loss = getPermanentMaxLoss(player);
        return SanityMath.maximumSanity(getMaximumAttributeValue(player), loss);
    }

    public static int getPermanentMaxLoss(ServerPlayer player) {
        return get(player).map(IPlayerSanity::getPermanentMaxLoss).orElse(0);
    }

    public static double getMaximumAttributeBaseValue(ServerPlayer player) {
        AttributeInstance instance = maximumAttribute(player);
        return instance == null ? SanityConstants.BASE_MAXIMUM : instance.getBaseValue();
    }

    public static double getMaximumAttributeValue(ServerPlayer player) {
        AttributeInstance instance = maximumAttribute(player);
        return instance == null ? SanityConstants.BASE_MAXIMUM : instance.getValue();
    }

    /** Returns the signed change (new value minus old value). */
    public static int setSanity(
            ServerPlayer player, int value, SanityChangeCause cause) {
        Optional<PlayerSanityData> optional = mutable(player);
        if (optional.isEmpty()) {
            return 0;
        }
        PlayerSanityData data = optional.get();
        int maximum = getMaximumSanity(player);
        PlayerSanityData.SanityTransition transition =
                data.setCurrentSanity(value, maximum);
        if (!transition.changed()) {
            return 0;
        }
        onTransition(player, data, transition);
        int finalCurrent = data.getCurrentSanity();
        logSanityChange(player, transition.previous(), finalCurrent, cause);
        sync(player);
        return finalCurrent - transition.previous();
    }

    /** Returns the amount actually restored. */
    public static int addSanity(
            ServerPlayer player, int amount, SanityChangeCause cause) {
        if (amount <= 0) {
            return 0;
        }
        int previous = getCurrentSanity(player);
        int target = saturatingAdd(previous, amount);
        return Math.max(0, setSanity(player, target, cause));
    }

    /** Returns the amount actually removed. */
    public static int damageSanity(
            ServerPlayer player, int amount, SanityChangeCause cause) {
        if (amount <= 0) {
            return 0;
        }
        int previous = getCurrentSanity(player);
        int target = saturatingAdd(previous, -amount);
        return Math.max(0, -setSanity(player, target, cause));
    }

    public static int restoreSanity(
            ServerPlayer player, int amount, SanityChangeCause cause) {
        return addSanity(player, amount, cause);
    }

    /** Returns permanent loss actually added, always within 0..9. */
    public static int addPermanentMaxLoss(
            ServerPlayer player, int amount, SanityChangeCause cause) {
        if (amount <= 0) {
            return 0;
        }
        Optional<PlayerSanityData> optional = mutable(player);
        if (optional.isEmpty()) {
            return 0;
        }
        PlayerSanityData data = optional.get();
        int previousLoss = data.getPermanentMaxLoss();
        int target = saturatingAdd(previousLoss, amount);
        int actual = Math.max(0, data.setPermanentMaxLoss(target));
        if (actual == 0) {
            return 0;
        }
        int maximum = getMaximumSanity(player);
        int previousSanity = data.getCurrentSanity();
        data.clampToMaximum(maximum);
        GoetyArkham.LOGGER.info(
                "[Sanity] Permanent maximum loss changed: player={}, uuid={}, previous={}, current={}, cause={}",
                player.getGameProfile().getName(), player.getUUID(),
                previousLoss, data.getPermanentMaxLoss(), normalizeCause(cause));
        if (previousSanity != data.getCurrentSanity()) {
            logSanityChange(player, previousSanity, data.getCurrentSanity(), cause);
        }
        sync(player);
        return actual;
    }

    /** Returns permanent loss actually restored. */
    public static int restorePermanentMaxLoss(
            ServerPlayer player, int amount, SanityChangeCause cause) {
        if (amount <= 0) {
            return 0;
        }
        Optional<PlayerSanityData> optional = mutable(player);
        if (optional.isEmpty()) {
            return 0;
        }
        PlayerSanityData data = optional.get();
        int previousLoss = data.getPermanentMaxLoss();
        int target = saturatingAdd(previousLoss, -amount);
        int signed = data.setPermanentMaxLoss(target);
        int restored = Math.max(0, -signed);
        if (restored == 0) {
            return 0;
        }
        // Raising the maximum never restores current sanity.
        GoetyArkham.LOGGER.info(
                "[Sanity] Permanent maximum loss restored: player={}, uuid={}, previous={}, current={}, cause={}",
                player.getGameProfile().getName(), player.getUUID(),
                previousLoss, data.getPermanentMaxLoss(), normalizeCause(cause));
        sync(player);
        return restored;
    }

    /** Recomputes the attribute-derived maximum and clamps current sanity down only. */
    public static boolean refreshMaximum(ServerPlayer player) {
        Optional<PlayerSanityData> optional = mutable(player);
        if (optional.isEmpty()) {
            return false;
        }
        PlayerSanityData data = optional.get();
        int maximum = getMaximumSanity(player);
        int previousMaximum = data.getLastObservedMaximum();
        int previousSanity = data.getCurrentSanity();
        boolean currentChanged = data.clampToMaximum(maximum);
        boolean maximumChanged = previousMaximum >= 0 && previousMaximum != maximum;
        data.setLastObservedMaximum(maximum);
        if (maximumChanged || currentChanged) {
            GoetyArkham.LOGGER.info(
                    "[Sanity] Maximum refreshed: player={}, uuid={}, previousMaximum={}, maximum={}, previousSanity={}, currentSanity={}",
                    player.getGameProfile().getName(), player.getUUID(),
                    previousMaximum, maximum, previousSanity, data.getCurrentSanity());
            sync(player);
        }
        return maximumChanged || currentChanged;
    }

    public static boolean sync(ServerPlayer player) {
        Optional<PlayerSanityData> optional = mutable(player);
        if (optional.isEmpty()) {
            return false;
        }
        PlayerSanityData data = optional.get();
        SanitySnapshot snapshot = new SanitySnapshot(
                data.getCurrentSanity(),
                getMaximumSanity(player),
                data.getPermanentMaxLoss());
        if (!data.needsClientSync(snapshot)) {
            return false;
        }
        if (!ModNetwork.sendSanity(player, snapshot)) {
            return false;
        }
        data.markClientSynced(snapshot);
        return true;
    }

    static boolean completeCollapseRespawn(ServerPlayer player) {
        Optional<PlayerSanityData> optional = mutable(player);
        if (optional.isEmpty()) {
            return false;
        }
        PlayerSanityData data = optional.get();
        if (!data.consumePendingCollapseRespawnRefill()) {
            return false;
        }
        int maximum = getMaximumSanity(player);
        int previous = data.getCurrentSanity();
        data.setCurrentSanity(maximum, maximum);
        data.resetCollapseAfterDeath();
        if (previous != data.getCurrentSanity()) {
            logSanityChange(
                    player, previous, data.getCurrentSanity(), SanityChangeCause.DEATH);
        }
        return true;
    }

    static Optional<PlayerSanityData> mutable(ServerPlayer player) {
        return player.getCapability(SanityCapabilities.PLAYER_SANITY)
                .resolve()
                .filter(PlayerSanityData.class::isInstance)
                .map(PlayerSanityData.class::cast);
    }

    static void tick(ServerPlayer player) {
        refreshMaximum(player);
        Optional<PlayerSanityData> optional = mutable(player);
        if (optional.isEmpty()) {
            return;
        }
        PlayerSanityData data = optional.get();
        if (data.getCurrentSanity() > 0) {
            return;
        }
        if (!data.isCollapseActive()) {
            data.setCurrentSanity(0, getMaximumSanity(player));
        }

        SoulPoolSnapshot before = SoulEnergyPoolService.getSnapshot(player);
        if (SanityCollapseRules.isSoulDepleted(
                before.hasContainer(), before.currentSoul())) {
            forceCollapseDeath(player, data, "empty soul pool");
            return;
        }
        if (!data.advanceCollapseTick()) {
            return;
        }

        SoulEnergyPoolService.removeSoul(player, SanityConstants.COLLAPSE_SOUL_COST);
        player.addEffect(new MobEffectInstance(
                MobEffects.HUNGER,
                SanityConstants.COLLAPSE_HUNGER_DURATION_TICKS,
                0,
                false,
                false,
                true));
        SoulPoolSnapshot after = SoulEnergyPoolService.getSnapshot(player);
        GoetyArkham.LOGGER.info(
                "[Sanity] Collapse settlement: player={}, uuid={}, soulBefore={}, soulAfter={}",
                player.getGameProfile().getName(), player.getUUID(),
                before.currentSoul(), after.currentSoul());
        if (SanityCollapseRules.isSoulDepleted(
                after.hasContainer(), after.currentSoul())) {
            forceCollapseDeath(player, data, "soul depleted by collapse");
        }
    }

    private static void onTransition(
            ServerPlayer player,
            PlayerSanityData data,
            PlayerSanityData.SanityTransition transition) {
        if (transition.enteredCollapse()) {
            int activated = WeaknessActivationService.activateAll(
                    player, WeaknessActivationCause.SANITY_DEPLETED);
            GoetyArkham.LOGGER.info(
                    "[Sanity] Collapse entered: player={}, uuid={}, weaknessesActivated={}",
                    player.getGameProfile().getName(), player.getUUID(), activated);
            // Weakness activation must complete before checking the soul pool.
            if (data.getCurrentSanity() == 0) {
                SoulPoolSnapshot soul = SoulEnergyPoolService.getSnapshot(player);
                if (SanityCollapseRules.isSoulDepleted(
                        soul.hasContainer(), soul.currentSoul())) {
                    forceCollapseDeath(player, data, "zero soul on collapse entry");
                }
            }
        } else if (transition.exitedCollapse()) {
            GoetyArkham.LOGGER.info(
                    "[Sanity] Collapse exited: player={}, uuid={}",
                    player.getGameProfile().getName(), player.getUUID());
        }
    }

    private static void forceCollapseDeath(
            ServerPlayer player, PlayerSanityData data, String reason) {
        if (!player.isAlive() || player.isRemoved()
                || !data.markCollapseDeathRequested()) {
            return;
        }
        GoetyArkham.LOGGER.warn(
                "[Sanity] Forcing collapse death: player={}, uuid={}, reason={}",
                player.getGameProfile().getName(), player.getUUID(), reason);
        // Entity#kill is the same unconditional path used by vanilla /kill.
        // It is not an armor-reducible or event-discountable resource cost.
        player.kill();
    }

    private static AttributeInstance maximumAttribute(ServerPlayer player) {
        if (!ModAttributes.MAX_SANITY.isPresent()) {
            return null;
        }
        return player.getAttribute(ModAttributes.MAX_SANITY.get());
    }

    private static void logSanityChange(
            ServerPlayer player, int previous, int current, SanityChangeCause cause) {
        GoetyArkham.LOGGER.info(
                "[Sanity] Current sanity changed: player={}, uuid={}, previous={}, current={}, cause={}",
                player.getGameProfile().getName(), player.getUUID(),
                previous, current, normalizeCause(cause));
    }

    private static SanityChangeCause normalizeCause(SanityChangeCause cause) {
        return cause == null ? SanityChangeCause.OTHER : cause;
    }

    private static int saturatingAdd(int value, int amount) {
        long result = (long) value + amount;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
    }
}
