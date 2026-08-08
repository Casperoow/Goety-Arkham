package com.casper.goetyarkham.soul;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy;
import com.Polarice3.Goety.common.capabilities.soulenergy.SEProvider;
import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.common.events.spell.GoetyEventFactory;
import com.casper.goetyarkham.attribute.ModAttributes;
import com.casper.goetyarkham.network.ModNetwork;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.casper.goetyarkham.willpower.WillpowerEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative facade for player soul-energy reads and mutations.
 *
 * <p>The service never calls the intercepted SEHelper entry points. Arca data
 * is accessed through Goety's capability and Totem data through ITotem NBT
 * methods, which prevents Mixin recursion.</p>
 */
public final class SoulEnergyPoolService {
    private SoulEnergyPoolService() {
    }

    public static boolean hasContainer(Player player) {
        return getSnapshot(player).hasContainer();
    }

    public static int getCurrentSoul(Player player) {
        return getSnapshot(player).currentSoul();
    }

    public static int getMaximumSoul(Player player) {
        return getSnapshot(player).maximumSoul();
    }

    public static boolean hasSoul(Player player, int amount) {
        SoulPoolSnapshot snapshot = getSnapshot(player);
        return snapshot.hasContainer() && snapshot.currentSoul() >= amount;
    }

    public static void setSoul(Player player, int amount) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SoulPoolSnapshot snapshot = update(serverPlayer);
        int target = Math.max(0, Math.min(snapshot.maximumSoul(), amount));
        if (target > snapshot.currentSoul()) {
            addSoul(serverPlayer, target - snapshot.currentSoul());
        } else if (target < snapshot.currentSoul()) {
            removeSoul(serverPlayer, snapshot.currentSoul() - target);
        }
    }

    public static int addSoul(Player player, int amount) {
        if (!(player instanceof ServerPlayer serverPlayer) || amount <= 0) {
            return 0;
        }
        Optional<PlayerSoulPoolData> optional = data(serverPlayer);
        if (optional.isEmpty()) {
            return 0;
        }

        PlayerSoulPoolData poolData = optional.get();
        PoolContext context = collect(serverPlayer);
        PoolValues values = values(serverPlayer, context);
        if (!context.hasContainer()) {
            poolData.setVirtualSoulReserve(0);
            update(serverPlayer);
            return 0;
        }

        SoulPoolOperations.MutationResult result = SoulPoolOperations.add(
                context.handles(),
                poolData.getVirtualSoulReserve(),
                values.virtualCapacity(),
                values.maximum(),
                amount
        );
        poolData.setVirtualSoulReserve(result.virtualReserve());
        update(serverPlayer);
        return result.changedAmount();
    }

    public static int removeSoul(Player player, int amount) {
        if (!(player instanceof ServerPlayer serverPlayer) || amount <= 0) {
            return 0;
        }
        Optional<PlayerSoulPoolData> optional = data(serverPlayer);
        if (optional.isEmpty()) {
            return 0;
        }

        PlayerSoulPoolData poolData = optional.get();
        PoolContext context = collect(serverPlayer);
        PoolValues values = values(serverPlayer, context);
        SoulPoolOperations.MutationResult result = SoulPoolOperations.remove(
                context.handles(),
                poolData.getVirtualSoulReserve(),
                values.maximum(),
                amount
        );
        poolData.setVirtualSoulReserve(result.virtualReserve());
        update(serverPlayer);
        return result.changedAmount();
    }

    /**
     * Pays an undiscounted fixed cost from the unified pool, all-or-nothing.
     * This deliberately bypasses Goety's soul discount and loss event path.
     */
    public static boolean tryRemoveSoul(Player player, int amount) {
        if (!(player instanceof ServerPlayer serverPlayer) || amount <= 0) {
            return false;
        }

        SoulPoolSnapshot snapshot = update(serverPlayer);
        if (!snapshot.hasContainer() || snapshot.currentSoul() < amount) {
            return false;
        }

        Optional<PlayerSoulPoolData> optional = data(serverPlayer);
        if (optional.isEmpty()) {
            return false;
        }

        PlayerSoulPoolData poolData = optional.get();
        PoolContext context = collect(serverPlayer);
        PoolValues values = values(serverPlayer, context);
        SoulPoolOperations.MutationResult result =
                SoulPoolOperations.removeExact(
                        context.handles(),
                        poolData.getVirtualSoulReserve(),
                        values.maximum(),
                        amount);
        if (result.changedAmount() != amount) {
            return false;
        }

        poolData.setVirtualSoulReserve(result.virtualReserve());
        update(serverPlayer);
        return true;
    }

    public static SoulPoolSnapshot getSnapshot(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return update(serverPlayer);
        }
        return SoulPoolSnapshot.EMPTY;
    }

    public static void refresh(ServerPlayer player) {
        update(player);
    }

    public static int addSoulFromGoety(ServerPlayer player, int requestedAmount) {
        int eventAmount = GoetyEventFactory.onSoulEnergyGain(
                player, Math.max(0, requestedAmount));
        return addSoul(player, Math.max(0, eventAmount));
    }

    public static int removeSoulFromGoety(ServerPlayer player, int requestedAmount) {
        int discounted = (int) (Math.max(0, requestedAmount)
                * com.Polarice3.Goety.utils.SEHelper.soulDiscount(player));
        int eventAmount = GoetyEventFactory.onSoulEnergyLoss(player, discounted);
        return removeSoul(player, Math.max(0, eventAmount));
    }

    private static SoulPoolSnapshot update(ServerPlayer player) {
        Optional<PlayerSoulPoolData> optional = data(player);
        if (optional.isEmpty()) {
            return SoulPoolSnapshot.EMPTY;
        }

        PlayerSoulPoolData poolData = optional.get();
        PoolContext context = collect(player);
        PoolValues values = values(player, context);
        int reserve = poolData.getVirtualSoulReserve();
        if (!context.hasContainer()) {
            reserve = 0;
        } else {
            SoulPoolOperations.MutationResult reconciled =
                    SoulPoolOperations.reconcileVirtualReserve(
                            context.handles(), reserve, values.virtualCapacity());
            reserve = reconciled.virtualReserve();
        }
        SoulPoolOperations.MutationResult truncated =
                SoulPoolOperations.truncateToMaximum(
                        context.handles(), reserve, values.maximum());
        reserve = truncated.virtualReserve();
        poolData.setVirtualSoulReserve(reserve);

        int physicalCurrent = SoulPoolOperations.physicalCurrent(context.handles());
        SoulPoolSnapshot snapshot = new SoulPoolSnapshot(
                SoulPoolOperations.current(physicalCurrent, reserve, values.maximum()),
                values.maximum(),
                context.hasContainer(),
                context.arcaMode()
        );
        long signature = signature(
                context,
                reserve,
                values.willpowerContribution(),
                values.directCapacityModifier(),
                values.percentModifier());
        boolean changed = signature != poolData.getLastContainerSignature()
                || !snapshot.equals(poolData.getLastSnapshot());

        if (changed) {
            poolData.updateCache(signature, snapshot);
            if (player.getAttribute(ModAttributes.SOUL_ENERGY_MAX.get()) != null) {
                player.getAttribute(ModAttributes.SOUL_ENERGY_MAX.get())
                        .setBaseValue(snapshot.maximumSoul());
            }
        }

        /*
         * Capability restoration can calculate a snapshot before the player's
         * play connection exists. Keep calculation caching separate from client
         * synchronization so the login/respawn/dimension refresh can send the
         * still-pending snapshot once the connection is ready.
         */
        if (poolData.needsClientSync(snapshot)
                && ModNetwork.sendSoulPool(player, snapshot)) {
            if (context.arcaMode()) {
                com.Polarice3.Goety.utils.SEHelper.sendSEUpdatePacket(player);
            }
            poolData.markClientSynced(snapshot);
        }
        return snapshot;
    }

    private static PoolValues values(ServerPlayer player, PoolContext context) {
        int physicalMaximum = SoulPoolOperations.physicalMaximum(context.handles());
        int willpower =
                PlayerStatsService.getFinalValue(player, StatType.WILLPOWER);
        int contribution = WillpowerEffects.calculateSoulCapacityContribution(willpower);
        int directCapacityModifier =
                com.casper.goetyarkham.effect.DreamsOfRlyehEffectService
                        .soulCapacityModifier(player);
        int percentModifier =
                com.casper.goetyarkham.item.HospitalDebtsItem
                        .soulCapacityPercentModifier(player);
        int maximum = SoulPoolOperations.maximum(
                context.hasContainer(),
                physicalMaximum,
                contribution,
                directCapacityModifier,
                percentModifier);
        int virtualCapacity = SoulPoolOperations.virtualCapacity(physicalMaximum, maximum);
        return new PoolValues(
                maximum,
                virtualCapacity,
                contribution,
                directCapacityModifier,
                percentModifier);
    }

    private static PoolContext collect(ServerPlayer player) {
        Optional<ISoulEnergy> arca = player.getCapability(SEProvider.CAPABILITY).resolve();
        List<SoulStorageHandle> handles = new ArrayList<>();
        IdentityHashMap<ItemStack, Boolean> seen = new IdentityHashMap<>();

        // Stable fill order: Curios, offhand, hotbar, then Arca. Removal uses
        // the reverse physical order in SoulPoolOperations.
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(inventory ->
                inventory.getCurios().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .forEach(entry -> addCurioStacks(
                                handles, seen, entry.getKey(), entry.getValue())));

        addStack(handles, seen, "offhand", player.getOffhandItem());
        for (int slot = 0; slot < 9; slot++) {
            addStack(handles, seen, "hotbar:" + slot, player.getInventory().getItem(slot));
        }

        boolean arcaMode = arca.isPresent()
                && SoulTransferArcaValidator
                .findValidArca(player, arca.get())
                .isPresent();
        if (arcaMode) {
            int maximum = Math.max(0, MainConfig.MaxArcaSouls.get());
            handles.add(new ArcaHandle(arca.get(), maximum));
        }
        return new PoolContext(
                List.copyOf(handles),
                !handles.isEmpty(),
                arcaMode
        );
    }

    private static void addCurioStacks(
            List<SoulStorageHandle> handles,
            IdentityHashMap<ItemStack, Boolean> seen,
            String slotType,
            ICurioStacksHandler stacksHandler) {
        for (int slot = 0; slot < stacksHandler.getStacks().getSlots(); slot++) {
            addStack(
                    handles,
                    seen,
                    "curios:" + slotType + ":" + slot,
                    stacksHandler.getStacks().getStackInSlot(slot)
            );
        }
    }

    private static void addStack(
            List<SoulStorageHandle> handles,
            IdentityHashMap<ItemStack, Boolean> seen,
            String slotId,
            ItemStack stack) {
        if (stack.isEmpty()
                || !(stack.getItem() instanceof ITotem totem)
                || seen.put(stack, Boolean.TRUE) != null) {
            return;
        }
        totem.setTagTick(stack);
        handles.add(new TotemHandle(slotId, stack));
    }

    private static long signature(
            PoolContext context,
            int virtualReserve,
            int willpowerContribution,
            int directCapacityModifier,
            int percentModifier) {
        long hash = 1125899906842597L;
        hash = 31L * hash + Boolean.hashCode(context.arcaMode());
        hash = 31L * hash + virtualReserve;
        hash = 31L * hash + willpowerContribution;
        hash = 31L * hash + directCapacityModifier;
        hash = 31L * hash + percentModifier;
        for (SoulStorageHandle handle : context.handles()) {
            hash = 31L * hash + handle.storageIdentity().hashCode();
            hash = 31L * hash + handle.getCurrentSoul();
            hash = 31L * hash + handle.getMaximumSoul();
        }
        return hash;
    }

    private static Optional<PlayerSoulPoolData> data(ServerPlayer player) {
        return player.getCapability(SoulPoolCapabilities.PLAYER_SOUL_POOL).resolve();
    }

    private record PoolContext(
            List<SoulStorageHandle> handles,
            boolean hasContainer,
            boolean arcaMode) {
    }

    private record PoolValues(
            int maximum,
            int virtualCapacity,
            int willpowerContribution,
            int directCapacityModifier,
            int percentModifier) {
    }

    private record TotemHandle(String slotId, ItemStack stack)
            implements SoulStorageHandle {
        @Override
        public String storageIdentity() {
            return slotId + ":" + ForgeRegistries.ITEMS.getKey(stack.getItem());
        }

        @Override
        public int getCurrentSoul() {
            return ITotem.currentSouls(stack);
        }

        @Override
        public int getMaximumSoul() {
            return Math.max(0, ITotem.maximumSouls(stack));
        }

        @Override
        public void setCurrentSoul(int amount) {
            ITotem.setSoulsamount(stack,
                    Math.max(0, Math.min(getMaximumSoul(), amount)));
        }
    }

    private record ArcaHandle(ISoulEnergy soulEnergy, int maximum)
            implements SoulStorageHandle {
        @Override
        public String slotId() {
            return "arca";
        }

        @Override
        public int getCurrentSoul() {
            return soulEnergy.getSoulEnergy();
        }

        @Override
        public int getMaximumSoul() {
            return maximum;
        }

        @Override
        public void setCurrentSoul(int amount) {
            soulEnergy.setSoulEnergy(Math.max(0, Math.min(maximum, amount)));
        }
    }
}
