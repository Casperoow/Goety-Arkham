package com.casper.goetyarkham.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class YoungDeepOneEntity extends PathfinderMob implements GeoEntity {
    public static final float EYE_HEIGHT = 0.62F;

    private static final RawAnimation IDLE_ANIMATION =
            RawAnimation.begin().thenLoop("animation.young_deep_one.idle");
    private static final RawAnimation CRAWL_ANIMATION =
            RawAnimation.begin().thenLoop("animation.young_deep_one.crawl");
    private static final double CRAWL_START_SPEED_SQUARED = 0.0004D;
    private static final double CRAWL_STOP_SPEED_SQUARED = 0.0001D;

    private final AnimatableInstanceCache animationCache =
            GeckoLibUtil.createInstanceCache(this);
    private boolean crawlAnimationActive;

    public YoungDeepOneEntity(EntityType<? extends YoungDeepOneEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return EYE_HEIGHT;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "movement",
                6,
                this::selectMovementAnimation
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    private PlayState selectMovementAnimation(
            AnimationState<YoungDeepOneEntity> animationState
    ) {
        if (!this.isAlive()) {
            this.crawlAnimationActive = false;
            return PlayState.STOP;
        }

        double horizontalSpeedSquared =
                this.getDeltaMovement().horizontalDistanceSqr();
        if (this.crawlAnimationActive) {
            this.crawlAnimationActive =
                    horizontalSpeedSquared > CRAWL_STOP_SPEED_SQUARED;
        } else {
            this.crawlAnimationActive =
                    horizontalSpeedSquared > CRAWL_START_SPEED_SQUARED;
        }

        return animationState.setAndContinue(
                this.crawlAnimationActive ? CRAWL_ANIMATION : IDLE_ANIMATION
        );
    }
}
