package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side stack and damage logic for a worn Beat Cop's Token: the
 * wearer's exact equipped stack gains 1 stack of Beat Cop's Retaliation per
 * full point of final effective damage taken (up to 3), and the wearer's
 * next successful direct melee attack consumes all stacks for +2 bonus
 * damage each, added on top of ordinary melee damage before armor and
 * resistance are applied.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class BeatCopsTokenEffectEvents {
    /**
     * The primary target of each player's most recent direct attack
     * ({@link AttackEntityEvent} fires once per {@code Player#attack} call,
     * before any vanilla sweeping-edge secondary hits, which reuse the same
     * damage source but target different entities). Bonus melee damage only
     * ever applies to this exact target. The entry is removed as soon as it
     * is consulted, so a later, unrelated hit on the same entity can never
     * reuse it, and stale entries are simply overwritten by each player's
     * next attack. Bounded by the number of online players and cleared on
     * logout, so nothing here persists across sessions or leaks memory.
     */
    private static final Map<UUID, Entity> PENDING_MELEE_TARGETS = new HashMap<>();

    private BeatCopsTokenEffectEvents() {
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_MELEE_TARGETS.put(player.getUUID(), event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_MELEE_TARGETS.remove(event.getEntity().getUUID());
    }

    /**
     * Gains stacks from the wearer's own final, effective damage taken.
     * {@link LivingDamageEvent} fires once per {@code LivingEntity#hurt}
     * call with the amount actually subtracted from health, after armor,
     * resistance/magic reduction, and absorption have all been applied, and
     * only when that amount is non-zero. Canceled hurt calls and fully
     * immune/invulnerable hits never reach this event at all, so neither
     * needs to be filtered out here, and client-side prediction never
     * reaches it either since {@code LivingEntity#hurt} returns immediately
     * on the client.
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer wearer)
                || event.getAmount() <= 0.0F) {
            return;
        }

        findWornToken(wearer).ifPresent(token ->
                BeatCopsTokenItem.addStacks(token, (int) event.getAmount()));
    }

    /**
     * Consumes stacks for bonus melee damage. Uses {@link LivingHurtEvent}
     * (fired before armor/resistance reduction) rather than
     * {@link LivingDamageEvent}, so the bonus becomes part of ordinary melee
     * damage and is still subject to the target's armor and resistances,
     * instead of triggering a second, separate {@code hurt()} call.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource source = event.getSource();
        if (!source.is(DamageTypes.PLAYER_ATTACK)) {
            // Excludes projectiles/guns, spells and rituals, explosions,
            // thorns, fire, and other damage-over-time sources, none of
            // which use the vanilla player_attack damage type.
            return;
        }

        if (!(source.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        Entity primaryTarget = PENDING_MELEE_TARGETS.remove(attacker.getUUID());
        if (primaryTarget != event.getEntity()) {
            // Either a vanilla sweeping-edge secondary hit (same damage
            // source, different entity) or a stale/absent entry; only the
            // attack's actual primary target can consume stacks.
            return;
        }

        findWornToken(attacker).ifPresent(token -> {
            int stacks = BeatCopsTokenItem.getStacks(token);
            if (stacks <= 0) {
                return;
            }
            event.setAmount(event.getAmount() + stacks * BeatCopsTokenItem.DAMAGE_PER_STACK);
            BeatCopsTokenItem.clearStacks(token);
        });
    }

    /**
     * The Beat Cop's Token currently occupying the player's token slot, if
     * any. Resolves to the live {@link ItemStack} instance backing that
     * Curios slot (not a copy, and not merely the first matching stack found
     * anywhere in the player's inventory), so mutations apply to, and are
     * read from, only the stack actually worn.
     */
    private static Optional<ItemStack> findWornToken(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.TOKEN))
                .map(BeatCopsTokenEffectEvents::firstWornToken)
                .filter(stack -> !stack.isEmpty());
    }

    private static ItemStack firstWornToken(ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (stack.is(ModItems.BEAT_COPS_TOKEN.get()) && !stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
