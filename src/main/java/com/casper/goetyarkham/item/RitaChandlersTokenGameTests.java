package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.effect.ModEffects;
import com.casper.goetyarkham.effect.RitaChandlersAuraEffectService;
import com.casper.goetyarkham.sanity.SanityService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.List;
import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RitaChandlersTokenGameTests {
    private RitaChandlersTokenGameTests() {
    }

    @GameTest(template = "empty")
    public static void ritaChandlersTokenGrantsBaseAttributesAndOwnAura(
            GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "rita_chandlers_token");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.RITA_CHANDLERS_TOKEN.get())),
                "Rita Chandler's Token registry ID mismatch");

        RitaChandlersTokenItem item = ModItems.RITA_CHANDLERS_TOKEN.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Rita Chandler's Token must not stack, like every other token");

        List<Component> tooltip = new java.util.ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 5,
                "Rita Chandler's Token tooltip line count mismatch (no Shift)");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Rita Chandler's Token when-worn heading is not yellow");
        helper.assertTrue("+6 Max Health".equals(tooltip.get(1).getString())
                        && "+3 Max Sanity".equals(tooltip.get(2).getString())
                        && "+1 Strength".equals(tooltip.get(3).getString()),
                "Rita Chandler's Token English tooltip effect text mismatch");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(
                        java.util.Set.of(CurioSlotIds.TOKEN)),
                "Rita Chandler's Token item tag must expose only the token slot");

        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "rita-token-wearer", 1.5D);
        Zombie target = zombie(level, wearer, 1.0D, helper);
        try {
            ICurioStacksHandler tokenHandler =
                    handler(wearer, CurioSlotIds.TOKEN, helper);

            double baseMaxHealth = wearer.getAttribute(Attributes.MAX_HEALTH).getValue();
            int baseMaxSanity = SanityService.getMaximumSanity(wearer);
            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);

            tokenHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            if (wearer.getHealth() < wearer.getMaxHealth()) {
                wearer.setHealth(wearer.getMaxHealth());
            }

            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaxHealth + RitaChandlersTokenItem.MAX_HEALTH_BONUS,
                    "Rita Chandler's Token did not add exactly +6 Max Health");
            helper.assertTrue(SanityService.getMaximumSanity(wearer)
                            == baseMaxSanity + RitaChandlersTokenItem.MAX_SANITY_BONUS,
                    "Rita Chandler's Token did not add exactly +3 Max Sanity");

            // The equipment component alone (the token's own worn bonus,
            // before the wearer's own aura is ever pulsed) must be exactly
            // +1, independent of any aura contribution.
            int equipmentStrength = PlayerStatsService.get(wearer)
                    .map(stats -> stats.get(StatType.STRENGTH).equipment())
                    .orElse(Integer.MIN_VALUE);
            helper.assertTrue(equipmentStrength == RitaChandlersTokenItem.STRENGTH_BONUS,
                    "Rita Chandler's Token equipment Strength component is not exactly +1");
            helper.assertTrue(!RitaChandlersAuraEffectService.isBlessed(wearer),
                    "Wearer was blessed before any curio tick ever pulsed the aura");

            // curioTick (driven by Curios' own LivingTickEvent listener, the
            // same mechanism settleCurioChange already exercises) pulses the
            // aura every tick while functionally equipped; the wearer is
            // always inside their own aura's radius.
            settleCurioChange(wearer);
            helper.assertTrue(RitaChandlersAuraEffectService.isBlessed(wearer),
                    "curioTick did not bless the wearer with their own aura");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + RitaChandlersTokenItem.STRENGTH_BONUS
                                    + RitaChandlersAuraEffectService.STRENGTH_BONUS,
                    "Wearer did not end up with the full +2 Strength"
                            + " (own equipment +1 and own aura +1)");

            // The flat +2 damage bonus fires on the blessed wearer's own hit
            // against another entity.
            assertDamageBonusApplies(wearer, target, helper);

            // A blessed player can never bonus their own self-inflicted
            // damage (also covers the shape of Aquinnah's Token's redirect,
            // which reuses the original attacker's DamageSource to hurt that
            // same attacker).
            LivingHurtEvent selfHit = new LivingHurtEvent(
                    wearer, wearer.damageSources().playerAttack(wearer), 3.0F);
            RitaChandlersTokenEffectEvents.onLivingHurt(selfHit);
            helper.assertTrue(Math.abs(selfHit.getAmount() - 3.0F) < 0.001F,
                    "A blessed player's self-inflicted damage incorrectly"
                            + " carried the aura's bonus damage");

            // Repeated pulses in the same window must not stack the effect:
            // amplifier stays 0 and duration is clamped to the same cap.
            MobEffectInstance beforeExtraPulse =
                    wearer.getEffect(ModEffects.RITA_CHANDLERS_BLESSING.get());
            RitaChandlersAuraEffectService.pulseAuraFrom(wearer);
            RitaChandlersAuraEffectService.pulseAuraFrom(wearer);
            MobEffectInstance afterExtraPulse =
                    wearer.getEffect(ModEffects.RITA_CHANDLERS_BLESSING.get());
            helper.assertTrue(afterExtraPulse.getAmplifier() == 0
                            && beforeExtraPulse.getAmplifier() == 0,
                    "Repeated aura pulses changed the blessing's amplifier");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength + RitaChandlersTokenItem.STRENGTH_BONUS
                                    + RitaChandlersAuraEffectService.STRENGTH_BONUS,
                    "Repeated aura pulses changed the wearer's final Strength");

            // Unequipping must immediately clear both the token's own worn
            // bonus and the wearer's own aura-sourced bonus and damage
            // bonus - not just eventually, once the blessing decays.
            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaxHealth,
                    "Unequipping left a residual Max Health bonus");
            helper.assertTrue(SanityService.getMaximumSanity(wearer) == baseMaxSanity,
                    "Unequipping left a residual Max Sanity bonus");
            helper.assertTrue(!RitaChandlersAuraEffectService.isBlessed(wearer),
                    "Unequipping left the wearer blessed by their own aura");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH)
                            == baseStrength,
                    "Unequipping left a residual Strength bonus");

            helper.succeed();
        } finally {
            target.discard();
            level.players().remove(wearer);
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void ritaChandlersTokenAuraAffectsOnlyPlayersActuallyInRange(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "rita-aura-wearer", 0.0D);
        TestPlayer near = testPlayer(level, "rita-aura-near", 6.0D);
        TestPlayer atEdge = testPlayer(level, "rita-aura-edge",
                RitaChandlersAuraEffectService.RADIUS);
        TestPlayer far = testPlayer(level, "rita-aura-far", 20.0D);
        try {
            // Captured before the wearer's token is ever equipped/ticked, so
            // curioTick's own aura pulse can never have touched these
            // players yet.
            int nearBaseStrength = PlayerStatsService.getFinalValue(near, StatType.STRENGTH);
            int edgeBaseStrength = PlayerStatsService.getFinalValue(atEdge, StatType.STRENGTH);
            int farBaseStrength = PlayerStatsService.getFinalValue(far, StatType.STRENGTH);

            equip(wearer, helper);
            settleCurioChange(wearer);

            helper.assertTrue(RitaChandlersAuraEffectService.isInRange(wearer, near),
                    "A player 6 blocks away must be inside the 10-block aura");
            helper.assertTrue(RitaChandlersAuraEffectService.isInRange(wearer, atEdge),
                    "A player exactly 10 blocks away must be inside the aura (inclusive boundary)");
            helper.assertTrue(!RitaChandlersAuraEffectService.isInRange(wearer, far),
                    "A player 20 blocks away must be outside the aura");

            RitaChandlersAuraEffectService.pulseAuraFrom(wearer);

            helper.assertTrue(RitaChandlersAuraEffectService.isBlessed(near),
                    "The in-range player was not blessed");
            helper.assertTrue(RitaChandlersAuraEffectService.isBlessed(atEdge),
                    "The exactly-at-the-boundary player was not blessed");
            helper.assertTrue(!RitaChandlersAuraEffectService.isBlessed(far),
                    "The out-of-range player was incorrectly blessed");

            helper.assertTrue(PlayerStatsService.getFinalValue(near, StatType.STRENGTH)
                            == nearBaseStrength + RitaChandlersAuraEffectService.STRENGTH_BONUS,
                    "In-range player did not gain exactly +1 Strength from the aura");
            helper.assertTrue(PlayerStatsService.getFinalValue(atEdge, StatType.STRENGTH)
                            == edgeBaseStrength + RitaChandlersAuraEffectService.STRENGTH_BONUS,
                    "Boundary player did not gain exactly +1 Strength from the aura");
            helper.assertTrue(PlayerStatsService.getFinalValue(far, StatType.STRENGTH)
                            == farBaseStrength,
                    "Out-of-range player's Strength was changed by the aura");

            assertDamageBonusApplies(wearer, near, helper);
            // "far" itself is never blessed, so its own attacks (regardless
            // of who they hit) must never carry the bonus.
            assertDamageBonusDoesNotApply(far, wearer, helper);

            // Leaving range: the in-range player moves far away. A fresh
            // pulse from the (stationary) wearer no longer reaches them; the
            // still-active blessing from the earlier pulse is simulated to
            // completion the same way its natural post-duration expiry
            // would remove it, since a GameTest cannot itself wait out real
            // server ticks.
            near.setPos(30.0D, near.getY(), near.getZ());
            helper.assertTrue(!RitaChandlersAuraEffectService.isInRange(wearer, near),
                    "Moved-away player is still considered in range");
            RitaChandlersAuraEffectService.pulseAuraFrom(wearer);
            near.removeEffect(ModEffects.RITA_CHANDLERS_BLESSING.get());
            helper.assertTrue(!RitaChandlersAuraEffectService.isBlessed(near),
                    "Blessing was not removed after leaving range and decaying");
            helper.assertTrue(PlayerStatsService.getFinalValue(near, StatType.STRENGTH)
                            == nearBaseStrength,
                    "Leaving range left a residual Strength bonus");
            // "near" is the one who lost the blessing here; its own attacks
            // (regardless of who they hit) must no longer carry the bonus.
            assertDamageBonusDoesNotApply(near, wearer, helper);

            // Re-entering range must reapply the buff.
            near.setPos(6.0D, near.getY(), near.getZ());
            RitaChandlersAuraEffectService.pulseAuraFrom(wearer);
            helper.assertTrue(RitaChandlersAuraEffectService.isBlessed(near),
                    "Re-entering range did not reapply the blessing");
            helper.assertTrue(PlayerStatsService.getFinalValue(near, StatType.STRENGTH)
                            == nearBaseStrength + RitaChandlersAuraEffectService.STRENGTH_BONUS,
                    "Re-entering range did not restore the aura's Strength bonus");

            helper.succeed();
        } finally {
            level.players().remove(wearer);
            level.players().remove(near);
            level.players().remove(atEdge);
            level.players().remove(far);
            wearer.discard();
            near.discard();
            atEdge.discard();
            far.discard();
        }
    }

    @GameTest(template = "empty")
    public static void ritaChandlersTokenAurasFromTwoWearersDoNotStackOnTheSameTarget(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer firstWearer = testPlayer(level, "rita-overlap-first", 0.0D);
        TestPlayer secondWearer = testPlayer(level, "rita-overlap-second", 2.0D);
        TestPlayer shared = testPlayer(level, "rita-overlap-shared", 1.0D);
        try {
            // Captured before either wearer's token is ever equipped/ticked.
            int sharedBaseStrength =
                    PlayerStatsService.getFinalValue(shared, StatType.STRENGTH);

            equip(firstWearer, helper);
            equip(secondWearer, helper);
            settleCurioChange(firstWearer);
            settleCurioChange(secondWearer);

            RitaChandlersAuraEffectService.pulseAuraFrom(firstWearer);
            RitaChandlersAuraEffectService.pulseAuraFrom(secondWearer);

            helper.assertTrue(RitaChandlersAuraEffectService.isBlessed(shared),
                    "Player covered by two overlapping auras was not blessed");
            MobEffectInstance instance =
                    shared.getEffect(ModEffects.RITA_CHANDLERS_BLESSING.get());
            helper.assertTrue(instance != null && instance.getAmplifier() == 0,
                    "Two overlapping same-name auras stacked amplifier instead of"
                            + " sharing a single non-stacking instance");
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(shared, StatType.STRENGTH)
                            == sharedBaseStrength
                                    + RitaChandlersAuraEffectService.STRENGTH_BONUS,
                    "Overlapping auras granted more than a single +1 Strength");

            helper.succeed();
        } finally {
            level.players().remove(firstWearer);
            level.players().remove(secondWearer);
            level.players().remove(shared);
            firstWearer.discard();
            secondWearer.discard();
            shared.discard();
        }
    }

    /**
     * Asserts a hit from {@code attacker} onto {@code victim} carries
     * exactly the +2 aura bonus. Builds and dispatches a
     * {@link LivingHurtEvent} directly to
     * {@link RitaChandlersTokenEffectEvents#onLivingHurt} instead of routing
     * through {@code LivingEntity#hurt}, so the assertion is not entangled
     * with vanilla's unrelated post-hit invulnerability window (which would
     * otherwise silently reduce or drop a second hit on the same test
     * player within the same tick).
     */
    private static void assertDamageBonusApplies(
            ServerPlayer attacker, LivingEntity victim, GameTestHelper helper) {
        DamageSource source = victim.damageSources().playerAttack(attacker);
        LivingHurtEvent event = new LivingHurtEvent(victim, source, 3.0F);
        RitaChandlersTokenEffectEvents.onLivingHurt(event);
        float expected = 3.0F + RitaChandlersAuraEffectService.DAMAGE_BONUS;
        helper.assertTrue(Math.abs(event.getAmount() - expected) < 0.001F,
                "Blessed attacker's hit did not carry exactly +2 bonus damage"
                        + " (expected " + expected + ", got " + event.getAmount() + ")");
    }

    private static void assertDamageBonusDoesNotApply(
            ServerPlayer attacker, LivingEntity victim, GameTestHelper helper) {
        DamageSource source = victim.damageSources().playerAttack(attacker);
        LivingHurtEvent event = new LivingHurtEvent(victim, source, 3.0F);
        RitaChandlersTokenEffectEvents.onLivingHurt(event);
        helper.assertTrue(Math.abs(event.getAmount() - 3.0F) < 0.001F,
                "Unblessed attacker's hit incorrectly carried the aura's bonus damage");
    }

    private static void equip(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler tokenHandler = handler(player, CurioSlotIds.TOKEN, helper);
        tokenHandler.getStacks().setStackInSlot(
                0, new ItemStack(ModItems.RITA_CHANDLERS_TOKEN.get()));
    }

    private static Zombie zombie(
            ServerLevel level, ServerPlayer near, double offset, GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(zombie != null, "Could not create test zombie");
        zombie.setPos(near.getX() + offset, near.getY(), near.getZ());
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        level.players().add(player);
        return player;
    }

    private static ICurioStacksHandler handler(
            ServerPlayer player, String slot, GameTestHelper helper) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(slot))
                .orElse(null);
        helper.assertTrue(handler != null, "Missing Curios handler: " + slot);
        return handler;
    }

    private static void settleCurioChange(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public void sendSystemMessage(Component message) {
            // GameTest players intentionally have no network connection.
        }

        @Override
        protected void onEffectAdded(MobEffectInstance effectInstance, Entity source) {
        }

        @Override
        protected void onEffectUpdated(
                MobEffectInstance effectInstance, boolean forced, Entity source) {
        }

        @Override
        protected void onEffectRemoved(MobEffectInstance effectInstance) {
        }
    }
}
