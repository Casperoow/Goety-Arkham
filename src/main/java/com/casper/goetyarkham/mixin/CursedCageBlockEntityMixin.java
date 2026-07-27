package com.casper.goetyarkham.mixin;

import com.Polarice3.Goety.common.blocks.entities.ArcaBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.items.ModItems;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.soul.SoulTransferArcaValidator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = CursedCageBlockEntity.class, remap = false)
public abstract class CursedCageBlockEntityMixin {
    @Shadow
    public abstract ItemStack getItem();

    @Shadow
    public abstract Player getOwner();

    @Shadow
    public abstract void generateParticles();

    @Shadow
    public abstract void markUpdated();

    @Inject(method = "getSouls", at = @At("HEAD"), cancellable = true)
    private void goetyarkham$getTransferGemSouls(
            CallbackInfoReturnable<Integer> callback) {
        if (!isSoulTransferGem()) {
            return;
        }

        Player owner = getOwner();
        if (owner instanceof ServerPlayer serverPlayer) {
            int souls = SoulTransferArcaValidator.findValidArca(serverPlayer)
                    .map(arca -> SoulEnergyPoolService.getCurrentSoul(serverPlayer))
                    .orElse(0);
            callback.setReturnValue(souls);
        }
    }

    @Inject(method = "decreaseSouls", at = @At("HEAD"), cancellable = true)
    private void goetyarkham$consumeTransferGemSouls(
            int amount, CallbackInfo callback) {
        if (!isSoulTransferGem()) {
            return;
        }

        Player owner = getOwner();
        if (owner instanceof ServerPlayer serverPlayer) {
            Optional<ArcaBlockEntity> arca =
                    SoulTransferArcaValidator.findValidArca(serverPlayer);
            if (amount > 0
                    && arca.isPresent()
                    && SoulEnergyPoolService.hasSoul(serverPlayer, amount)
                    && SoulEnergyPoolService.removeSoulFromGoety(
                            serverPlayer, amount) > 0) {
                arca.get().generateParticles();
                generateParticles();
            }
            markUpdated();
        }
        callback.cancel();
    }

    private boolean isSoulTransferGem() {
        return getItem().is(ModItems.SOUL_TRANSFER.get());
    }
}
