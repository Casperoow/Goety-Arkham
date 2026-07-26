package com.casper.goetyarkham.agility;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.attribute.ModAttributes;
import com.casper.goetyarkham.stats.IPlayerStats;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.init.ModDamageTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Formulas, attribute mirrors, and server-side combat effects derived from the
 * authoritative agility stat.
 */
@Mod.EventBusSubscriber(modid = GoetyArkham.MOD_ID)
public final class AgilityEffects {
    public static final UUID MOVEMENT_SPEED_MODIFIER_ID =
            UUID.fromString("5d3a36e7-cd51-46e4-a73d-1cd9f6445bd2");
    public static final UUID DODGE_CHANCE_MODIFIER_ID =
            UUID.fromString("61db475f-c495-4241-b7f8-3868f88eb217");
    public static final UUID BULLET_DAMAGE_MODIFIER_ID =
            UUID.fromString("bcd100f5-7f6d-4901-b0bb-2a01e8ec6b64");

    private static final String MOVEMENT_SPEED_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".agility_movement_speed";
    private static final String DODGE_CHANCE_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".agility_dodge_chance";
    private static final String BULLET_DAMAGE_MODIFIER_NAME =
            GoetyArkham.MOD_ID + ".agility_bullet_damage";

    private AgilityEffects() {
    }

    public static double calculateDodgeChance(int agility) {
        double chance;
        if (agility >= 0) {
            chance = (1.0D + agility) / (6.0D + 2.0D * agility);
        } else {
            chance = 1.0D / (6.0D - 3.0D * agility);
        }
        return Mth.clamp(chance, 0.0D, 1.0D);
    }

    public static double calculateMovementSpeedModifier(int agility) {
        return Math.max(-1.0D, agility * 0.01D);
    }

    public static double calculateBulletDamageMultiplier(int agility) {
        return Math.max(0.0D, 1.0D + agility * 0.005D);
    }

    /**
     * Replaces fixed-UUID transient modifiers from the current capability
     * value. Forge attributes carry effects and client display only.
     */
    public static void refreshAgilityEffects(ServerPlayer player) {
        Optional<IPlayerStats> capability = PlayerStatsService.get(player);
        if (capability.isEmpty()) {
            GoetyArkham.LOGGER.debug(
                    "[AgilityEffects] Skipped refresh because PlayerStats is unavailable: player={}, uuid={}",
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
            return;
        }

        int agility = capability.get().get(StatType.AGILITY).finalValue();
        updateModifier(
                player,
                player.getAttribute(Attributes.MOVEMENT_SPEED),
                Attributes.MOVEMENT_SPEED.getDescriptionId(),
                MOVEMENT_SPEED_MODIFIER_ID,
                MOVEMENT_SPEED_MODIFIER_NAME,
                calculateMovementSpeedModifier(agility),
                AttributeModifier.Operation.MULTIPLY_TOTAL,
                true
        );
        updateModifier(
                player,
                player.getAttribute(ModAttributes.DODGE_CHANCE.get()),
                ModAttributes.DODGE_CHANCE.get().getDescriptionId(),
                DODGE_CHANCE_MODIFIER_ID,
                DODGE_CHANCE_MODIFIER_NAME,
                calculateDodgeChance(agility),
                AttributeModifier.Operation.ADDITION,
                false
        );
        updateModifier(
                player,
                player.getAttribute(ModAttributes.BULLET_DAMAGE.get()),
                ModAttributes.BULLET_DAMAGE.get().getDescriptionId(),
                BULLET_DAMAGE_MODIFIER_ID,
                BULLET_DAMAGE_MODIFIER_NAME,
                calculateBulletDamageMultiplier(agility) - 1.0D,
                AttributeModifier.Operation.ADDITION,
                false
        );
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getAmount() <= 0.0F
                || event.getSource().is(ModDamageTypes.BULLETS_TAG)) {
            return;
        }

        PlayerStatsService.get(player).ifPresent(stats -> {
            int agility = stats.get(StatType.AGILITY).finalValue();
            if (player.getRandom().nextDouble() < calculateDodgeChance(agility)) {
                event.setCanceled(true);
            }
        });
    }

    @SubscribeEvent
    public static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }

        if (event.getHurtEntity() instanceof ServerPlayer hurtPlayer) {
            Optional<IPlayerStats> hurtStats = PlayerStatsService.get(hurtPlayer);
            if (hurtStats.isPresent()) {
                int agility = hurtStats.get().get(StatType.AGILITY).finalValue();
                if (hurtPlayer.getRandom().nextDouble() < calculateDodgeChance(agility)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (event.getAttacker() instanceof ServerPlayer attacker) {
            PlayerStatsService.get(attacker).ifPresent(stats -> {
                int agility = stats.get(StatType.AGILITY).finalValue();
                double multiplier = calculateBulletDamageMultiplier(agility);
                event.setBaseAmount((float) (event.getBaseAmount() * multiplier));
            });
        }
    }

    static void updateModifier(
            ServerPlayer player,
            AttributeInstance instance,
            String attributeName,
            UUID modifierId,
            String modifierName,
            double amount,
            AttributeModifier.Operation operation,
            boolean removeWhenZero) {
        if (instance == null) {
            GoetyArkham.LOGGER.warn(
                    "[AgilityEffects] Player is missing {}: player={}, uuid={}",
                    attributeName,
                    player.getGameProfile().getName(),
                    player.getUUID()
            );
            return;
        }

        replaceTransientModifier(
                instance,
                modifierId,
                modifierName,
                amount,
                operation,
                removeWhenZero
        );
    }

    static void replaceTransientModifier(
            AttributeInstance instance,
            UUID modifierId,
            String modifierName,
            double amount,
            AttributeModifier.Operation operation,
            boolean removeWhenZero) {
        instance.removeModifier(modifierId);
        if (!removeWhenZero || Double.compare(amount, 0.0D) != 0) {
            instance.addTransientModifier(new AttributeModifier(
                    modifierId,
                    modifierName,
                    amount,
                    operation
            ));
        }
    }
}
