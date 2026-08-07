package com.casper.goetyarkham.client;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.item.HardKnocksBonusProvider;
import com.casper.goetyarkham.item.HardKnocksService;
import com.casper.goetyarkham.item.ResourceSlotBonusSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Kept in the client package so dedicated servers never load {@link
 * Minecraft}. Reads only - never touches the shared {@code resource} slot's
 * capacity, contents, or any player attribute. Mirrors {@link
 * ClientPhysicalTrainingBonus} exactly.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientHardKnocksBonus {
    private ClientHardKnocksBonus() {
    }

    public static ResourceSlotBonusSnapshot compute() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return ResourceSlotBonusSnapshot.UNAVAILABLE;
        }
        boolean equipped = HardKnocksService.isWearing(player);
        List<ItemStack> resourceContents = DynamicCurioSlotContributionService.slotContents(
                player, CurioSlotIds.RESOURCE);
        return new ResourceSlotBonusSnapshot(
                true, equipped, HardKnocksBonusProvider.INSTANCE.computeBonus(resourceContents));
    }
}
