package com.casper.goetyarkham.mixin;

import com.casper.goetyarkham.sanity.SanityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Single-purpose bridge for successful cake-slice consumption. */
@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {
    @Inject(method = "eat", at = @At("RETURN"))
    private static void goetyarkham$afterCakeEaten(
            LevelAccessor level,
            BlockPos pos,
            BlockState previousState,
            Player player,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !callback.getReturnValue().consumesAction()
                || !previousState.hasProperty(CakeBlock.BITES)) {
            return;
        }

        int previousBites = previousState.getValue(CakeBlock.BITES);
        BlockState currentState = level.getBlockState(pos);
        boolean biteAdvanced = currentState.hasProperty(CakeBlock.BITES)
                && currentState.getBlock() instanceof CakeBlock
                && currentState.getValue(CakeBlock.BITES) == previousBites + 1;
        boolean finalSliceConsumed = previousBites == CakeBlock.MAX_BITES
                && !(currentState.getBlock() instanceof CakeBlock);
        if (biteAdvanced || finalSliceConsumed) {
            SanityEvents.ForgeBus.restoreFromFoodIfEligible(serverPlayer);
        }
    }
}
