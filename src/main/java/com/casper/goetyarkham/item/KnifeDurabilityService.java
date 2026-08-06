package com.casper.goetyarkham.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Thin wrapper around {@link ItemStack#hurt(int, RandomSource, ServerPlayer)}
 * used by {@link com.casper.goetyarkham.entity.ThrownKnifeEntity} for its
 * one-point entity-hit durability loss. Exists as its own method (rather
 * than an inline call on the entity) purely so a GameTest can verify the
 * Unbreaking-aware roll deterministically by injecting a fixed {@link
 * RandomSource}, instead of asserting on genuinely random outcomes.
 */
public final class KnifeDurabilityService {
    private KnifeDurabilityService() {
    }

    /** Returns {@code true} if this hit broke the stack (reached max damage). */
    public static boolean damageOnEntityHit(
            ItemStack stack, RandomSource random, @Nullable ServerPlayer owner) {
        return stack.hurt(1, random, owner);
    }
}
