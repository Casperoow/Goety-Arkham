package com.casper.goetyarkham.item;

import com.Polarice3.Goety.utils.OwnedDamageSource;
import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;

/**
 * Central Sneak Attack logic: resolving the real attacking {@link
 * ServerPlayer} behind any {@link DamageSource} - melee, vanilla
 * projectiles, TACZ bullets, and Goety spell/summon damage all included -
 * and deciding whether a given hit qualifies for the double-damage bonus.
 * {@link SneakAttackEvents} is the only caller that actually mutates
 * damage; everything here is a pure query against live state (nothing is
 * cached), so equip/unequip and target healing are always reflected
 * correctly at the instant a hit resolves.
 */
public final class SneakAttackService {
    public static final float DAMAGE_MULTIPLIER = 2.0F;
    private static final float HEALTH_EPSILON = 1.0e-4F;

    private SneakAttackService() {
    }

    /**
     * Resolves {@code source} back to the {@link ServerPlayer} that
     * ultimately caused it, or empty if none can be reliably traced. Checked
     * in order:
     * <ol>
     *   <li>{@link OwnedDamageSource#getOwner()} - Goety's own indirect
     *   damage sources (e.g. a summoned servant's attack, built via {@code
     *   ModDamageSource#summonAttack}) construct the underlying vanilla
     *   {@link DamageSource} with the summoned creature as both direct and
     *   causing entity, so {@link DamageSource#getEntity()} resolves to the
     *   creature; only the separate {@code owner} field carries the actual
     *   casting player.</li>
     *   <li>{@link DamageSource#getEntity()} (the vanilla "causing" entity)
     *   - covers ordinary melee (causing == direct == the player), vanilla
     *   projectiles (arrows/tridents/the mod's own {@code ThrownKnifeEntity}
     *   all build their damage source as {@code direct = projectile,
     *   causing = owner}), TACZ bullets ({@code ModDamageTypes.Sources.bullet}
     *   passes the shooter as the causing entity), and every two-entity
     *   Goety spell factory in {@code ModDamageSource} (e.g. {@code
     *   magicBolt}/{@code sword}, which this addon's own {@code
     *   HeirloomSoulSpendEffectService} already calls with the caster as
     *   both entities).</li>
     *   <li>{@link DamageSource#getDirectEntity()} as a {@link Projectile}
     *   - a defensive fallback for any projectile whose causing entity was
     *   not set to its owner.</li>
     * </ol>
     */
    public static Optional<ServerPlayer> resolveAttackingPlayer(DamageSource source) {
        if (source instanceof OwnedDamageSource owned
                && owned.getOwner() instanceof ServerPlayer ownerOfIndirectDamage) {
            return Optional.of(ownerOfIndirectDamage);
        }

        Entity causing = source.getEntity();
        if (causing instanceof ServerPlayer causingPlayer) {
            return Optional.of(causingPlayer);
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer projectileOwner) {
            return Optional.of(projectileOwner);
        }

        return Optional.empty();
    }

    /** True while {@code player} has a Sneak Attack functionally equipped in {@link CurioSlotIds#ASSET}. */
    public static boolean isWearing(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.ASSET))
                .map(SneakAttackService::hasSneakAttack)
                .orElse(false);
    }

    private static boolean hasSneakAttack(ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (stacks.getStackInSlot(slot).is(ModItems.SNEAK_ATTACK.get())) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code target} was at (or effectively at, allowing for float error) full health before this hit. */
    public static boolean isTargetAtFullHealth(LivingEntity target) {
        return target.getHealth() >= target.getMaxHealth() - HEALTH_EPSILON;
    }

    /**
     * True when this exact hit should be doubled: {@code target} was at
     * full health immediately before the hit, and the resolved attacker
     * currently has Sneak Attack functionally equipped. Both checks read
     * live state at the moment of the hit - never cached - so a target
     * healing back to full re-qualifies on the next hit, and a projectile
     * fired before Sneak Attack was equipped (or after it was unequipped)
     * is judged by whichever equip state is true when it actually lands.
     */
    public static boolean qualifies(DamageSource source, LivingEntity target) {
        if (!isTargetAtFullHealth(target)) {
            return false;
        }
        return resolveAttackingPlayer(source)
                .filter(SneakAttackService::isWearing)
                .isPresent();
    }
}
