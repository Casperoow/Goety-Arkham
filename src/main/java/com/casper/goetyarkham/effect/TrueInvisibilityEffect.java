package com.casper.goetyarkham.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server-authoritative marker applied alongside vanilla {@link
 * net.minecraft.world.effect.MobEffects#INVISIBILITY} while On the Lam's
 * True Invisibility is active (see {@link
 * com.casper.goetyarkham.item.OnTheLamService}). Synced to every client
 * tracking the wearer exactly like any other potion effect, so it can be
 * queried both by server-side AI-blocking logic and by the client-side
 * armor/held-item render mixins that hide the gear vanilla invisibility
 * intentionally leaves visible.
 */
public final class TrueInvisibilityEffect extends MobEffect {
    public TrueInvisibilityEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x2B2B2B);
    }

    /**
     * Forge builds every instance's default cure list from this method.
     * Returning an empty list prevents milk and every other ordinary curative
     * item from silently desyncing this marker from the cooldown-gated state
     * machine that owns it.
     */
    @Override
    public List<ItemStack> getCurativeItems() {
        return List.of();
    }
}
