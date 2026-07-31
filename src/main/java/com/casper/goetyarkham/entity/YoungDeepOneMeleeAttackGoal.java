package com.casper.goetyarkham.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class YoungDeepOneMeleeAttackGoal extends Goal {
    static final double ATTACK_EDGE_REACH = 1.0D;
    private static final double ATTACK_EDGE_REACH_SQUARED =
            ATTACK_EDGE_REACH * ATTACK_EDGE_REACH;
    private static final double MINIMUM_FORWARD_DOT = 0.15D;
    private static final double CHASE_SPEED_MODIFIER = 1.0D;
    private static final int PATH_REFRESH_TICKS = 10;
    private static final double ATTACK_MOVEMENT_DAMPING = 0.2D;

    private final YoungDeepOneEntity deepOne;
    private final SwipeAttackSequence sequence = new SwipeAttackSequence();
    private LivingEntity attackTarget;
    private int pathRefreshCooldown;

    public YoungDeepOneMeleeAttackGoal(YoungDeepOneEntity deepOne) {
        this.deepOne = deepOne;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return isValidPlayerTarget(this.deepOne.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return this.deepOne.isAlive()
                && (this.sequence.isAttacking()
                || isValidPlayerTarget(this.deepOne.getTarget()));
    }

    @Override
    public void start() {
        this.pathRefreshCooldown = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        this.deepOne.getNavigation().stop();
        if (this.sequence.isAttacking()) {
            this.sequence.cancel();
            this.deepOne.cancelSwipeAttack();
        } else {
            this.deepOne.finishSwipeAttack();
        }
        this.attackTarget = null;
    }

    @Override
    public void tick() {
        if (this.sequence.isAttacking()) {
            this.tickActiveSwipe();
            return;
        }

        LivingEntity target = this.deepOne.getTarget();
        if (!isValidPlayerTarget(target)) {
            return;
        }

        this.faceTarget(target);
        if (isWithinAttackReach(
                this.deepOne.getBoundingBox(),
                target.getBoundingBox()
        )) {
            this.stopAttackMovement();
            if (this.sequence.canStart(this.deepOne.tickCount)) {
                this.startSwipe(target);
            }
            return;
        }

        if (--this.pathRefreshCooldown <= 0) {
            this.pathRefreshCooldown = PATH_REFRESH_TICKS;
            this.deepOne.getNavigation().moveTo(target, CHASE_SPEED_MODIFIER);
        }
    }

    private void startSwipe(LivingEntity target) {
        this.attackTarget = target;
        this.sequence.start(this.deepOne.tickCount);
        this.deepOne.beginSwipeAttack();
        this.stopAttackMovement();
    }

    private void tickActiveSwipe() {
        LivingEntity target = this.attackTarget;
        if (target != null && target.isAlive()) {
            this.faceTarget(target);
        }
        this.stopAttackMovement();

        boolean shouldDamage = this.sequence.tick(() ->
                this.canDamageTargetAtHitFrame(target));
        if (shouldDamage && !this.deepOne.level().isClientSide) {
            float damage = (float) this.deepOne.getAttributeValue(
                    Attributes.ATTACK_DAMAGE
            );
            target.hurt(this.deepOne.damageSources().mobAttack(this.deepOne), damage);
        }

        if (!this.sequence.isAttacking()) {
            this.deepOne.finishSwipeAttack();
            this.attackTarget = null;
        }
    }

    private boolean canDamageTargetAtHitFrame(LivingEntity target) {
        if (!isValidPlayerTarget(target)
                || target != this.deepOne.getTarget()
                || !isWithinAttackReach(
                        this.deepOne.getBoundingBox(),
                        target.getBoundingBox()
                )
                || !this.deepOne.getSensing().hasLineOfSight(target)) {
            return false;
        }

        return isInFrontOfAttacker(this.deepOne, target);
    }

    private void faceTarget(LivingEntity target) {
        this.deepOne.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void stopAttackMovement() {
        this.deepOne.getNavigation().stop();
        Vec3 movement = this.deepOne.getDeltaMovement();
        this.deepOne.setDeltaMovement(
                movement.x * ATTACK_MOVEMENT_DAMPING,
                movement.y,
                movement.z * ATTACK_MOVEMENT_DAMPING
        );
    }

    private static boolean isValidPlayerTarget(LivingEntity target) {
        return target instanceof Player player
                && target.isAlive()
                && !player.isCreative()
                && !player.isSpectator();
    }

    static boolean isWithinAttackReach(AABB attacker, AABB target) {
        return squaredDistanceBetween(attacker, target)
                <= ATTACK_EDGE_REACH_SQUARED;
    }

    static double squaredDistanceBetween(AABB first, AABB second) {
        double xGap = axisGap(first.minX, first.maxX, second.minX, second.maxX);
        double yGap = axisGap(first.minY, first.maxY, second.minY, second.maxY);
        double zGap = axisGap(first.minZ, first.maxZ, second.minZ, second.maxZ);
        return xGap * xGap + yGap * yGap + zGap * zGap;
    }

    private static double axisGap(
            double firstMinimum,
            double firstMaximum,
            double secondMinimum,
            double secondMaximum
    ) {
        if (firstMaximum < secondMinimum) {
            return secondMinimum - firstMaximum;
        }
        if (secondMaximum < firstMinimum) {
            return firstMinimum - secondMaximum;
        }
        return 0.0D;
    }

    private static boolean isInFrontOfAttacker(
            YoungDeepOneEntity attacker,
            LivingEntity target
    ) {
        Vec3 facing = attacker.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D);
        AABB targetBox = target.getBoundingBox();
        Vec3 towardTarget = new Vec3(
                (targetBox.minX + targetBox.maxX) * 0.5D - attacker.getX(),
                0.0D,
                (targetBox.minZ + targetBox.maxZ) * 0.5D - attacker.getZ()
        );
        if (towardTarget.lengthSqr() < 1.0E-6D) {
            return true;
        }

        return facing.normalize().dot(towardTarget.normalize())
                >= MINIMUM_FORWARD_DOT;
    }
}
