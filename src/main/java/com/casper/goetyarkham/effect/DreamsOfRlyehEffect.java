package com.casper.goetyarkham.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DreamsOfRlyehEffect extends MobEffect {
    public DreamsOfRlyehEffect() {
        super(MobEffectCategory.HARMFUL, 0x36566F);
    }

    /**
     * Forge builds every instance's default cure list from this method.
     * Returning an empty list prevents milk and every other ordinary curative
     * item while still allowing explicit server-side removeEffect calls.
     */
    @Override
    public List<ItemStack> getCurativeItems() {
        return List.of();
    }
}
