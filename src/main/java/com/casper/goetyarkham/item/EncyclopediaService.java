package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.EncyclopediaSkillSlotContributionService;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Owns the single +1 {@link CurioSlotIds#ENCYCLOPEDIA_SKILL} Curios slot
 * granted by an equipped Encyclopedia, worn in either {@link
 * CurioSlotIds#HANDS} or {@link CurioSlotIds#BOOK}. Reuses the same {@link
 * EncyclopediaSkillSlotContributionService} slot-modifier lifecycle as
 * every other dynamic-slot Curio, so it stacks additively with any other
 * future contributor to that slot without interfering with it.
 */
public final class EncyclopediaService {
    public static final UUID SKILL_SLOT_MODIFIER_ID = UUID.fromString(
            "63f8b0ff-0a2c-4282-8364-d7348782c883");
    public static final String SKILL_SLOT_MODIFIER_NAME =
            "goetyarkham:encyclopedia";
    private static final List<String> WORN_SLOTS =
            List.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK);

    private EncyclopediaService() {
    }

    /**
     * Login/clone/dimension/sync-safe repair. Stateless: it only looks at
     * whether the Encyclopedia is currently worn (in either supported
     * slot), so calling it repeatedly (or out of order with any other
     * reconcile call) never duplicates or loses the slot modifier.
     */
    public static void reconcile(ServerPlayer player) {
        EncyclopediaSkillSlotContributionService.reconcile(
                player,
                SKILL_SLOT_MODIFIER_ID,
                SKILL_SLOT_MODIFIER_NAME,
                ModItems.ENCYCLOPEDIA::get,
                WORN_SLOTS);
    }

    public static boolean isWearing(ServerPlayer player) {
        return EncyclopediaSkillSlotContributionService.isWearing(
                player, ModItems.ENCYCLOPEDIA.get(), WORN_SLOTS);
    }

    public static int equippedCount(ServerPlayer player) {
        return EncyclopediaSkillSlotContributionService.equippedCount(
                player, ModItems.ENCYCLOPEDIA.get(), WORN_SLOTS);
    }
}
