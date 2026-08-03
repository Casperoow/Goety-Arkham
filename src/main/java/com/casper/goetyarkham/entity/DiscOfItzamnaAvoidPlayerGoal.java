package com.casper.goetyarkham.entity;

import com.casper.goetyarkham.item.DiscOfItzamnaEffectService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A server-side avoidance goal modeled on vanilla's {@code AvoidEntityGoal}.
 * The avoided player is resolved dynamically so equipment changes and multiple
 * disc wearers do not require replacing goals on nearby mobs.
 */
public final class DiscOfItzamnaAvoidPlayerGoal extends Goal {
    private static final int ESCAPE_HORIZONTAL_RANGE = 16;
    private static final int ESCAPE_VERTICAL_RANGE = 7;
    private static final int DIRECT_ESCAPE_TICKS = 40;

    private final Mob mob;
    private final PathNavigation navigation;
    private final double walkSpeedModifier;
    private final double sprintSpeedModifier;
    private Player wearer;
    private Path path;
    private Vec3 directEscapeTarget;
    private int directEscapeTicks;

    public DiscOfItzamnaAvoidPlayerGoal(
            Mob mob,
            double walkSpeedModifier,
            double sprintSpeedModifier) {
        this.mob = mob;
        this.navigation = mob.getNavigation();
        this.walkSpeedModifier = walkSpeedModifier;
        this.sprintSpeedModifier = sprintSpeedModifier;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        wearer = DiscOfItzamnaEffectService.findNearestActiveWearer(mob);
        if (wearer == null) {
            return false;
        }

        Vec3 escapePosition = findEscapePosition(wearer);
        if (escapePosition == null
                || wearer.distanceToSqr(escapePosition)
                <= wearer.distanceToSqr(mob)) {
            wearer = null;
            return false;
        }

        path = navigation.createPath(BlockPos.containing(escapePosition), 0);
        directEscapeTarget = path == null ? escapePosition : null;
        directEscapeTicks = 0;
        return path != null || !(mob instanceof PathfinderMob);
    }

    @Override
    public boolean canContinueToUse() {
        if (wearer == null
                || !DiscOfItzamnaEffectService.isWearingActiveDisc(wearer)
                || !DiscOfItzamnaEffectService.isEligibleEnemy(wearer, mob)) {
            return false;
        }
        if (path != null) {
            return !navigation.isDone();
        }
        return directEscapeTarget != null
                && directEscapeTicks < DIRECT_ESCAPE_TICKS
                && mob.distanceToSqr(directEscapeTarget) > 4.0D;
    }

    @Override
    public void start() {
        if (path != null) {
            navigation.moveTo(path, walkSpeedModifier);
        } else {
            moveDirectlyAway();
        }
    }

    @Override
    public void stop() {
        navigation.stop();
        wearer = null;
        path = null;
        directEscapeTarget = null;
        directEscapeTicks = 0;
    }

    @Override
    public void tick() {
        double speed = wearer != null && mob.distanceToSqr(wearer) < 49.0D
                ? sprintSpeedModifier
                : walkSpeedModifier;
        if (path != null) {
            navigation.setSpeedModifier(speed);
        } else {
            directEscapeTicks++;
            moveDirectlyAway(speed);
        }
    }

    private Vec3 findEscapePosition(Player activeWearer) {
        if (mob instanceof PathfinderMob pathfinderMob) {
            return DefaultRandomPos.getPosAway(
                    pathfinderMob,
                    ESCAPE_HORIZONTAL_RANGE,
                    ESCAPE_VERTICAL_RANGE,
                    activeWearer.position()
            );
        }

        Vec3 away = mob.position().subtract(activeWearer.position());
        if (away.lengthSqr() < 1.0E-4D) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        }
        return mob.position().add(away.normalize().scale(12.0D));
    }

    private void moveDirectlyAway() {
        moveDirectlyAway(walkSpeedModifier);
    }

    private void moveDirectlyAway(double speed) {
        if (directEscapeTarget != null) {
            mob.getMoveControl().setWantedPosition(
                    directEscapeTarget.x,
                    directEscapeTarget.y,
                    directEscapeTarget.z,
                    speed
            );
        }
    }
}
