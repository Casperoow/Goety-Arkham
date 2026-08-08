package com.casper.goetyarkham.mixin.client;

import com.casper.goetyarkham.effect.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides both held items (main hand and off hand) for a player under On the
 * Lam's True Invisibility. Vanilla invisibility intentionally still renders
 * held items on invisible entities, so this mod-specific effect needs its
 * own explicit opt-out. See {@link TrueInvisibilityArmorLayerMixin} for the
 * armor counterpart.
 */
@Mixin(ItemInHandLayer.class)
public abstract class TrueInvisibilityItemLayerMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void goetyarkham$hideWhileTrueInvisible(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        if (entity instanceof Player
                && ModEffects.TRUE_INVISIBILITY.isPresent()
                && entity.hasEffect(ModEffects.TRUE_INVISIBILITY.get())) {
            ci.cancel();
        }
    }
}
