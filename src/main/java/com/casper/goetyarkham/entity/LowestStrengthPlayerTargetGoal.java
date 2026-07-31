package com.casper.goetyarkham.entity;

import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public final class LowestStrengthPlayerTargetGoal extends TargetGoal {
    private final YoungDeepOneEntity deepOne;

    public LowestStrengthPlayerTargetGoal(YoungDeepOneEntity deepOne) {
        super(deepOne, true, false);
        this.deepOne = deepOne;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        ServerLevel level = (ServerLevel) this.deepOne.level();
        double followDistance = this.getFollowDistance();
        TargetingConditions conditions = TargetingConditions.forCombat()
                .range(followDistance);
        this.targetMob = selectBest(
                level.getPlayers(player -> this.isInitialCandidate(
                        player,
                        level,
                        conditions,
                        followDistance
                )),
                player -> PlayerStatsService.getFinalValue(
                        player,
                        StatType.STRENGTH
                ),
                this.deepOne::distanceToSqr,
                ServerPlayer::getUUID
        ).orElse(null);
        return this.targetMob != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity current = this.deepOne.getTarget();
        if (current == null) {
            current = this.targetMob;
        }
        if (!(current instanceof ServerPlayer player)
                || player.serverLevel() != this.deepOne.level()
                || player.isRemoved()
                || !player.isAlive()
                || player.isCreative()
                || player.isSpectator()) {
            return false;
        }
        return super.canContinueToUse();
    }

    @Override
    public void start() {
        this.deepOne.setTarget(this.targetMob);
        super.start();
    }

    private boolean isInitialCandidate(
            ServerPlayer player,
            ServerLevel level,
            TargetingConditions conditions,
            double followDistance
    ) {
        return player.serverLevel() == level
                && !player.isRemoved()
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && this.deepOne.distanceToSqr(player)
                <= followDistance * followDistance
                && this.canAttack(player, conditions);
    }

    static <T> Optional<T> selectBest(
            Collection<T> candidates,
            ToIntFunction<T> finalStrength,
            ToDoubleFunction<T> distanceSquared,
            Function<T, UUID> uuid
    ) {
        return candidates.stream()
                .map(candidate -> new RankedCandidate<>(
                        candidate,
                        finalStrength.applyAsInt(candidate),
                        distanceSquared.applyAsDouble(candidate),
                        uuid.apply(candidate)
                ))
                .min(Comparator
                        .comparingInt(RankedCandidate<T>::finalStrength)
                        .thenComparingDouble(
                                RankedCandidate<T>::distanceSquared
                        )
                        .thenComparing(RankedCandidate<T>::uuid))
                .map(RankedCandidate::candidate);
    }

    private record RankedCandidate<T>(
            T candidate,
            int finalStrength,
            double distanceSquared,
            UUID uuid
    ) {
    }
}
