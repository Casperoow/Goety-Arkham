package com.casper.goetyarkham.entity;

import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class YoungDeepOneEntity extends Monster implements GeoEntity {
    public static final float EYE_HEIGHT = 0.62F;

    private static final String ATTACK_CONTROLLER = "attack";
    private static final String SWIPE_TRIGGER = "swipe";
    private static final EntityDataAccessor<Boolean> SWIPE_ATTACKING =
            SynchedEntityData.defineId(
                    YoungDeepOneEntity.class,
                    EntityDataSerializers.BOOLEAN
            );
    private static final RawAnimation IDLE_ANIMATION =
            RawAnimation.begin().thenLoop("animation.young_deep_one.idle");
    private static final RawAnimation CRAWL_ANIMATION =
            RawAnimation.begin().thenLoop("animation.young_deep_one.crawl");
    private static final RawAnimation SWIPE_ANIMATION =
            RawAnimation.begin().thenPlay("animation.young_deep_one.swipe");
    private static final double CRAWL_START_SPEED_SQUARED = 0.0004D;
    private static final double CRAWL_STOP_SPEED_SQUARED = 0.0001D;
    private static final float WATER_SPEED_MODIFIER = 0.5F;
    private static final float LAND_SPEED_MODIFIER = 1.0F;
    private static final double SHORE_LOOK_AHEAD_DISTANCE = 0.4D;
    private static final double SHORE_OBSTRUCTION_CHECK_DISTANCE = 0.2D;
    private static final double SHORE_STEP_HEIGHT = 1.0D;
    private static final double SHORE_SUPPORT_PROBE_DEPTH = 0.125D;
    private static final double SHORE_ASCENT_SPEED = 0.12D;
    private static final double SHORE_SURFACE_ALLOWANCE = 0.55D;
    private static final double MINIMUM_SHORE_DIRECTION_SQUARED = 1.0E-4D;
    static final int SOUL_EROSION_INTERVAL_TICKS = 20;
    static final int SOUL_EROSION_AMOUNT = 10;
    static final double SOUL_EROSION_RADIUS = 8.0D;
    static final double SOUL_EROSION_RADIUS_SQUARED =
            SOUL_EROSION_RADIUS * SOUL_EROSION_RADIUS;
    static final int SOUL_EROSION_WITHER_TICKS = 100;
    static final int SOUL_EROSION_WITHER_AMPLIFIER = 0;
    static final int SOUL_EROSION_WITHER_REFRESH_THRESHOLD_TICKS =
            SOUL_EROSION_INTERVAL_TICKS;

    private final AnimatableInstanceCache animationCache =
            GeckoLibUtil.createInstanceCache(this);
    private boolean crawlAnimationActive;
    private int soulErosionCooldown = SOUL_EROSION_INTERVAL_TICKS;

    public YoungDeepOneEntity(EntityType<? extends YoungDeepOneEntity> entityType, Level level) {
        super(entityType, level);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.moveControl = new SmoothSwimmingMoveControl(
                this,
                85,
                10,
                WATER_SPEED_MODIFIER,
                LAND_SPEED_MODIFIER,
                false
        );
        this.lookControl = new SmoothSwimmingLookControl(this, 20);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new YoungDeepOneMeleeAttackGoal(this));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(
                2,
                new LowestStrengthPlayerTargetGoal(this)
        );
    }

    @Override
    public void tick() {
        super.tick();
        this.tickSoulErosionAura();
    }

    void tickSoulErosionAura() {
        if (!(this.level() instanceof ServerLevel serverLevel)
                || !this.isAlive()) {
            return;
        }
        if (--this.soulErosionCooldown > 0) {
            return;
        }
        this.soulErosionCooldown = SOUL_EROSION_INTERVAL_TICKS;

        for (ServerPlayer player : serverLevel.getPlayers(
                this::canErodeSoul)) {
            boolean hasContainer = SoulEnergyPoolService.hasContainer(player);
            if (hasContainer) {
                SoulEnergyPoolService.removeSoul(
                        player,
                        SOUL_EROSION_AMOUNT
                );
            }
            if (!hasContainer
                    || SoulEnergyPoolService.getCurrentSoul(player) == 0) {
                applySoulErosionWither(player);
            }
        }
    }

    private static void applySoulErosionWither(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.WITHER);
        if (current != null
                && (current.getAmplifier()
                != SOUL_EROSION_WITHER_AMPLIFIER
                || current.getDuration()
                > SOUL_EROSION_WITHER_REFRESH_THRESHOLD_TICKS)) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                MobEffects.WITHER,
                SOUL_EROSION_WITHER_TICKS,
                SOUL_EROSION_WITHER_AMPLIFIER,
                false,
                false,
                true
        ));
    }

    private boolean canErodeSoul(ServerPlayer player) {
        return player.serverLevel() == this.level()
                && !player.isRemoved()
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && this.distanceToSqr(player)
                <= SOUL_EROSION_RADIUS_SQUARED;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            Vec3 movement = this.getDeltaMovement().scale(0.9D);
            if (this.shouldAssistShoreExit()) {
                this.getJumpControl().jump();
                movement = new Vec3(
                        movement.x,
                        Math.max(movement.y, SHORE_ASCENT_SPEED),
                        movement.z
                );
            }
            this.setDeltaMovement(movement);
            return;
        }

        super.travel(travelVector);
    }

    private boolean shouldAssistShoreExit() {
        LivingEntity target = this.getTarget();
        if (target == null
                || !target.isAlive()
                || target.isInWater()
                || this.getFluidHeight(FluidTags.WATER)
                > this.getBbHeight() + SHORE_SURFACE_ALLOWANCE) {
            return false;
        }

        Vec3 towardTarget = new Vec3(
                target.getX() - this.getX(),
                0.0D,
                target.getZ() - this.getZ()
        );
        if (towardTarget.lengthSqr() < MINIMUM_SHORE_DIRECTION_SQUARED) {
            return false;
        }

        Vec3 direction = towardTarget.normalize();
        AABB currentBox = this.getBoundingBox();
        boolean blockedAhead = this.horizontalCollision
                || !this.level().noCollision(
                        this,
                        currentBox.move(
                                direction.x * SHORE_OBSTRUCTION_CHECK_DISTANCE,
                                0.0D,
                                direction.z * SHORE_OBSTRUCTION_CHECK_DISTANCE
                        )
                );
        if (!blockedAhead) {
            return false;
        }

        AABB steppedBox = currentBox.move(
                direction.x * SHORE_LOOK_AHEAD_DISTANCE,
                SHORE_STEP_HEIGHT,
                direction.z * SHORE_LOOK_AHEAD_DISTANCE
        ).deflate(1.0E-4D);
        if (!this.level().noCollision(this, steppedBox)) {
            return false;
        }

        AABB supportProbe = steppedBox.move(
                0.0D,
                -SHORE_SUPPORT_PROBE_DEPTH,
                0.0D
        );
        return !this.level().noCollision(this, supportProbe);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SWIPE_ATTACKING, false);
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
        controllers.add(new AnimationController<>(
                this,
                ATTACK_CONTROLLER,
                0,
                animationState -> PlayState.STOP
        ).triggerableAnim(SWIPE_TRIGGER, SWIPE_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    private PlayState selectMovementAnimation(
            AnimationState<YoungDeepOneEntity> animationState
    ) {
        if (!this.isAlive() || this.isSwipeAttacking()) {
            this.crawlAnimationActive = false;
            return PlayState.STOP;
        }

        double movementSpeedSquared = this.isInWater()
                ? this.getDeltaMovement().lengthSqr()
                : this.getDeltaMovement().horizontalDistanceSqr();
        if (this.crawlAnimationActive) {
            this.crawlAnimationActive =
                    movementSpeedSquared > CRAWL_STOP_SPEED_SQUARED;
        } else {
            this.crawlAnimationActive =
                    movementSpeedSquared > CRAWL_START_SPEED_SQUARED;
        }

        return animationState.setAndContinue(
                this.crawlAnimationActive ? CRAWL_ANIMATION : IDLE_ANIMATION
        );
    }

    boolean isSwipeAttacking() {
        return this.entityData.get(SWIPE_ATTACKING);
    }

    void beginSwipeAttack() {
        this.entityData.set(SWIPE_ATTACKING, true);
        if (!this.level().isClientSide) {
            this.triggerAnim(ATTACK_CONTROLLER, SWIPE_TRIGGER);
        }
    }

    void finishSwipeAttack() {
        this.entityData.set(SWIPE_ATTACKING, false);
    }

    void cancelSwipeAttack() {
        this.entityData.set(SWIPE_ATTACKING, false);
        if (!this.level().isClientSide) {
            this.stopTriggeredAnimation(ATTACK_CONTROLLER, SWIPE_TRIGGER);
        }
    }
}
