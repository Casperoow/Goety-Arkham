package com.casper.goetyarkham.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.casper.goetyarkham.client.model.YoungDeepOneModel;
import com.casper.goetyarkham.entity.YoungDeepOneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class YoungDeepOneRenderer extends GeoEntityRenderer<YoungDeepOneEntity> {
    private static final float MODEL_BACKWARD_OFFSET = -0.3F;

    public YoungDeepOneRenderer(EntityRendererProvider.Context context) {
        super(context, new YoungDeepOneModel());
        this.shadowRadius = 0.6F;
    }

    @Override
    public void preRender(
            PoseStack poseStack,
            YoungDeepOneEntity animatable,
            BakedGeoModel model,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        // GeckoLib has already applied entity yaw here. In model-local space,
        // +Z points toward the creature's rear, so this follows every turn.
        poseStack.translate(0.0F, 0.0F, MODEL_BACKWARD_OFFSET);
        super.preRender(
                poseStack,
                animatable,
                model,
                bufferSource,
                buffer,
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                red,
                green,
                blue,
                alpha
        );
    }
}
