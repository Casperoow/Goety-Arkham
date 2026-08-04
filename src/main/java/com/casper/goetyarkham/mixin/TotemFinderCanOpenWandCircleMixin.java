package com.casper.goetyarkham.mixin;

import com.Polarice3.Goety.utils.TotemFinder;
import com.casper.goetyarkham.curios.FocusCurioService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the wand focus radial menu open when the player's only available
 * focus lives in a Curios {@code focus} slot. {@code TotemFinder} is common
 * (not client-only) code that already checks the focus bag, the inventory,
 * and the wand itself; this only adds the fourth source.
 */
@Mixin(value = TotemFinder.class, remap = false)
public abstract class TotemFinderCanOpenWandCircleMixin {
    // TotemFinder.canOpenWandCircle(Player): the sole gate ClientEvents uses
    // before opening FocusRadialMenuScreen from the wand-circle keybind.
    @Inject(method = "canOpenWandCircle", at = @At("RETURN"), cancellable = true, require = 1)
    private static void goetyarkham$allowOpenFromFocusSlot(
            Player player, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && FocusCurioService.hasAnyFocus(player)) {
            callback.setReturnValue(true);
        }
    }
}
