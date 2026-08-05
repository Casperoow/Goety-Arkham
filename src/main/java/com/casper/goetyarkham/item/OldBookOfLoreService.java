package com.casper.goetyarkham.item;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.FocusSlotContributionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Server-authoritative funnel for the Old Book of Lore's cooldown redirect,
 * reached only through {@code OldBookOfLoreCooldownMixin}'s injection into
 * Goety's own {@code SEHelper.addCooldown} - the single point where every
 * focus-cast cooldown (already fully modified by wand cooldown attributes,
 * buffs/debuffs, a spell's own custom cooldown, and any compatibility mod's
 * adjustment) is about to be applied to the focus item.
 *
 * <p>Nothing here is ever reached on a call Goety itself only makes after a
 * cast has already succeeded, so a failed, cancelled, or insufficient cast
 * never redirects anything - Goety simply never calls {@code addCooldown}
 * for those.</p>
 */
public final class OldBookOfLoreService {
    private static final List<String> WORN_SLOTS =
            List.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK);

    private OldBookOfLoreService() {
    }

    /**
     * Returns {@code true} if {@code duration} was redirected onto the Old
     * Book of Lore's own vanilla item cooldown instead of the focus - in
     * which case the caller must cancel Goety's original cooldown
     * application so the focus itself never cools down. Keyed by item type
     * on the player's own {@link net.minecraft.world.item.ItemCooldowns},
     * so any number of worn copies (in either slot) share one cooldown, and
     * unequipping does not clear or pause it.
     */
    public static boolean tryRedirectCooldown(ServerPlayer player, Item item, int duration) {
        if (duration <= 0 || !(item instanceof IFocus)) {
            return false;
        }
        if (player.getCooldowns().isOnCooldown(ModItems.OLD_BOOK_OF_LORE.get())) {
            return false;
        }
        if (!FocusSlotContributionService.isWearing(
                player, ModItems.OLD_BOOK_OF_LORE.get(), WORN_SLOTS)) {
            return false;
        }
        player.getCooldowns().addCooldown(ModItems.OLD_BOOK_OF_LORE.get(), duration);
        return true;
    }
}
