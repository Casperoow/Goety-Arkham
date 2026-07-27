package com.casper.goetyarkham.mixin.client;

import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.items.ModItems;
import com.casper.goetyarkham.client.ClientSoulPool;
import com.casper.goetyarkham.soul.SoulPoolSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CursedCageBlockEntity.class, remap = false)
public abstract class CursedCageBlockEntityClientMixin {
    @Shadow
    public abstract ItemStack getItem();

    @Shadow
    public abstract Player getOwner();

    @Inject(method = "getSouls", at = @At("HEAD"), cancellable = true)
    private void goetyarkham$getLocalTransferGemSouls(
            CallbackInfoReturnable<Integer> callback) {
        CursedCageBlockEntity cage =
                (CursedCageBlockEntity) (Object) this;
        if (cage.getLevel() == null
                || !cage.getLevel().isClientSide
                || !getItem().is(ModItems.SOUL_TRANSFER.get())) {
            return;
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        Player owner = getOwner();
        if (localPlayer == null
                || owner == null
                || !localPlayer.getUUID().equals(owner.getUUID())) {
            return;
        }

        SoulPoolSnapshot snapshot = ClientSoulPool.snapshot();
        callback.setReturnValue(
                snapshot.arcaMode() ? snapshot.currentSoul() : 0);
    }
}
