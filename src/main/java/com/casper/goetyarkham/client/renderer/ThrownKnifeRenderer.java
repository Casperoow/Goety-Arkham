package com.casper.goetyarkham.client.renderer;

import com.casper.goetyarkham.entity.ThrownKnifeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Renders the in-flight knife as its own item model (not a custom entity
 * model), oriented toward its velocity the same way {@code AbstractArrow}
 * itself already tracks {@code xRot}/{@code yRot} every tick - this class
 * only reads that rotation and delegates to the vanilla {@link
 * net.minecraft.client.renderer.ItemRenderer}, mirroring vanilla's own
 * {@code TridentRenderer} rotation math. Lives under {@code client.renderer}
 * and is registered only from the {@code Dist.CLIENT}-only {@code
 * ClientModEvents}, so this class is never loaded on a dedicated server.
 */
public final class ThrownKnifeRenderer extends EntityRenderer<ThrownKnifeEntity> {
    public ThrownKnifeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            ThrownKnifeEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        Minecraft.getInstance().getItemRenderer().renderStatic(
                entity.getKnifeItem(),
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownKnifeEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
