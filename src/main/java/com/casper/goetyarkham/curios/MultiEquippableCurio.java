package com.casper.goetyarkham.curios;

/**
 * Marks a Goety: Arkham Curio item that a player may have equipped more than
 * once at a time (e.g. two copies in different {@link CurioSlotIds#ASSET}
 * slot indices), opting it out of {@link CurioEquipRules}'s default rule
 * that a mod-owned Curio may never be equipped in more than one slot at
 * once. Each equipped copy still contributes its own independent effect -
 * see {@link DynamicCurioSlotContributionService}.
 */
public interface MultiEquippableCurio {
}
