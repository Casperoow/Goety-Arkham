package com.casper.goetyarkham.client.model;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.entity.YoungDeepOneEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class YoungDeepOneModel extends GeoModel<YoungDeepOneEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(GoetyArkham.MOD_ID, "geo/young_deep_one.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(GoetyArkham.MOD_ID, "textures/entity/young_deep_one.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(
                    GoetyArkham.MOD_ID,
                    "animations/young_deep_one.animation.json"
            );

    @Override
    public ResourceLocation getModelResource(YoungDeepOneEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(YoungDeepOneEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(YoungDeepOneEntity animatable) {
        return ANIMATION;
    }
}
