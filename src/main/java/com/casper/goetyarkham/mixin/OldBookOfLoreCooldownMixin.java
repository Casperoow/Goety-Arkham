package com.casper.goetyarkham.mixin;

import com.Polarice3.Goety.utils.SEHelper;
import com.casper.goetyarkham.item.OldBookOfLoreService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts Goety's single unified entry point for applying a focus's
 * cast cooldown - {@code SEHelper.addCooldown} - so the Old Book of Lore
 * can redirect that cooldown onto itself instead. This is the point where
 * the cooldown argument already reflects every modification Goety (wand
 * cooldown attributes, per-spell custom cooldowns) and any compatibility
 * mod applies; nothing here recomputes it.
 *
 * <p>Only ever acts when {@code player} is a real {@link ServerPlayer}: the
 * client-side mirror call from Goety's own cooldown-sync packet handler
 * passes a client {@code LocalPlayer}, which this check naturally excludes,
 * keeping the redirect decision entirely server-authoritative.</p>
 */
@Mixin(value = SEHelper.class, remap = false)
public abstract class OldBookOfLoreCooldownMixin {
    @Inject(method = "addCooldown", at = @At("HEAD"), cancellable = true)
    private static void goetyarkham$redirectFocusCooldownToOldBookOfLore(
            Player player, Item item, int duration, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer
                && OldBookOfLoreService.tryRedirectCooldown(serverPlayer, item, duration)) {
            callback.cancel();
        }
    }
}
