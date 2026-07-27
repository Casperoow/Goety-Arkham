package com.casper.goetyarkham.mixin;

import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.casper.goetyarkham.illager_treachery.IllagerTreacheryApi;
import com.casper.goetyarkham.illager_treachery.TriggerSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = DarkAltarBlockEntity.class, remap = false)
public abstract class GoetyRitualCompletionMixin {
    @Inject(
            method = "stopRitual",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/Polarice3/Goety/common/ritual/Ritual;finish(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/Polarice3/Goety/common/blocks/entities/DarkAltarBlockEntity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER))
    private void goetyarkham$onRitualFinished(
            boolean completed,
            CallbackInfo callbackInfo) {
        DarkAltarBlockEntity altar =
                (DarkAltarBlockEntity) (Object) this;
        if (!completed
                || !(altar.getLevel() instanceof ServerLevel level)
                || !(altar.castingPlayer instanceof ServerPlayer player)) {
            return;
        }
        String instanceKey = "ritual:"
                + level.dimension().location()
                + ":" + altar.getBlockPos().asLong()
                + ":" + player.getUUID()
                + ":" + level.getGameTime();
        IllagerTreacheryApi.submitDeduplicated(
                level.getServer(),
                TriggerSource.RITUAL,
                List.of(player),
                instanceKey
        );
    }
}
