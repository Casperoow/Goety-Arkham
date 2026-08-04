package com.casper.goetyarkham.effect;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.attribute.StatAttributeBridge;
import com.casper.goetyarkham.strength.StrengthEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the Player Stats Strength mirror (and its downstream attack-damage
 * and critical-hit formulas) in sync with the Rita Chandler's Blessing
 * effect's own lifecycle.
 *
 * <p>Unlike {@link DreamsOfRlyehEffectEvents}, this deliberately calls only
 * {@link StatAttributeBridge#syncToAttributes} and
 * {@link StrengthEffects#refreshStrengthEffects} instead of the broader
 * {@code PlayerStatsService#sync}: the blessing only ever changes Strength,
 * so there is no reason to also re-run the unrelated Agility/Willpower/
 * Revelation refreshes or push a stats network packet whose payload (the raw
 * base/equipment/temporary/derived components) never actually changes when
 * this effect toggles - and the Willpower refresh path in particular reaches
 * into Goety's own soul-energy client sync, which assumes a real player
 * connection and is not this effect's concern to keep working under every
 * caller.</p>
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class RitaChandlersBlessingEffectEvents {
    private RitaChandlersBlessingEffectEvents() {
    }

    @SubscribeEvent
    public static void effectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffectInstance().getEffect()
                == ModEffects.RITA_CHANDLERS_BLESSING.get()) {
            refreshStrengthMirrors(player);
        }
    }

    @SubscribeEvent
    public static void effectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect()
                == ModEffects.RITA_CHANDLERS_BLESSING.get()) {
            refreshAfterRemoval(event);
        }
    }

    @SubscribeEvent
    public static void effectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() == ModEffects.RITA_CHANDLERS_BLESSING.get()) {
            refreshAfterRemoval(event);
        }
    }

    private static void refreshAfterRemoval(MobEffectEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getServer().execute(() -> refreshStrengthMirrors(player));
        }
    }

    private static void refreshStrengthMirrors(ServerPlayer player) {
        StatAttributeBridge.syncToAttributes(player);
        StrengthEffects.refreshStrengthEffects(player);
    }
}
