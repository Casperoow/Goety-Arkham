package com.casper.goetyarkham.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marks a player as currently standing inside a Rita Chandler's Token aura.
 * Carries no attribute modifiers of its own; the Strength bonus is mixed in
 * by {@link RitaChandlersAuraEffectService#strengthModifier} and the flat
 * damage bonus is applied by the item's own damage-event handler, both keyed
 * off this effect's presence.
 */
public final class RitaChandlersBlessingEffect extends MobEffect {
    public RitaChandlersBlessingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC9A227);
    }
}
