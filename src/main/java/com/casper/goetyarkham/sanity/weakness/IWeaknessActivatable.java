package com.casper.goetyarkham.sanity.weakness;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface IWeaknessActivatable {
    void activateWeakness(
            ServerPlayer player,
            ItemStack stack,
            int slotIndex,
            WeaknessActivationCause cause);
}
