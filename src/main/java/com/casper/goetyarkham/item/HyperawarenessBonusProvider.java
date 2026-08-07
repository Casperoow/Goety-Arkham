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
 * Hyperawareness' {@link ResourceSlotProvider} declaration: while worn in
 * {@link CurioSlotIds#ASSET}, it declares a minimum of 3 {@link
 * CurioSlotIds#RESOURCE} slots and independently scores every Book or
 * Rabbit's Foot currently sitting in any {@code resource} slot at {@code
 * +1} to that item's corresponding skill (Intellect / Agility
 * respectively). Mirrors {@link PhysicalTrainingBonusProvider}'s shape
 * exactly - same shared {@code resource} pool, same "declare a minimum,
 * score independently" contract - just a different recognized-item set.
 */
public final class HyperawarenessBonusProvider implements ResourceSlotProvider {
    public static final HyperawarenessBonusProvider INSTANCE = new HyperawarenessBonusProvider();
    public static final String PROVIDER_ID = "goetyarkham:hyperawareness";

    private static final int SLOT_COUNT = 3;
    private static final int BONUS_AMOUNT = 1;
    private static final List<String> WORN_SLOTS = List.of(CurioSlotIds.ASSET);
    private static final Map<Item, StatType> BONUS_BY_ITEM = Map.of(
            Items.BOOK, StatType.INTELLECT,
            Items.RABBIT_FOOT, StatType.AGILITY);

    private HyperawarenessBonusProvider() {
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
                player, ModItems.HYPERAWARENESS.get(), WORN_SLOTS);
    }

    @Override
    public int statBonus(StatType stat, Item item) {
        StatType granted = BONUS_BY_ITEM.get(item);
        return granted == stat ? BONUS_AMOUNT : 0;
    }
}
