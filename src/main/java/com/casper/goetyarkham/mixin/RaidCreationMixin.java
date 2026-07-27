package com.casper.goetyarkham.mixin;

import com.casper.goetyarkham.illager_treachery.IllagerTreacheryApi;
import com.casper.goetyarkham.illager_treachery.RaidTriggerPlayerContext;
import com.casper.goetyarkham.illager_treachery.TriggerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Raid.class)
public abstract class RaidCreationMixin {
    @Inject(
            method = "<init>(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
            at = @At("RETURN"))
    private void goetyarkham$onRaidCreated(
            int id,
            ServerLevel level,
            BlockPos center,
            CallbackInfo callbackInfo) {
        String instanceKey = "raid:"
                + level.dimension().location()
                + ":" + id;
        List<net.minecraft.server.level.ServerPlayer> relatedPlayers =
                RaidTriggerPlayerContext.current()
                        .filter(player -> player.getServer() == level.getServer())
                        .map(List::of)
                        .orElseGet(List::of);
        IllagerTreacheryApi.submitDeduplicated(
                level.getServer(),
                TriggerSource.RAID,
                relatedPlayers,
                instanceKey
        );
    }
}
