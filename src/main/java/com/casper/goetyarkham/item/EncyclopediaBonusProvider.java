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
 * The Encyclopedia's {@link ResourceSlotProvider} declaration: while worn
 * in either {@link CurioSlotIds#HANDS} or {@link CurioSlotIds#BOOK}, it
 * declares a minimum of 1 {@link CurioSlotIds#RESOURCE} slot and
 * independently scores every resource item currently sitting in any {@code
 * resource} slot at {@code +2} to that item's corresponding skill. None of
 * the four permitted items (three vanilla, one from Goety) implement {@code
 * EquipmentStatModifier} themselves, so {@code EquipmentStatsService}
 * consults every registered {@link ResourceSlotProvider} - this one
 * included - directly instead.
 */
public final class EncyclopediaBonusProvider implements ResourceSlotProvider {
    public static final EncyclopediaBonusProvider INSTANCE = new EncyclopediaBonusProvider();
    public static final String PROVIDER_ID = "goetyarkham:encyclopedia";

    private static final int SLOT_COUNT = 1;
    private static final int BONUS_AMOUNT = 2;
    private static final List<String> WORN_SLOTS =
            List.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK);
    private static final Map<Item, StatType> BONUS_BY_ITEM = Map.of(
            Items.IRON_INGOT, StatType.STRENGTH,
            Items.RABBIT_FOOT, StatType.AGILITY,
            Items.BOOK, StatType.INTELLECT,
            com.Polarice3.Goety.common.items.ModItems.ECTOPLASM.get(), StatType.WILLPOWER);

    private EncyclopediaBonusProvider() {
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
                player, ModItems.ENCYCLOPEDIA.get(), WORN_SLOTS);
    }

    @Override
    public int statBonus(StatType stat, Item item) {
        StatType granted = BONUS_BY_ITEM.get(item);
        return granted == stat ? BONUS_AMOUNT : 0;
    }
}
