package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.attribute.ModAttributes;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Covers {@link SwitchbladeItem}: registration, durability/stack limits,
 * main-hand-only Attack Damage/Speed, the live-computed Agility bonus
 * ({@link SwitchbladeAgilityBonusService}), the Strength-gated bonus damage
 * ({@link SwitchbladeDamageBonusService}), and the required resource files.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SwitchbladeGameTests {
    private static final String BATCH = "goetyarkham:switchblade";

    private SwitchbladeGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void registrationDurabilityAndStackSizeAreExact(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "switchblade");
        helper.assertTrue(
                expectedId.equals(ForgeRegistries.ITEMS.getKey(ModItems.SWITCHBLADE.get())),
                "Switchblade registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.SWITCHBLADE.get());
        helper.assertTrue(stack.getMaxStackSize() == 1, "Switchblade must not stack");
        helper.assertTrue(stack.getMaxDamage() == SwitchbladeItem.MAX_DURABILITY,
                "Switchblade max durability must be exactly 250");
        helper.succeed();
    }

    /**
     * A throwaway {@link AttributeMap} built from {@link Player#createAttributes()}
     * (vanilla's own canonical default attack attributes), mirroring {@code
     * KnifeGameTests#mainHandGrantsExactFinalAttackDamageAndSpeed}, so the
     * assertion is tied only to this item's own math rather than any live
     * player's possibly-adjusted base values.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void mainHandGrantsExactFinalAttackDamageAndSpeed(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.SWITCHBLADE.get());
        AttributeMap attributes = new AttributeMap(Player.createAttributes().build());
        attributes.addTransientAttributeModifiers(
                stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
        helper.assertTrue(
                attributes.getValue(Attributes.ATTACK_DAMAGE) == SwitchbladeItem.FINAL_ATTACK_DAMAGE,
                "Main-hand final Attack Damage must be exactly 3.5, found "
                        + attributes.getValue(Attributes.ATTACK_DAMAGE));
        helper.assertTrue(
                attributes.getValue(Attributes.ATTACK_SPEED) == SwitchbladeItem.FINAL_ATTACK_SPEED,
                "Main-hand final Attack Speed must be exactly 2.0, found "
                        + attributes.getValue(Attributes.ATTACK_SPEED));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void nonMainHandSlotsGrantNoWeaponAttributes(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.SWITCHBLADE.get());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND) {
                continue;
            }
            helper.assertTrue(
                    stack.getAttributeModifiers(slot).isEmpty(),
                    "Switchblade granted attribute modifiers in slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void mainHandGrantsAgilityBonusMirroredToAttribute(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "switchblade-agility", 1.5D);
        try {
            int baseAgility = PlayerStatsService.getFinalValue(player, StatType.AGILITY);
            helper.assertTrue(baseAgility == 0, "Test setup expected zero baseline Agility");

            setMainHand(player, new ItemStack(ModItems.SWITCHBLADE.get()));

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.AGILITY)
                            == baseAgility + SwitchbladeItem.AGILITY_BONUS,
                    "Wielding the Switchblade in main hand did not grant +2 Agility");
            AttributeInstance agilityAttribute = player.getAttribute(ModAttributes.AGILITY.get());
            helper.assertTrue(
                    agilityAttribute != null
                            && agilityAttribute.getValue() == baseAgility + SwitchbladeItem.AGILITY_BONUS,
                    "Mirrored Agility attribute (client-visible value) did not match the final stat");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void offHandGrantsNoAgilityBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "switchblade-offhand", 4.5D);
        try {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SWITCHBLADE.get()));
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.AGILITY) == 0,
                    "Switchblade in the off hand incorrectly granted the Agility bonus");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void switchingAwayFromMainHandRestoresAgilityImmediately(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "switchblade-switch-away", 7.5D);
        try {
            setMainHand(player, new ItemStack(ModItems.SWITCHBLADE.get()));
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.AGILITY)
                            == SwitchbladeItem.AGILITY_BONUS,
                    "Test setup did not grant the Agility bonus while wielded");

            setMainHand(player, ItemStack.EMPTY);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.AGILITY) == 0,
                    "Switching away from the Switchblade did not immediately remove the Agility bonus");
            AttributeInstance agilityAttribute = player.getAttribute(ModAttributes.AGILITY.get());
            helper.assertTrue(
                    agilityAttribute != null && agilityAttribute.getValue() == 0,
                    "Mirrored Agility attribute did not immediately follow the hand swap");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void strengthBelowThresholdGrantsNoBonusDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "switchblade-strength-below", 10.5D);
        try {
            setMainHand(player, new ItemStack(ModItems.SWITCHBLADE.get()));
            PlayerStatsService.setBase(player, StatType.STRENGTH, 4);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 4,
                    "Test setup did not produce final Strength of exactly 4");

            AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(
                    attackDamage.getModifier(SwitchbladeDamageBonusService.ATTACK_DAMAGE_MODIFIER_ID)
                            == null,
                    "Below-threshold Strength still granted the conditional +2 Attack Damage");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void strengthAtThresholdGrantsBonusDamageMergedIntoAttackDamage(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "switchblade-strength-at", 13.5D);
        try {
            setMainHand(player, new ItemStack(ModItems.SWITCHBLADE.get()));
            PlayerStatsService.setBase(player, StatType.STRENGTH, 5);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 5,
                    "Test setup did not produce final Strength of exactly 5");

            AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeModifier modifier = attackDamage.getModifier(
                    SwitchbladeDamageBonusService.ATTACK_DAMAGE_MODIFIER_ID);
            helper.assertTrue(
                    modifier != null
                            && modifier.getAmount() == SwitchbladeDamageBonusService.DAMAGE_BONUS
                            && modifier.getOperation() == AttributeModifier.Operation.ADDITION,
                    "At-threshold Strength did not grant the conditional +2 Attack Damage");

            // The conditional bonus is a second ADDITION modifier on the same
            // Attack Damage attribute instance as the weapon's own base
            // modifier, so a single melee hit reads one aggregate value -
            // there is no separate damage instance or second hurt() call.
            double aggregate = attackDamage.getValue();
            helper.assertTrue(
                    aggregate == SwitchbladeItem.FINAL_ATTACK_DAMAGE
                            + SwitchbladeDamageBonusService.DAMAGE_BONUS,
                    "Base weapon damage and conditional bonus did not merge into one aggregate value, found "
                            + aggregate);
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void equipmentDrivenStrengthAlsoTriggersBonusDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "switchblade-equipment-strength", 16.5D);
        try {
            setMainHand(player, new ItemStack(ModItems.SWITCHBLADE.get()));
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 0,
                    "Test setup expected zero baseline Strength");

            PlayerStatsService.setEquipment(player, StatType.STRENGTH, 5);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 5,
                    "Equipment-driven Strength did not reach exactly 5");

            AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeModifier modifier = attackDamage.getModifier(
                    SwitchbladeDamageBonusService.ATTACK_DAMAGE_MODIFIER_ID);
            helper.assertTrue(
                    modifier != null && modifier.getAmount() == SwitchbladeDamageBonusService.DAMAGE_BONUS,
                    "Strength reaching 5 via equipment did not grant the conditional +2 Attack Damage");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void rangedDamageIgnoresConditionalBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "switchblade-ranged-attacker", 19.5D);
        try {
            setMainHand(attacker, new ItemStack(ModItems.SWITCHBLADE.get()));
            PlayerStatsService.setBase(attacker, StatType.STRENGTH, 5);
            helper.assertTrue(
                    attacker.getAttribute(Attributes.ATTACK_DAMAGE)
                            .getModifier(SwitchbladeDamageBonusService.ATTACK_DAMAGE_MODIFIER_ID) != null,
                    "Test setup did not grant the conditional Attack Damage bonus");

            AbstractArrow arrow = EntityType.ARROW.create(level);
            helper.assertTrue(arrow != null, "Could not create test arrow");
            arrow.setOwner(attacker);
            // An armor-less target (Pig, 0 base armor) so the assertion below
            // measures raw damage dealt, not damage reduced by the target's
            // own armor absorption.
            Pig target = pig(level, attacker, 3.0D);
            DamageSource arrowSource = attacker.damageSources().arrow(arrow, attacker);

            float arrowDamage = 3.0F;
            float healthBefore = target.getHealth();
            target.hurt(arrowSource, arrowDamage);
            float damageDealt = healthBefore - target.getHealth();

            helper.assertTrue(damageDealt == arrowDamage,
                    "Ranged (arrow) damage was affected by the wielder's melee conditional bonus, dealt "
                            + damageDealt + " expected " + arrowDamage);
            helper.succeed();
        } finally {
            PlayerStatsService.reset(attacker);
            attacker.discard();
        }
    }

    /**
     * Because the bonus is a fixed-UUID transient modifier merged onto {@link
     * Attributes#ATTACK_DAMAGE} rather than a separate event-driven damage
     * instance, a single {@code hurt} call using the aggregate attribute
     * value already includes it - there is no second damage instance, and
     * this call alone never touches the weapon's own durability (that is
     * exclusively {@link SwordItem#hurtEnemy}'s responsibility, covered
     * separately below).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void bonusDamageDoesNotCreateASecondDamageInstance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "switchblade-single-hit", 22.5D);
        try {
            ItemStack stack = new ItemStack(ModItems.SWITCHBLADE.get());
            setMainHand(attacker, stack);
            PlayerStatsService.setBase(attacker, StatType.STRENGTH, 5);

            double aggregate = attacker.getAttribute(Attributes.ATTACK_DAMAGE).getValue();
            helper.assertTrue(aggregate == SwitchbladeItem.FINAL_ATTACK_DAMAGE
                            + SwitchbladeDamageBonusService.DAMAGE_BONUS,
                    "Test setup did not produce the expected merged Attack Damage value");

            // Armor-less target so the health delta reflects raw damage dealt.
            Pig target = pig(level, attacker, 3.0D);
            float healthBefore = target.getHealth();
            target.hurt(attacker.damageSources().playerAttack(attacker), (float) aggregate);
            float damageDealt = healthBefore - target.getHealth();

            helper.assertTrue(damageDealt == (float) aggregate,
                    "A single hurt() call using the merged Attack Damage value did not deal exactly "
                            + aggregate + " damage, dealt " + damageDealt);
            helper.assertTrue(attacker.getMainHandItem().getDamageValue() == 0,
                    "Dealing damage alone (without hurtEnemy) must not consume durability");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(attacker);
            attacker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void singleMeleeHitConsumesExactlyOneDurability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "switchblade-durability", 25.5D);
        try {
            ItemStack stack = new ItemStack(ModItems.SWITCHBLADE.get());
            setMainHand(attacker, stack);
            PlayerStatsService.setBase(attacker, StatType.STRENGTH, 5);
            Zombie target = zombie(level, attacker, 3.0D);

            SwordItem switchbladeItem = (SwordItem) ModItems.SWITCHBLADE.get();
            switchbladeItem.hurtEnemy(stack, target, attacker);

            helper.assertTrue(stack.getDamageValue() == 1,
                    "A single successful melee hit must consume exactly 1 durability, found "
                            + stack.getDamageValue());
            helper.succeed();
        } finally {
            PlayerStatsService.reset(attacker);
            attacker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void modelLangAndTextureResourcesArePresent(GameTestHelper helper) {
        String model = readResourceAsString(
                "/assets/goetyarkham/models/item/switchblade.json");
        helper.assertTrue(model.contains("\"parent\": \"minecraft:item/handheld\""),
                "Switchblade item model must use the minecraft:item/handheld parent");
        helper.assertTrue(model.contains("\"goetyarkham:item/switchblade\""),
                "Switchblade item model must reference the goetyarkham:item/switchblade texture");

        String zhCn = readResourceAsString("/assets/goetyarkham/lang/zh_cn.json");
        helper.assertTrue(zhCn.contains("\"item.goetyarkham.switchblade\": \"弹簧刀\""),
                "zh_cn.json is missing the Switchblade item name");
        helper.assertTrue(zhCn.contains("\"tooltip.goetyarkham.switchblade.main_hand\""),
                "zh_cn.json is missing the Switchblade main-hand tooltip key");

        String enUs = readResourceAsString("/assets/goetyarkham/lang/en_us.json");
        helper.assertTrue(enUs.contains("\"item.goetyarkham.switchblade\": \"Switchblade\""),
                "en_us.json is missing the Switchblade item name");
        helper.assertTrue(enUs.contains("\"tooltip.goetyarkham.switchblade.main_hand\""),
                "en_us.json is missing the Switchblade main-hand tooltip key");

        helper.assertTrue(
                resourceExistsAndNonEmpty("/assets/goetyarkham/textures/item/switchblade.png"),
                "switchblade.png texture is missing or empty");
        helper.succeed();
    }

    private static void setMainHand(ServerPlayer player, ItemStack newStack) {
        ItemStack old = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, newStack);
        MinecraftForge.EVENT_BUS.post(
                new LivingEquipmentChangeEvent(player, EquipmentSlot.MAINHAND, old, newStack));
    }

    private static Zombie zombie(ServerLevel level, ServerPlayer near, double offset) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.setPos(near.getX() + offset, near.getY(), near.getZ());
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static Pig pig(ServerLevel level, ServerPlayer near, double offset) {
        Pig pig = EntityType.PIG.create(level);
        pig.setPos(near.getX() + offset, near.getY(), near.getZ());
        level.addFreshEntity(pig);
        return pig;
    }

    private static String readResourceAsString(String path) {
        try (InputStream stream = SwitchbladeGameTests.class.getResourceAsStream(path)) {
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
        try (InputStream stream = SwitchbladeGameTests.class.getResourceAsStream(path)) {
            return stream != null && stream.read() != -1;
        } catch (IOException exception) {
            return false;
        }
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        return player;
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
