package com.casper.goetyarkham.entity;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Central entity-type classifications shared by every Goety: Arkham feature. */
public final class ModEntityTypeTags {
    public static final TagKey<EntityType<?>> BOSS_OR_ELITE = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    GoetyArkham.MOD_ID,
                    "boss_or_elite"
            )
    );

    private ModEntityTypeTags() {
    }

    public static boolean isBossOrElite(Entity entity) {
        return isBossOrElite(entity.getType());
    }

    public static boolean isBossOrElite(EntityType<?> entityType) {
        return entityType.is(BOSS_OR_ELITE);
    }
}
