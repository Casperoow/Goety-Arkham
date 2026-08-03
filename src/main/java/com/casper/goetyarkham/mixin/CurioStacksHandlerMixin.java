package com.casper.goetyarkham.mixin;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.weakness.ILockedWeakness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

/** Prevents Curios size recalculation from ejecting a locked weakness. */
@Mixin(value = CurioStacksHandler.class, remap = false)
public abstract class CurioStacksHandlerMixin {
    @Inject(method = "resize", at = @At("HEAD"), cancellable = true)
    private void goetyarkham$keepLockedWeaknessesInSlot(
            int newSize, CallbackInfo callback) {
        ICurioStacksHandler handler =
                (ICurioStacksHandler) (Object) this;
        if (!CurioSlotIds.WEAKNESS.equals(handler.getIdentifier())) {
            return;
        }
        IDynamicStackHandler stacks = handler.getStacks();
        if (newSize >= stacks.getSlots()) {
            return;
        }
        for (int slot = Math.max(0, newSize);
             slot < stacks.getSlots();
             slot++) {
            if (stacks.getStackInSlot(slot).getItem()
                    instanceof ILockedWeakness) {
                GoetyArkham.LOGGER.warn(
                        "[Weakness] Blocked automatic slot shrink that would eject locked item from slot {}",
                        slot);
                callback.cancel();
                return;
            }
        }
    }
}
