package com.casper.goetyarkham.item;

import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.curios.DynamicCurioSlotContributionService;
import com.casper.goetyarkham.curios.ReconcileMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Maintains the independent asset-slot contributions of asset Curios. */
public final class AssetSlotBonusService {
    private static final UUID EMERGENCY_CACHE_MODIFIER_ID = UUID.fromString(
            "1d1d4aca-9518-4b90-b63e-b07e63d93ccb");
    private static final UUID HOT_STREAK_MODIFIER_ID = UUID.fromString(
            "d4588603-16f4-43ae-b326-397b48cb28b1");
    private static final List<String> WORN_SLOTS = List.of(CurioSlotIds.ASSET);

    private AssetSlotBonusService() {
    }

    /** Reconciles both sources after a confirmed Curios inventory change. */
    public static void reconcile(ServerPlayer player) {
        reconcile(player, ReconcileMode.CONFIRMED_SHRINK, "confirmed");
    }

    /** Restores both sources without shrinking while Curios is settling. */
    public static void reconcileRestore(ServerPlayer player) {
        reconcile(player, ReconcileMode.RESTORE, "restore");
    }

    private static void reconcile(
            ServerPlayer player, ReconcileMode mode, String source) {
        reconcileContribution(
                player,
                EMERGENCY_CACHE_MODIFIER_ID,
                "goetyarkham:emergency_cache",
                ModItems.EMERGENCY_CACHE,
                EmergencyCacheItem.ASSET_SLOT_BONUS,
                mode,
                source);
        reconcileContribution(
                player,
                HOT_STREAK_MODIFIER_ID,
                "goetyarkham:hot_streak",
                ModItems.HOT_STREAK,
                HotStreakItem.ASSET_SLOT_BONUS,
                mode,
                source);
    }

    private static void reconcileContribution(
            ServerPlayer player,
            UUID modifierId,
            String modifierName,
            Supplier<? extends Item> item,
            int amount,
            ReconcileMode mode,
            String source) {
        int targetSize = DynamicCurioSlotContributionService.isWearing(
                player, item.get(), WORN_SLOTS) ? amount : 0;
        DynamicCurioSlotContributionService.reconcileSize(
                player,
                CurioSlotIds.ASSET,
                modifierId,
                modifierName,
                targetSize,
                mode,
                source);
    }
}
