package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.FocusSlotContributionService;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Owns the single +1 {@link CurioSlotIds#FOCUS} Curios slot granted by an
 * equipped Book of Shadows, worn in either {@link CurioSlotIds#HANDS} or
 * {@link CurioSlotIds#BOOK}. Reuses the same {@link
 * FocusSlotContributionService} slot-modifier lifecycle as the Arcane
 * Initiate's Token, so both items' contributions stack additively without
 * either one interfering with the other.
 */
public final class BookOfShadowsService {
    public static final UUID FOCUS_SLOT_MODIFIER_ID = UUID.fromString(
            "67bdbbcc-8b10-4336-b20a-452de1d05c8a");
    public static final String FOCUS_SLOT_MODIFIER_NAME =
            "goetyarkham:book_of_shadows";
    private static final List<String> WORN_SLOTS =
            List.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK);

    private BookOfShadowsService() {
    }

    /**
     * Login/clone/dimension/sync-safe repair. Stateless: it only looks at
     * whether the book is currently worn (in either supported slot), so
     * calling it repeatedly (or out of order with any other reconcile call)
     * never duplicates or loses the slot modifier.
     */
    public static void reconcile(ServerPlayer player) {
        FocusSlotContributionService.reconcile(
                player,
                FOCUS_SLOT_MODIFIER_ID,
                FOCUS_SLOT_MODIFIER_NAME,
                ModItems.BOOK_OF_SHADOWS::get,
                WORN_SLOTS);
    }

    public static boolean isWearing(ServerPlayer player) {
        return FocusSlotContributionService.isWearing(
                player, ModItems.BOOK_OF_SHADOWS.get(), WORN_SLOTS);
    }

    public static int equippedCount(ServerPlayer player) {
        return FocusSlotContributionService.equippedCount(
                player, ModItems.BOOK_OF_SHADOWS.get(), WORN_SLOTS);
    }
}
