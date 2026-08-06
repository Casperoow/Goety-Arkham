package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.stats.PlayerStatsService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The four-stat system otherwise only resyncs on an actual stat/equipment
 * (Curios) change - see {@code PlayerStatsProvider#setOnChanged} and {@link
 * com.casper.goetyarkham.stats.EquipmentStatsService}. A vanilla hotbar item
 * such as the Switchblade has no such hook, so this forces one resync
 * whenever the main-hand stack changes (switch, drop, pickup, or it being
 * cleared on death). Forge fires {@link LivingEquipmentChangeEvent} for
 * every equipment slot, including {@link EquipmentSlot#MAINHAND}, once per
 * tick that the slot's stack actually differs from last tick's - this is
 * the same edge-triggered pattern {@code CurioChangeEvent} plays for Curios
 * slots, just for vanilla hand/armor slots instead.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class SwitchbladeEvents {
    private SwitchbladeEvents() {
    }

    @SubscribeEvent
    public static void onMainHandChanged(LivingEquipmentChangeEvent event) {
        if (event.getSlot() == EquipmentSlot.MAINHAND
                && event.getEntity() instanceof ServerPlayer player) {
            PlayerStatsService.sync(player);
        }
    }
}
