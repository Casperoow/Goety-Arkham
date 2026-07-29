package com.casper.goetyarkham.chaosbag;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class ChaosBagTags {
    public static final TagKey<EntityType<?>> GHOULS = TagKey.create(
            Registries.ENTITY_TYPE,
            new ResourceLocation(GoetyArkham.MOD_ID, "ghouls"));

    private ChaosBagTags() {
    }
}
