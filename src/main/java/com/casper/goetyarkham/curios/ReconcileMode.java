package com.casper.goetyarkham.curios;

/**
 * Distinguishes why a shared-slot reconcile is running, so destructive
 * shrink/evacuate logic only ever fires for a genuinely confirmed change -
 * never for a login/respawn/clone/dimension-change restore where Curios'
 * equipped-item state may not have finished settling yet.
 *
 * <ul>
 *   <li>{@link #RESTORE} - player entity/Capability/Curios handler was just
 *       (re)created (login, respawn, clone, dimension change). Growing the
 *       slot (or re-applying its modifier at the same size) is safe, but
 *       shrinking or evacuating slot contents is never allowed: an equipped
 *       provider that is transiently unreadable at this point must not be
 *       misread as "unequipped".</li>
 *   <li>{@link #CONFIRMED_SHRINK} - triggered directly by a real Curios
 *       equip/unequip transition ({@code CurioChangeEvent}) or an explicit,
 *       already-settled state change. Shrinking and evacuating overflow slot
 *       contents back to the player is allowed.</li>
 * </ul>
 */
public enum ReconcileMode {
    RESTORE,
    CONFIRMED_SHRINK;

    boolean allowsShrink() {
        return this == CONFIRMED_SHRINK;
    }
}
