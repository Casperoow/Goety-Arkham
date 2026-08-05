package com.casper.goetyarkham.mixin;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.SharedBonusSlotStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;
import top.theillusivec4.curios.common.inventory.DynamicStackHandler;

import java.util.function.Function;

/**
 * Makes a player's {@link CurioSlotIds#SKILL_BONUS} {@code
 * CurioStacksHandler} build its backing stack handler(s) as {@link
 * SharedBonusSlotStackHandler} - a plain {@code DynamicStackHandler}
 * subclass that caps to 1 item per slot - instead of a stock {@code
 * DynamicStackHandler}. Curios itself exposes no data-driven or API hook
 * for "this slot type's item limit is N regardless of the item's own max
 * stack size" (its {@code SlotData} only carries slot *count*, not a
 * per-item stack cap), so this is the smallest available interception
 * point: redirect the two {@code new DynamicStackHandler(...)} calls made
 * by {@code CurioStacksHandler}'s own constructor (one for the real
 * stack handler, one for the cosmetic one - both redirected uniformly,
 * since capping the cosmetic mirror too is harmless) to construct our
 * subclass instead, whenever this handler's own identifier (already
 * assigned to the {@code identifier} field earlier in the same
 * constructor, readable here via the public {@link ICurioStacksHandler}
 * interface) is {@code skill_bonus}.
 */
@Mixin(value = CurioStacksHandler.class, remap = false)
public abstract class SharedBonusSlotHandlerMixin {
    @Redirect(
            method = "<init>(Ltop/theillusivec4/curios/api/type/capability/ICuriosItemHandler;"
                    + "Ljava/lang/String;IZZZLtop/theillusivec4/curios/api/type/capability/ICurio$DropRule;)V",
            at = @At(
                    value = "NEW",
                    target = "top/theillusivec4/curios/common/inventory/DynamicStackHandler"))
    private DynamicStackHandler goetyarkham$capSharedBonusSlot(
            int size, Function<Integer, SlotContext> ctxBuilder) {
        ICurioStacksHandler self = (ICurioStacksHandler) (Object) this;
        if (CurioSlotIds.SKILL_BONUS.equals(self.getIdentifier())) {
            return new SharedBonusSlotStackHandler(size, ctxBuilder);
        }
        return new DynamicStackHandler(size, ctxBuilder);
    }
}
