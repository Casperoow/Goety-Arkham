package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * A Curio that participates in the shared {@link CurioSlotIds#SKILL_BONUS}
 * slot pool. Any number of items can each implement this independently
 * (Encyclopedia today; future items like a "Preternatural Perception" or
 * "Occult Studies" charm are expected to follow the same shape):
 *
 * <ul>
 *   <li>{@link #providerId()} is a stable key used to deduplicate multiple
 *       copies of the same provider item (two Encyclopedias worn at once
 *       still count as one contributor) - see {@link
 *       SharedBonusSlotProviderRegistry}.</li>
 *   <li>{@link #declaredSlotCount()} is how many {@code skill_bonus} slots
 *       this provider grants while equipped. The slot's actual capacity is
 *       the <em>maximum</em> declared by any one currently equipped
 *       provider, never a sum - see {@link SharedBonusSlotService}.</li>
 *   <li>{@link #isEquipped(ServerPlayer)} reports whether this provider is
 *       currently active for the given player.</li>
 *   <li>{@link #statBonus(StatType, Item)} is this provider's own,
 *       independent scoring rule for a single item sitting in one of the
 *       shared slots. Every equipped provider scans the same shared slot
 *       contents and scores them under its own rule; an item a provider
 *       does not recognize contributes 0 to that provider, and different
 *       providers' bonuses for the same item stack additively.</li>
 * </ul>
 */
public interface SharedBonusSlotProvider {
    String providerId();

    int declaredSlotCount();

    boolean isEquipped(ServerPlayer player);

    /** Zero if this provider assigns no bonus to {@code item} for {@code stat}. */
    int statBonus(StatType stat, Item item);
}
