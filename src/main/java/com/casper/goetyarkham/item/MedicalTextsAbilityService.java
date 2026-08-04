package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.chaosbag.ChaosBagApi;
import com.casper.goetyarkham.chaosbag.ChaosBaseValueSource;
import com.casper.goetyarkham.chaosbag.ChaosCheckModifier;
import com.casper.goetyarkham.chaosbag.ChaosCheckRequest;
import com.casper.goetyarkham.chaosbag.ChaosCheckResult;
import com.casper.goetyarkham.chaosbag.ChaosCheckService;
import com.casper.goetyarkham.chaosbag.ChaosToken;
import com.casper.goetyarkham.curios.CurioSlotIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;

/**
 * Server-authoritative Medical Texts ability: reached only through {@link
 * #tryUse}, the single entry point the network packet handler calls. Every
 * check below is re-derived from the sender's own authoritative state -
 * nothing about equip status, cooldown, or the check outcome is ever trusted
 * from the client.
 */
public final class MedicalTextsAbilityService {
    public static final ResourceLocation CHECK_SOURCE =
            ResourceLocation.fromNamespaceAndPath(GoetyArkham.MOD_ID, "medical_texts");
    public static final int INTELLECT_TARGET = 2;
    public static final int COOLDOWN_TICKS = 100;
    public static final float FAILURE_DAMAGE = 1.0F;

    private MedicalTextsAbilityService() {
    }

    public static void tryUse(ServerPlayer player) {
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || !isEquipped(player)
                || player.getCooldowns().isOnCooldown(ModItems.MEDICAL_TEXTS.get())) {
            return;
        }

        // Added before the check runs, and only once every gate above has
        // passed, so an unequipped or already-cooling-down copy can never
        // refresh the cooldown, and multiple worn copies can never trigger
        // more than one check per key press.
        player.getCooldowns().addCooldown(ModItems.MEDICAL_TEXTS.get(), COOLDOWN_TICKS);

        ChaosCheckRequest request = ChaosCheckService.createRequest(
                player,
                CHECK_SOURCE,
                ChaosBaseValueSource.INTELLECT,
                0,
                INTELLECT_TARGET,
                List.<ChaosCheckModifier>of(),
                ChaosBagApi.snapshot(player.getServer()),
                List.<ChaosToken>of(),
                player.getRandom()::nextInt);
        ChaosCheckResult result = ChaosCheckService.resolveAndApply(player, request);
        applyOutcome(player, result.success());
    }

    /** Isolated from the random chaos draw so its two branches are directly testable. */
    static void applyOutcome(ServerPlayer player, boolean success) {
        if (success) {
            player.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 0));
        } else {
            player.hurt(player.damageSources().magic(), FAILURE_DAMAGE);
        }
    }

    private static boolean isEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> hasMedicalTexts(inventory, CurioSlotIds.HANDS)
                        || hasMedicalTexts(inventory, CurioSlotIds.BOOK))
                .orElse(false);
    }

    private static boolean hasMedicalTexts(ICuriosItemHandler inventory, String slotId) {
        return inventory.getStacksHandler(slotId)
                .map(MedicalTextsAbilityService::hasMedicalTexts)
                .orElse(false);
    }

    private static boolean hasMedicalTexts(ICurioStacksHandler handler) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (stacks.getStackInSlot(slot).is(ModItems.MEDICAL_TEXTS.get())) {
                return true;
            }
        }
        return false;
    }
}
