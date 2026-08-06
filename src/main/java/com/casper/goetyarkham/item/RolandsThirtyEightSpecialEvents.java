package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.EquipmentStatsService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Server-side kill tracking for Roland's .38 Special and Cover Up's periodic Soul Energy drain. */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class RolandsThirtyEightSpecialEvents {
    private RolandsThirtyEightSpecialEvents() {
    }

    /**
     * Credits a kill to every Roland's .38 Special currently worn by the
     * killing player. {@link net.minecraft.world.damagesource.DamageSource#getEntity()}
     * resolves to the indirect/causing entity (the shooter or spellcaster),
     * not a direct projectile/bullet entity, so this correctly credits TACZ
     * and other projectile-based kills to the player, not the projectile.
     * {@code ServerPlayer} only ever exists server-side, so no explicit
     * client-side guard is needed.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            creditKill(killer);
        }
    }

    private static void creditKill(ServerPlayer killer) {
        CuriosApi.getCuriosInventory(killer).resolve().ifPresent(inventory ->
                inventory.getStacksHandler(CurioSlotIds.HANDS).ifPresent(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    boolean changed = false;
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        ItemStack stack = stacks.getStackInSlot(slot);
                        if (stack.is(ModItems.ROLANDS_38_SPECIAL.get())) {
                            RolandsThirtyEightSpecialItem.addKillBonus(stack);
                            changed = true;
                        }
                    }
                    if (changed) {
                        EquipmentStatsService.refresh(killer);
                    }
                }));
    }

    /**
     * Cover Up's drain must be paced by the server's own tick count, never a
     * client tick, so this checks {@code ServerPlayer#tickCount} rather than
     * running its own timer.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CoverUpSoulDrainService.INTERVAL_TICKS != 0) {
            return;
        }
        CoverUpSoulDrainService.tick(player);
    }
}
