package com.casper.goetyarkham.mixin;

import com.casper.goetyarkham.illager_treachery.RaidTriggerPlayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Raids.class)
public abstract class RaidsCreationContextMixin {
    @Inject(method = "createOrExtendRaid", at = @At("HEAD"))
    private void goetyarkham$captureRaidTriggerPlayer(
            ServerPlayer player,
            CallbackInfoReturnable<Raid> callbackInfo) {
        RaidTriggerPlayerContext.enter(player);
    }

    @Inject(method = "createOrExtendRaid", at = @At("RETURN"))
    private void goetyarkham$clearRaidTriggerPlayer(
            ServerPlayer player,
            CallbackInfoReturnable<Raid> callbackInfo) {
        RaidTriggerPlayerContext.exit();
    }
}
