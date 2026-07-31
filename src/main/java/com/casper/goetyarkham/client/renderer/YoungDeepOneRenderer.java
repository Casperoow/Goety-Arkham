package com.casper.goetyarkham.client.renderer;

import com.casper.goetyarkham.client.model.YoungDeepOneModel;
import com.casper.goetyarkham.entity.YoungDeepOneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class YoungDeepOneRenderer extends GeoEntityRenderer<YoungDeepOneEntity> {
    public YoungDeepOneRenderer(EntityRendererProvider.Context context) {
        super(context, new YoungDeepOneModel());
        this.shadowRadius = 0.6F;
    }
}
