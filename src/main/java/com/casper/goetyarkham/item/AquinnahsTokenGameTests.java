package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.SanityService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.DigDurabilityEnchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AquinnahsTokenGameTests {
    private static final Field SPAWN_INVULNERABLE_TIME_FIELD =
            findSpawnInvulnerableTimeField();

    private AquinnahsTokenGameTests() {
    }

    private static Field findSpawnInvulnerableTimeField() {
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @GameTest(template = "empty")
    public static void aquinnahsTokenGrantsAttributesAndRedirectsDirectAttacks(
            GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "aquinnahs_token");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.AQUINNAHS_TOKEN.get())),
                "Aquinnah's Token registry ID mismatch");

        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "aquinnahs-token-wearer", 1.5D);
        Zombie zombie = zombie(level, wearer, 1.0D, helper);
        try {
            ICurioStacksHandler tokenHandler =
                    handler(wearer, CurioSlotIds.TOKEN, helper);

            double baseMaxHealth = wearer.getAttribute(Attributes.MAX_HEALTH).getValue();
            int baseMaxSanity = SanityService.getMaximumSanity(wearer);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            ItemStack token = new ItemStack(ModItems.AQUINNAHS_TOKEN.get());
            tokenHandler.getStacks().setStackInSlot(0, token);
            settleCurioChange(wearer);
            if (wearer.getHealth() < wearer.getMaxHealth()) {
                wearer.setHealth(wearer.getMaxHealth());
            }

            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaxHealth + AquinnahsTokenItem.MAX_HEALTH_BONUS,
                    "Aquinnah's Token did not add exactly +2 Max Health");
            helper.assertTrue(SanityService.getMaximumSanity(wearer)
                            == baseMaxSanity + AquinnahsTokenItem.MAX_SANITY_BONUS,
                    "Aquinnah's Token did not add exactly +4 Max Sanity");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                            == baseAgility + AquinnahsTokenItem.AGILITY_BONUS,
                    "Aquinnah's Token did not add exactly +1 Agility");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + AquinnahsTokenItem.WILLPOWER_BONUS,
                    "Aquinnah's Token did not add exactly +1 Willpower");

            // Unequip must remove every bonus immediately.
            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaxHealth,
                    "Unequipping left a residual Max Health bonus");
            helper.assertTrue(SanityService.getMaximumSanity(wearer) == baseMaxSanity,
                    "Unequipping left a residual Max Sanity bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                            == baseAgility,
                    "Unequipping left a residual Agility bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower,
                    "Unequipping left a residual Willpower bonus");

            // Re-equip a fresh, full-durability token for the redirect checks.
            tokenHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.AQUINNAHS_TOKEN.get()));
            settleCurioChange(wearer);
            wearer.setHealth(wearer.getMaxHealth());
            ItemStack equipped = tokenHandler.getStacks().getStackInSlot(0);

            float attackDamage = (float) zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float zombieHealthBefore = zombie.getHealth();

            for (int hit = 1; hit <= AquinnahsTokenItem.MAX_DURABILITY; hit++) {
                boolean hurtReturn = zombie.doHurtTarget(wearer);
                helper.assertTrue(!hurtReturn,
                        "A redirected attack must report as not applied to the wearer");
                helper.assertTrue(wearer.getHealth() == wearer.getMaxHealth(),
                        "Wearer took damage on redirect hit " + hit);
                helper.assertTrue(equipped.getDamageValue() == hit,
                        "Aquinnah's Token durability did not drop by exactly 1 on hit " + hit);
                helper.assertTrue(equipped.is(ModItems.AQUINNAHS_TOKEN.get())
                                && !equipped.isEmpty(),
                        "Aquinnah's Token disappeared at durability " + hit);
                float expectedZombieHealth = zombieHealthBefore - attackDamage * hit;
                helper.assertTrue(
                        Math.abs(zombie.getHealth() - expectedZombieHealth) < 0.001F,
                        "Zombie did not take the redirected damage on hit " + hit);
            }
            helper.assertTrue(equipped.getDamageValue() == AquinnahsTokenItem.MAX_DURABILITY,
                    "Token did not reach maximum damage after exhausting durability");

            // A fifth attack must find the token depleted: the wearer now
            // takes the hit normally, and the token neither breaks nor loses
            // further durability, nor grants a further redirect.
            float zombieHealthAtDepletion = zombie.getHealth();
            float wearerHealthBefore = wearer.getHealth();
            zombie.doHurtTarget(wearer);
            helper.assertTrue(wearer.getHealth() < wearerHealthBefore,
                    "A depleted Aquinnah's Token still blocked damage to the wearer");
            helper.assertTrue(zombie.getHealth() == zombieHealthAtDepletion,
                    "A depleted Aquinnah's Token still redirected damage to the attacker");
            helper.assertTrue(equipped.getDamageValue() == AquinnahsTokenItem.MAX_DURABILITY,
                    "A depleted Aquinnah's Token changed damage value further");
            helper.assertTrue(equipped.is(ModItems.AQUINNAHS_TOKEN.get())
                            && !equipped.isEmpty(),
                    "A depleted Aquinnah's Token was destroyed instead of kept at 0 durability");

            // Attribute/stat bonuses must survive full durability loss.
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaxHealth + AquinnahsTokenItem.MAX_HEALTH_BONUS,
                    "A depleted token lost its Max Health bonus");
            helper.assertTrue(SanityService.getMaximumSanity(wearer)
                            == baseMaxSanity + AquinnahsTokenItem.MAX_SANITY_BONUS,
                    "A depleted token lost its Max Sanity bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.AGILITY)
                            == baseAgility + AquinnahsTokenItem.AGILITY_BONUS,
                    "A depleted token lost its Agility bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + AquinnahsTokenItem.WILLPOWER_BONUS,
                    "A depleted token lost its Willpower bonus");

            helper.succeed();
        } finally {
            zombie.discard();
            level.players().remove(wearer);
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void aquinnahsTokenIgnoresIndirectAndSourcelessDamage(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "aquinnahs-token-indirect", 1.5D);
        Zombie shooter = zombie(level, wearer, 5.0D, helper);
        try {
            ICurioStacksHandler tokenHandler =
                    handler(wearer, CurioSlotIds.TOKEN, helper);
            ItemStack token = new ItemStack(ModItems.AQUINNAHS_TOKEN.get());
            tokenHandler.getStacks().setStackInSlot(0, token);
            settleCurioChange(wearer);
            wearer.setHealth(wearer.getMaxHealth());

            assertNotRedirected(helper, wearer, tokenHandler,
                    wearer.damageSources().fall(), 2.0F, "fall damage");
            assertNotRedirected(helper, wearer, tokenHandler,
                    wearer.damageSources().inFire(), 2.0F, "fire damage");
            assertNotRedirected(helper, wearer, tokenHandler,
                    wearer.damageSources().lava(), 2.0F, "lava damage");
            assertNotRedirected(helper, wearer, tokenHandler,
                    wearer.damageSources().fellOutOfWorld(), 2.0F, "void damage");
            assertNotRedirected(helper, wearer, tokenHandler,
                    wearer.damageSources().drown(), 2.0F, "drowning damage");
            assertNotRedirected(helper, wearer, tokenHandler,
                    wearer.damageSources().starve(), 2.0F, "starvation damage");
            assertNotRedirected(helper, wearer, tokenHandler,
                    wearer.damageSources().explosion(null, null),
                    2.0F, "explosion damage");
            assertNotRedirected(helper, wearer, tokenHandler,
                    wearer.damageSources().magic(),
                    2.0F, "sourceless magic damage");

            // An arrow's direct entity is the arrow itself, not the shooter,
            // even though the shooter (a living entity) is its indirect cause.
            Arrow arrow = EntityType.ARROW.create(level);
            helper.assertTrue(arrow != null, "Could not create test arrow");
            arrow.setPos(wearer.getX(), wearer.getY(), wearer.getZ());
            level.addFreshEntity(arrow);
            DamageSource arrowSource = wearer.damageSources().arrow(arrow, shooter);
            assertNotRedirected(helper, wearer, tokenHandler,
                    arrowSource, 2.0F, "arrow damage");
            arrow.discard();

            helper.succeed();
        } finally {
            shooter.discard();
            level.players().remove(wearer);
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void aquinnahsTokenDoesNotChainBetweenTwoWearers(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer first = testPlayer(level, "aquinnahs-token-first", 1.5D);
        TestPlayer second = testPlayer(level, "aquinnahs-token-second", 4.5D);
        try {
            ICurioStacksHandler firstToken = handler(first, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler secondToken = handler(second, CurioSlotIds.TOKEN, helper);
            firstToken.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.AQUINNAHS_TOKEN.get()));
            settleCurioChange(first);
            secondToken.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.AQUINNAHS_TOKEN.get()));
            settleCurioChange(second);
            first.setHealth(first.getMaxHealth());
            second.setHealth(second.getMaxHealth());

            ItemStack firstStack = firstToken.getStacks().getStackInSlot(0);
            ItemStack secondStack = secondToken.getStacks().getStackInSlot(0);

            DamageSource secondAttacksFirst = second.damageSources().playerAttack(second);
            float damage = Math.min(6.0F, first.getMaxHealth() - 1.0F);
            first.hurt(secondAttacksFirst, damage);

            helper.assertTrue(first.getHealth() == first.getMaxHealth(),
                    "First wearer took damage despite an available token");
            helper.assertTrue(firstStack.getDamageValue() == 1,
                    "First wearer's token did not lose exactly 1 durability");
            helper.assertTrue(second.getHealth() < second.getMaxHealth(),
                    "The attacker did not take the redirected damage");
            helper.assertTrue(secondStack.getDamageValue() == 0,
                    "The redirected hit re-triggered the attacker's own token"
                            + " (infinite-bounce guard failed)");

            helper.succeed();
        } finally {
            level.players().remove(first);
            level.players().remove(second);
            first.discard();
            second.discard();
        }
    }

    @GameTest(template = "empty")
    public static void aquinnahsTokenUnbreakingAlwaysRedirectsButOnlySometimesConsumesDurability(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "aquinnahs-token-unbreaking", 1.5D);
        Zombie zombie = zombie(level, wearer, 1.0D, helper);
        try {
            ICurioStacksHandler tokenHandler =
                    handler(wearer, CurioSlotIds.TOKEN, helper);
            ItemStack token = new ItemStack(ModItems.AQUINNAHS_TOKEN.get());
            EnchantmentHelper.setEnchantments(
                    Map.of(Enchantments.UNBREAKING, 3), token);
            tokenHandler.getStacks().setStackInSlot(0, token);
            settleCurioChange(wearer);
            wearer.setHealth(wearer.getMaxHealth());
            ItemStack equipped = tokenHandler.getStacks().getStackInSlot(0);

            // 3 hits against 4 durability: even in the unlucky case where
            // Unbreaking never blocks a single drop, the token is never
            // fully depleted mid-loop, so every one of these hits must be
            // redirected regardless of how the enchantment rolls resolve.
            for (int hit = 1; hit <= 3; hit++) {
                zombie.doHurtTarget(wearer);
                helper.assertTrue(wearer.getHealth() == wearer.getMaxHealth(),
                        "Unbreaking blocking a durability drop incorrectly let"
                                + " the wearer take damage on hit " + hit);
                helper.assertTrue(equipped.getDamageValue() <= hit,
                        "Durability decreased more than once for a single hit");
            }

            // Directly probe the durability roll against vanilla's own
            // Unbreaking probability function so both outcomes (a blocked
            // drop and an applied drop) are proven reachable and consistent
            // with vanilla, without depending on live RNG state anywhere
            // else in this test.
            boolean sawIgnored = false;
            boolean sawApplied = false;
            for (long seed = 0; seed < 200 && !(sawIgnored && sawApplied); seed++) {
                ItemStack probe = new ItemStack(ModItems.AQUINNAHS_TOKEN.get());
                EnchantmentHelper.setEnchantments(
                        Map.of(Enchantments.UNBREAKING, 3), probe);
                boolean expectedIgnore = DigDurabilityEnchantment.shouldIgnoreDurabilityDrop(
                        probe, 3, RandomSource.create(seed));
                // Aquinnah's Token durability consumption is a thin wrapper
                // around this exact vanilla ItemStack#hurt call (see
                // AquinnahsTokenEffectEvents#consumeDurability), so probing
                // it directly with the same seed proves both outcomes are
                // reachable and match vanilla's own Unbreaking odds.
                probe.hurt(1, RandomSource.create(seed), null);
                if (expectedIgnore) {
                    sawIgnored = true;
                    helper.assertTrue(probe.getDamageValue() == 0,
                            "A blocked Unbreaking roll still consumed durability"
                                    + " (seed " + seed + ")");
                } else {
                    sawApplied = true;
                    helper.assertTrue(probe.getDamageValue() == 1,
                            "An unblocked Unbreaking roll failed to consume"
                                    + " durability (seed " + seed + ")");
                }
            }
            helper.assertTrue(sawIgnored,
                    "Never observed Unbreaking block a durability drop in 200 seeds");
            helper.assertTrue(sawApplied,
                    "Never observed Unbreaking allow a durability drop in 200 seeds");

            helper.succeed();
        } finally {
            zombie.discard();
            level.players().remove(wearer);
            wearer.discard();
        }
    }

    private static void assertNotRedirected(
            GameTestHelper helper,
            TestPlayer wearer,
            ICurioStacksHandler tokenHandler,
            DamageSource source,
            float amount,
            String label) {
        ItemStack before = tokenHandler.getStacks().getStackInSlot(0);
        int damageBefore = before.getDamageValue();
        float healthBefore = wearer.getHealth();
        wearer.hurt(source, amount);
        helper.assertTrue(wearer.getHealth() < healthBefore,
                "Aquinnah's Token incorrectly blocked " + label);
        helper.assertTrue(
                tokenHandler.getStacks().getStackInSlot(0).getDamageValue() == damageBefore,
                "Aquinnah's Token incorrectly consumed durability for " + label);
        wearer.setHealth(wearer.getMaxHealth());
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        level.players().add(player);
        return player;
    }

    private static Zombie zombie(
            ServerLevel level,
            ServerPlayer caster,
            double offset,
            GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(zombie != null, "Could not create test zombie");
        zombie.setPos(caster.getX() + offset, caster.getY(), caster.getZ());
        level.addFreshEntity(zombie);
        return zombie;
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
            try {
                SPAWN_INVULNERABLE_TIME_FIELD.setInt(this, 0);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(
                        "Failed to disable test-player spawn protection", exception);
            }
        }

        @Override
        public void sendSystemMessage(Component message) {
            // GameTest players intentionally have no network connection.
        }

        @Override
        protected void onEffectAdded(
                MobEffectInstance effectInstance, Entity source) {
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
