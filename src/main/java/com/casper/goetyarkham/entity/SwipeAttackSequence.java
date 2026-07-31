package com.casper.goetyarkham.entity;

import java.util.function.BooleanSupplier;

final class SwipeAttackSequence {
    static final int ANIMATION_TICKS = 15;
    static final int HIT_TICK = 6;
    static final int COOLDOWN_TICKS = 27;

    private int attackTick = -1;
    private int nextAllowedStartTick = Integer.MIN_VALUE;
    private boolean hitResolved;

    boolean canStart(int currentTick) {
        return !this.isAttacking() && currentTick >= this.nextAllowedStartTick;
    }

    void start(int currentTick) {
        if (!this.canStart(currentTick)) {
            throw new IllegalStateException("Swipe attack is still active or cooling down");
        }

        this.attackTick = 0;
        this.nextAllowedStartTick = currentTick + COOLDOWN_TICKS;
        this.hitResolved = false;
    }

    boolean tick(BooleanSupplier hitEligibility) {
        if (!this.isAttacking()) {
            return false;
        }

        this.attackTick++;
        boolean shouldDamage = false;
        if (!this.hitResolved && this.attackTick >= HIT_TICK) {
            this.hitResolved = true;
            shouldDamage = hitEligibility.getAsBoolean();
        }

        if (this.attackTick >= ANIMATION_TICKS) {
            this.attackTick = -1;
        }

        return shouldDamage;
    }

    boolean isAttacking() {
        return this.attackTick >= 0;
    }

    int attackTick() {
        return this.attackTick;
    }

    int nextAllowedStartTick() {
        return this.nextAllowedStartTick;
    }

    void cancel() {
        this.attackTick = -1;
        this.hitResolved = true;
    }
}
