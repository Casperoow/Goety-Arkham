package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Server-side redirect logic for a worn Aquinnah's Token: whenever the
 * wearer takes damage whose direct source is a living entity (never a
 * projectile, explosion, or environmental hazard), the hit is canceled, one
 * durability point is consumed (subject to Unbreaking), and the same amount
 * of damage is applied back to the attacker via the original damage source.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class AquinnahsTokenEffectEvents {
    /**
     * Guards against the redirected hit re-entering this handler: a second
     * token-wearer retaliating against the first, or the same token trying
     * to intercept its own transferred damage. Only ever read/written from
     * the server thread that processes entity damage, so a plain ThreadLocal
     * is sufficient and leaves no cross-player or persistent state behind.
     */
    private static final ThreadLocal<Boolean> REDIRECTING =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AquinnahsTokenEffectEvents() {
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled() || Boolean.TRUE.equals(REDIRECTING.get())) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer wearer)
                || wearer.level().isClientSide
                || event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof LivingEntity attacker) || attacker == wearer) {
            // Projectiles, explosions, falling/environmental damage, and
            // sourceless damage all fail this check, since only a living
            // entity's own direct attack should be redirected.
            return;
        }

        ItemStack token = findAvailableToken(wearer);
        if (token == null) {
            return;
        }

        event.setCanceled(true);
        consumeDurability(token, wearer);
        redirect(attacker, source, event.getAmount());
    }

    private static void redirect(
            LivingEntity attacker, DamageSource source, float amount) {
        REDIRECTING.set(Boolean.TRUE);
        try {
            attacker.hurt(source, amount);
        } finally {
            REDIRECTING.set(Boolean.FALSE);
        }
    }

    /**
     * The first token in the wearer's functional token slot(s) that still
     * has at least 1 durability point. If several tokens are equipped,
     * only this one is touched, so a single attack can never be transferred
     * more than once or drain more than one token.
     */
    private static ItemStack findAvailableToken(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.TOKEN))
                .map(AquinnahsTokenEffectEvents::firstAvailableToken)
                .filter(stack -> !stack.isEmpty())
                .orElse(null);
    }

    private static ItemStack firstAvailableToken(ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (stack.is(ModItems.AQUINNAHS_TOKEN.get())
                    && stack.getDamageValue() < stack.getMaxDamage()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Uses vanilla's own {@code ItemStack#hurt} to apply the durability
     * point, so Unbreaking's chance to ignore the drop is evaluated exactly
     * the way it is for every vanilla tool or armor piece. Its returned
     * "should break" flag is deliberately ignored instead of being handed to
     * {@code hurtAndBreak}, which is what destroys a stack at maximum
     * damage: reaching maximum damage here just leaves the token at its cap,
     * still occupying its Curios slot and still granting its attribute
     * bonuses, only no longer eligible for a redirect. Findable-token
     * gating in {@link #findAvailableToken} guarantees this is only ever
     * called with 1 point of headroom left, so damage never exceeds max.
     */
    static void consumeDurability(ItemStack stack, ServerPlayer player) {
        stack.hurt(1, player.getRandom(), player);
    }
}
