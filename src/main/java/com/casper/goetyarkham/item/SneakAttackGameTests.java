package com.casper.goetyarkham.item;

import com.Polarice3.Goety.utils.ModDamageSource;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.init.ModDamageTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Covers {@link SneakAttackItem}: registration, the Asset Curios tag,
 * tooltip layout, and - primarily through real {@link LivingHurtEvent}
 * postings rather than calling {@link SneakAttackService} in isolation -
 * the full-health/×2 behavior across melee, vanilla projectiles, Goety
 * spell damage, TACZ bullet damage, Goety indirect (owned/summon) damage,
 * environmental damage, and multiplayer isolation.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SneakAttackGameTests {
    private static final String BATCH = "goetyarkham:sneak_attack";
    private static final float EPSILON = 1.0e-4F;
    private static final float BASE_DAMAGE = 4.0F;

    private SneakAttackGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void registrationTagsAndTooltip(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "sneak_attack");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.SNEAK_ATTACK.get())),
                "Sneak Attack registry ID mismatch");

        SneakAttackItem item = ModItems.SNEAK_ATTACK.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1, "Sneak Attack must not stack");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(CurioSlotIds.ASSET)),
                "Sneak Attack item tag must expose only the asset slot");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 3, "Sneak Attack tooltip line count mismatch");
        helper.assertTrue(tooltip.get(0).getContents()
                        instanceof TranslatableContents slotHeading
                        && "tooltip.goetyarkham.sneak_attack.slot".equals(slotHeading.getKey())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(0).getStyle().getColor()),
                "Sneak Attack slot line mismatch");
        helper.assertTrue(tooltip.get(1).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY.equals(heading.getKey())
                        && TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                                .equals(tooltip.get(1).getStyle().getColor()),
                "Sneak Attack when-worn heading is not the shared yellow heading");
        helper.assertTrue(tooltip.get(2).getContents()
                        instanceof TranslatableContents effect
                        && "tooltip.goetyarkham.sneak_attack.effect".equals(effect.getKey())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(2).getStyle().getColor()),
                "Sneak Attack effect tooltip line mismatch");

        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void fullHealthMeleeDoublesDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-melee-full", 3.0D);
        Zombie target = fullHealthZombie(level, attacker, 3.0D);
        try {
            equip(attacker, ModItems.SNEAK_ATTACK.get());

            DamageSource source = attacker.damageSources().playerAttack(attacker);
            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE * 2.0F,
                    "A full-health melee hit from an equipped attacker must deal exactly 2x damage");
            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void nonFullHealthMeleeKeepsBaseDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-melee-hurt", 6.0D);
        Zombie target = zombie(level, attacker, 3.0D);
        target.setHealth(target.getMaxHealth() - 1.0F);
        try {
            equip(attacker, ModItems.SNEAK_ATTACK.get());

            DamageSource source = attacker.damageSources().playerAttack(attacker);
            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE,
                    "A hit against a target missing even 1 HP must not be doubled");
            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void secondConsecutiveHitDoesNotDouble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-sequential", 9.0D);
        Zombie target = fullHealthZombie(level, attacker, 3.0D);
        try {
            equip(attacker, ModItems.SNEAK_ATTACK.get());
            DamageSource source = attacker.damageSources().playerAttack(attacker);

            LivingHurtEvent first = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(first);
            assertAmount(helper, first, BASE_DAMAGE * 2.0F,
                    "The first hit on a full-health target must be doubled");
            // Apply the resolved damage so the target is no longer at full health,
            // exactly as real gameplay would after LivingHurtEvent hands off to
            // Minecraft's normal health subtraction.
            target.setHealth(target.getHealth() - first.getAmount());

            LivingHurtEvent second = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(second);
            assertAmount(helper, second, BASE_DAMAGE,
                    "An immediate follow-up hit on a now-damaged target must not be doubled");

            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void healingBackToFullRetriggersDouble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-reheal", 12.0D);
        Zombie target = fullHealthZombie(level, attacker, 3.0D);
        try {
            equip(attacker, ModItems.SNEAK_ATTACK.get());
            DamageSource source = attacker.damageSources().playerAttack(attacker);

            LivingHurtEvent first = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(first);
            target.setHealth(target.getHealth() - first.getAmount());

            LivingHurtEvent second = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(second);
            assertAmount(helper, second, BASE_DAMAGE,
                    "Test setup expected the follow-up hit to not be doubled before healing");

            target.setHealth(target.getMaxHealth());
            LivingHurtEvent third = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(third);
            assertAmount(helper, third, BASE_DAMAGE * 2.0F,
                    "Healing the target back to full health must re-qualify the next hit for 2x");

            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void unequippedAttackerGetsNoBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-unequipped", 15.0D);
        Zombie target = fullHealthZombie(level, attacker, 3.0D);
        try {
            DamageSource source = attacker.damageSources().playerAttack(attacker);
            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE,
                    "An attacker with no Sneak Attack equipped must never get the bonus");
            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void unequippingRestoresNormalDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-unequip-flow", 18.0D);
        Zombie target = fullHealthZombie(level, attacker, 3.0D);
        try {
            equip(attacker, ModItems.SNEAK_ATTACK.get());
            DamageSource source = attacker.damageSources().playerAttack(attacker);

            LivingHurtEvent first = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(first);
            assertAmount(helper, first, BASE_DAMAGE * 2.0F,
                    "Test setup expected the equipped hit to be doubled");

            unequip(attacker);
            target.setHealth(target.getMaxHealth());
            LivingHurtEvent second = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(second);
            assertAmount(helper, second, BASE_DAMAGE,
                    "Unequipping Sneak Attack must immediately restore normal damage");

            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void traceableProjectileDoublesFullHealthDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-arrow-full", 21.0D);
        Zombie target = fullHealthZombie(level, attacker, 3.0D);
        try {
            equip(attacker, ModItems.SNEAK_ATTACK.get());
            AbstractArrow arrow = EntityType.ARROW.create(level);
            helper.assertTrue(arrow != null, "Could not create test arrow");
            arrow.setOwner(attacker);
            DamageSource source = attacker.damageSources().arrow(arrow, attacker);

            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE * 2.0F,
                    "An arrow traceable to an equipped shooter must deal 2x damage to a full-health target");
            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void traceableProjectileAgainstHurtTargetDoesNotDouble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-arrow-hurt", 24.0D);
        Zombie target = zombie(level, attacker, 3.0D);
        target.setHealth(target.getMaxHealth() - 2.0F);
        try {
            equip(attacker, ModItems.SNEAK_ATTACK.get());
            AbstractArrow arrow = EntityType.ARROW.create(level);
            helper.assertTrue(arrow != null, "Could not create test arrow");
            arrow.setOwner(attacker);
            DamageSource source = attacker.damageSources().arrow(arrow, attacker);

            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE,
                    "An arrow hitting a non-full-health target must not be doubled");
            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void goetySpellDamageFromCasterDoublesFullHealthDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer caster = testPlayer(level, "sneak-attack-spell", 27.0D);
        Zombie target = fullHealthZombie(level, caster, 3.0D);
        try {
            equip(caster, ModItems.SNEAK_ATTACK.get());
            // Same real Goety magic-damage construction this addon's own
            // HeirloomSoulSpendEffectService uses: the caster as both direct
            // and causing entity.
            DamageSource source = ModDamageSource.magicBolt(caster, caster);

            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE * 2.0F,
                    "Goety spell damage traceable to an equipped caster must deal 2x to a full-health target");
            helper.succeed();
        } finally {
            target.discard();
            discard(caster);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void goetyOwnedSummonDamageFromCasterDoublesFullHealthDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer caster = testPlayer(level, "sneak-attack-summon", 30.0D);
        Zombie target = fullHealthZombie(level, caster, 3.0D);
        Zombie summon = zombie(level, caster, -3.0D);
        try {
            equip(caster, ModItems.SNEAK_ATTACK.get());
            // OwnedDamageSource: the summoned creature is the direct/causing
            // entity, the caster is only reachable through getOwner().
            DamageSource source = ModDamageSource.summonAttack(summon, caster);

            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE * 2.0F,
                    "Goety owned/summon damage traceable to an equipped caster must deal 2x"
                            + " to a full-health target");
            helper.succeed();
        } finally {
            target.discard();
            summon.discard();
            discard(caster);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void taczBulletDamageFromShooterDoublesFullHealthDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer shooter = testPlayer(level, "sneak-attack-tacz", 33.0D);
        Zombie target = fullHealthZombie(level, shooter, 3.0D);
        try {
            equip(shooter, ModItems.SNEAK_ATTACK.get());
            DamageSource source = ModDamageTypes.Sources.bullet(
                    level.registryAccess(), shooter, shooter, false);

            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE * 2.0F,
                    "TACZ bullet damage traceable to an equipped shooter must deal 2x"
                            + " to a full-health target");
            helper.succeed();
        } finally {
            target.discard();
            discard(shooter);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void environmentalDamageWithoutAttackerNeverDoubles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie target = fullHealthZombie(level, null, 3.0D);
        try {
            DamageSource source = target.damageSources().lava();
            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE,
                    "Environmental damage with no traceable attacker must never be doubled,"
                            + " even against a full-health target");
            helper.succeed();
        } finally {
            target.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void multiplayerIsolationOnlyEquippedAttackerGetsBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearerA = testPlayer(level, "sneak-attack-mp-a", 40.0D);
        TestPlayer bystanderB = testPlayer(level, "sneak-attack-mp-b", 45.0D);
        Zombie targetA = fullHealthZombie(level, wearerA, 3.0D);
        Zombie targetB = fullHealthZombie(level, bystanderB, 3.0D);
        try {
            equip(wearerA, ModItems.SNEAK_ATTACK.get());
            // bystanderB intentionally never equips Sneak Attack.

            LivingHurtEvent eventA = new LivingHurtEvent(
                    targetA, wearerA.damageSources().playerAttack(wearerA), BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(eventA);
            assertAmount(helper, eventA, BASE_DAMAGE * 2.0F,
                    "Player A (equipped) must deal 2x damage to their own full-health target");

            LivingHurtEvent eventB = new LivingHurtEvent(
                    targetB, bystanderB.damageSources().playerAttack(bystanderB), BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(eventB);
            assertAmount(helper, eventB, BASE_DAMAGE,
                    "Player B (not equipped) must not receive A's bonus merely by being nearby");

            helper.succeed();
        } finally {
            targetA.discard();
            targetB.discard();
            discard(wearerA);
            discard(bystanderB);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void unequippingBeforeProjectileImpactCancelsBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-unequip-midflight", 48.0D);
        Zombie target = fullHealthZombie(level, attacker, 3.0D);
        try {
            equip(attacker, ModItems.SNEAK_ATTACK.get());
            AbstractArrow arrow = EntityType.ARROW.create(level);
            helper.assertTrue(arrow != null, "Could not create test arrow");
            arrow.setOwner(attacker);
            DamageSource source = attacker.damageSources().arrow(arrow, attacker);

            // Unequip after the arrow is "in flight" but before it "lands":
            // eligibility must be judged by equip state at impact time only.
            unequip(attacker);

            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE,
                    "Unequipping before a mid-flight arrow lands must cancel the bonus");
            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void equippingAfterProjectileLaunchStillAppliesAtImpact(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "sneak-attack-equip-midflight", 51.0D);
        Zombie target = fullHealthZombie(level, attacker, 3.0D);
        try {
            AbstractArrow arrow = EntityType.ARROW.create(level);
            helper.assertTrue(arrow != null, "Could not create test arrow");
            arrow.setOwner(attacker);
            DamageSource source = attacker.damageSources().arrow(arrow, attacker);

            // Equip after the arrow is "in flight" but before it "lands":
            // eligibility must be judged by equip state at impact time only.
            equip(attacker, ModItems.SNEAK_ATTACK.get());

            LivingHurtEvent event = new LivingHurtEvent(target, source, BASE_DAMAGE);
            MinecraftForge.EVENT_BUS.post(event);

            assertAmount(helper, event, BASE_DAMAGE * 2.0F,
                    "Equipping before a mid-flight arrow lands must apply the bonus at impact");
            helper.succeed();
        } finally {
            target.discard();
            discard(attacker);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void modelLangAndTextureResourcesArePresent(GameTestHelper helper) {
        String model = readResourceAsString(
                "/assets/goetyarkham/models/item/sneak_attack.json");
        helper.assertTrue(model.contains("\"parent\": \"minecraft:item/generated\""),
                "Sneak Attack item model must use the minecraft:item/generated parent");
        helper.assertTrue(model.contains("\"goetyarkham:item/sneak_attack\""),
                "Sneak Attack item model must reference the goetyarkham:item/sneak_attack texture");

        String zhCn = readResourceAsString("/assets/goetyarkham/lang/zh_cn.json");
        helper.assertTrue(zhCn.contains("\"item.goetyarkham.sneak_attack\": \"偷袭\""),
                "zh_cn.json is missing the Sneak Attack item name");
        helper.assertTrue(zhCn.contains("\"tooltip.goetyarkham.sneak_attack.effect\""),
                "zh_cn.json is missing the Sneak Attack effect tooltip key");

        String enUs = readResourceAsString("/assets/goetyarkham/lang/en_us.json");
        helper.assertTrue(enUs.contains("\"item.goetyarkham.sneak_attack\": \"Sneak Attack\""),
                "en_us.json is missing the Sneak Attack item name");
        helper.assertTrue(enUs.contains("\"tooltip.goetyarkham.sneak_attack.effect\""),
                "en_us.json is missing the Sneak Attack effect tooltip key");

        String assetTag = readResourceAsString("/data/curios/tags/items/asset.json");
        helper.assertTrue(assetTag.contains("\"goetyarkham:sneak_attack\""),
                "curios:tags/items/asset.json is missing the Sneak Attack entry");

        helper.assertTrue(
                resourceExistsAndNonEmpty("/assets/goetyarkham/textures/item/sneak_attack.png"),
                "sneak_attack.png texture is missing or empty");
        helper.succeed();
    }

    private static void assertAmount(
            GameTestHelper helper, LivingHurtEvent event, float expected, String message) {
        helper.assertTrue(Math.abs(event.getAmount() - expected) < EPSILON,
                message + ", found " + event.getAmount());
    }

    private static void equip(ServerPlayer player, net.minecraft.world.item.Item item) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.ASSET))
                .orElseThrow(() -> new IllegalStateException("Missing asset Curios handler"));
        handler.getStacks().setStackInSlot(0, new ItemStack(item));
    }

    private static void unequip(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.ASSET))
                .ifPresent(handler -> handler.getStacks().setStackInSlot(0, ItemStack.EMPTY));
    }

    private static Zombie zombie(ServerLevel level, ServerPlayer near, double offset) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        double x = near != null ? near.getX() + offset : offset;
        double z = near != null ? near.getZ() : 0.0D;
        zombie.setPos(x, 1.0D, z);
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static Zombie fullHealthZombie(ServerLevel level, ServerPlayer near, double offset) {
        Zombie zombie = zombie(level, near, offset);
        zombie.setHealth(zombie.getMaxHealth());
        return zombie;
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        return player;
    }

    private static void discard(TestPlayer player) {
        player.discard();
    }

    private static String readResourceAsString(String path) {
        try (InputStream stream = SneakAttackGameTests.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read classpath resource: " + path, exception);
        }
    }

    private static boolean resourceExistsAndNonEmpty(String path) {
        try (InputStream stream = SneakAttackGameTests.class.getResourceAsStream(path)) {
            return stream != null && stream.read() != -1;
        } catch (IOException exception) {
            return false;
        }
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public void sendSystemMessage(Component message) {
        }

        @Override
        protected void onEffectAdded(
                net.minecraft.world.effect.MobEffectInstance effectInstance,
                net.minecraft.world.entity.Entity source) {
        }

        @Override
        protected void onEffectUpdated(
                net.minecraft.world.effect.MobEffectInstance effectInstance,
                boolean forced,
                net.minecraft.world.entity.Entity source) {
        }

        @Override
        protected void onEffectRemoved(
                net.minecraft.world.effect.MobEffectInstance effectInstance) {
        }
    }
}
