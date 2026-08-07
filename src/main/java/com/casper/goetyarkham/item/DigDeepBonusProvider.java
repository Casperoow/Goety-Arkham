package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.curios.ResourceSlotProvider;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Dig Deep's {@link ResourceSlotProvider} declaration: while worn in {@link
 * CurioSlotIds#ASSET}, it declares a minimum of 3 {@link
 * CurioSlotIds#RESOURCE} slots and independently scores every Ectoplasm or
 * Rabbit's Foot currently sitting in any {@code resource} slot at {@code +1}
 * to that item's corresponding skill (Willpower / Agility respectively).
 * Mirrors {@link PhysicalTrainingBonusProvider}'s shape exactly - same
 * shared {@code resource} pool, same "declare a minimum, score
 * independently" contract - just a different recognized-item set. Which of
 * Goety's items is really Ectoplasm is not guessed here: it reuses the same
 * {@code com.Polarice3.Goety.common.items.ModItems.ECTOPLASM} reference
 * {@link PhysicalTrainingBonusProvider} already established.
 */
public final class DigDeepBonusProvider implements ResourceSlotProvider {
    public static final DigDeepBonusProvider INSTANCE = new DigDeepBonusProvider();
    public static final String PROVIDER_ID = "goetyarkham:dig_deep";

    private static final int SLOT_COUNT = 3;
    private static final int BONUS_AMOUNT = 1;
    private static final List<String> WORN_SLOTS = List.of(CurioSlotIds.ASSET);
    private static final Map<Item, StatType> BONUS_BY_ITEM = Map.of(
            com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get(), StatType.WILLPOWER,
            Items.RABBIT_FOOT, StatType.AGILITY);

    private DigDeepBonusProvider() {
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public int declaredSlotCount() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEquipped(ServerPlayer player) {
        return DynamicCurioSlotContributionService.isWearing(
                player, ModItems.DIG_DEEP.get(), WORN_SLOTS);
    }

    @Override
    public int statBonus(StatType stat, Item item) {
        StatType granted = BONUS_BY_ITEM.get(item);
        return granted == stat ? BONUS_AMOUNT : 0;
    }
}
