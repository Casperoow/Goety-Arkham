package com.casper.goetyarkham.client;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.item.EncyclopediaBonusProvider;
import com.casper.goetyarkham.item.EncyclopediaService;
import com.casper.goetyarkham.item.EncyclopediaTooltipBonus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Kept in the client package so dedicated servers never load {@link
 * Minecraft}. Reads only - never touches the shared {@code skill_bonus}
 * slot's capacity, contents, or any player attribute.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientEncyclopediaBonus {
    private ClientEncyclopediaBonus() {
    }

    public static EncyclopediaTooltipBonus compute() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return EncyclopediaTooltipBonus.UNAVAILABLE;
        }
        boolean equipped = EncyclopediaService.isWearing(player);
        List<ItemStack> skillBonusContents = DynamicCurioSlotContributionService.slotContents(
                player, CurioSlotIds.SKILL_BONUS);
        return new EncyclopediaTooltipBonus(
                true, equipped, EncyclopediaBonusProvider.INSTANCE.computeBonus(skillBonusContents));
    }
}
