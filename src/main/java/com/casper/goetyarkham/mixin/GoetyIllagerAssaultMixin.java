package com.casper.goetyarkham.mixin;

import com.Polarice3.Goety.common.events.IllagerSpawner;
import com.casper.goetyarkham.illager_treachery.IllagerTreacheryApi;
import com.casper.goetyarkham.illager_treachery.TriggerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = IllagerSpawner.class, remap = false)
public abstract class GoetyIllagerAssaultMixin {
    @Inject(method = "spawnRaider", at = @At("RETURN"))
    private void goetyarkham$onAssaultRaiderSpawned(
            IllagerSpawner.IllagerDataType dataType,
            EntityType<?> entityType,
            ServerLevel level,
            BlockPos position,
            RandomSource random,
            int soulAmount,
            ServerPlayer target,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        if (!callbackInfo.getReturnValueZ()) {
            return;
        }
        String instanceKey = "illager_assault:"
                + level.dimension().location()
                + ":" + level.getGameTime()
                + ":" + System.identityHashCode(this);
        IllagerTreacheryApi.submitDeduplicated(
                level.getServer(),
                TriggerSource.ILLAGER_ASSAULT,
                List.of(target),
                instanceKey
        );
    }
}
