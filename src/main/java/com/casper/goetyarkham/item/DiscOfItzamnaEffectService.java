package com.casper.goetyarkham.item;

import com.Polarice3.Goety.api.entities.IOwned;
import com.Polarice3.Goety.api.entities.ally.IServant;
import com.Polarice3.Goety.utils.SEHelper;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.entity.DiscOfItzamnaAvoidPlayerGoal;
import com.casper.goetyarkham.entity.ModEntityTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Comparator;
import java.util.List;

/** Server-authoritative target filtering and AI wiring for the Disc of Itzamna. */
public final class DiscOfItzamnaEffectService {
    public static final double EFFECT_RADIUS = 10.0D;
    public static final double EFFECT_RADIUS_SQUARED =
            EFFECT_RADIUS * EFFECT_RADIUS;
    private static final int AVOID_GOAL_PRIORITY = 0;
    private static final double WALK_SPEED_MODIFIER = 1.0D;
    private static final double SPRINT_SPEED_MODIFIER = 1.2D;

    private DiscOfItzamnaEffectService() {
    }

    /**
     * Installs the reusable avoidance goal on each current target and reports
     * whether this wearer's disc is actively affecting at least one enemy.
     */
    public static boolean activateFor(Player wearer) {
        List<Mob> targets = wearer.level().getEntitiesOfClass(
                Mob.class,
                wearer.getBoundingBox().inflate(EFFECT_RADIUS),
                mob -> isEligibleEnemy(wearer, mob)
        );
        targets.forEach(DiscOfItzamnaEffectService::ensureAvoidGoal);
        return !targets.isEmpty();
    }

    /** The single authoritative definition of an enemy affected by the disc. */
    public static boolean isEligibleEnemy(Player wearer, Mob mob) {
        return wearer.isAlive()
                && mob.isAlive()
                && mob instanceof Enemy
                && wearer.distanceToSqr(mob) <= EFFECT_RADIUS_SQUARED
                && !isPlayerAllyOrServant(wearer, mob)
                && !ModEntityTypeTags.isBossOrElite(mob);
    }

    public static Player findNearestActiveWearer(Mob mob) {
        return mob.level().players().stream()
                .filter(Player::isAlive)
                .filter(DiscOfItzamnaEffectService::isWearingActiveDisc)
                .filter(player -> isEligibleEnemy(player, mob))
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    /** Only the functional stack collection of the Curios charm handler counts. */
    public static boolean isWearingActiveDisc(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(
                        CurioSlotIds.CHARM))
                .map(handler -> {
                    for (int slot = 0;
                         slot < handler.getStacks().getSlots();
                         slot++) {
                        ItemStack stack = handler.getStacks()
                                .getStackInSlot(slot);
                        if (stack.is(ModItems.DISC_OF_ITZAMNA.get())
                                && !stack.isEmpty()
                                && stack.getDamageValue()
                                < stack.getMaxDamage()) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    public static boolean isPlayerAllyOrServant(Player player, Mob mob) {
        if (player.isAlliedTo(mob)
                || mob.isAlliedTo(player)
                || SEHelper.isAlly(player, mob)) {
            return true;
        }
        return isPlayerOwnedOrSummoned(mob);
    }

    /** Reuses Goety and vanilla ownership contracts instead of entity ID lists. */
    public static boolean isPlayerOwnedOrSummoned(Mob mob) {
        if (mob instanceof IServant) {
            return true;
        }
        if (mob instanceof IOwned owned
                && (owned.getTrueOwner() instanceof Player
                || owned.getMasterOwner() instanceof Player)) {
            return true;
        }
        return mob instanceof OwnableEntity ownable
                && ownable.getOwnerUUID() != null;
    }

    private static void ensureAvoidGoal(Mob mob) {
        boolean alreadyInstalled = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal()
                        instanceof DiscOfItzamnaAvoidPlayerGoal);
        if (!alreadyInstalled) {
            mob.goalSelector.addGoal(
                    AVOID_GOAL_PRIORITY,
                    new DiscOfItzamnaAvoidPlayerGoal(
                            mob,
                            WALK_SPEED_MODIFIER,
                            SPRINT_SPEED_MODIFIER
                    )
            );
        }
    }
}
