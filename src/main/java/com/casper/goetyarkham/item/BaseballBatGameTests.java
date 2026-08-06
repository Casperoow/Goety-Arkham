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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
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
 * Covers {@link BaseballBatItem}: registration, durability/stack limits,
 * main-hand-only Attack Damage/Speed, the live-computed Strength bonus
 * ({@link BaseballBatStrengthBonusService}), melee durability loss, absence
 * of sword/tool special mechanics, and the required resource files.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BaseballBatGameTests {
    private static final String BATCH = "goetyarkham:baseball_bat";
    private static final double EPSILON = 1.0e-4D;

    private BaseballBatGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void registrationDurabilityAndStackSizeAreExact(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "baseball_bat");
        helper.assertTrue(
                expectedId.equals(ForgeRegistries.ITEMS.getKey(ModItems.BASEBALL_BAT.get())),
                "Baseball Bat registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.BASEBALL_BAT.get());
        helper.assertTrue(stack.getMaxStackSize() == 1, "Baseball Bat must not stack");
        helper.assertTrue(stack.getMaxDamage() == BaseballBatItem.MAX_DURABILITY,
                "Baseball Bat max durability must be exactly 80");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void mainHandGrantsExactFinalAttackDamageAndSpeed(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.BASEBALL_BAT.get());
        AttributeMap attributes = new AttributeMap(Player.createAttributes().build());
        attributes.addTransientAttributeModifiers(
                stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
        helper.assertTrue(
                Math.abs(attributes.getValue(Attributes.ATTACK_DAMAGE) - BaseballBatItem.FINAL_ATTACK_DAMAGE) < EPSILON,
                "Main-hand final Attack Damage must be exactly 8.0, found "
                        + attributes.getValue(Attributes.ATTACK_DAMAGE));
        helper.assertTrue(
                Math.abs(attributes.getValue(Attributes.ATTACK_SPEED) - BaseballBatItem.FINAL_ATTACK_SPEED) < EPSILON,
                "Main-hand final Attack Speed must be exactly 1.0, found "
                        + attributes.getValue(Attributes.ATTACK_SPEED));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void nonMainHandSlotsGrantNoWeaponAttributes(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.BASEBALL_BAT.get());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND) {
                continue;
            }
            helper.assertTrue(
                    stack.getAttributeModifiers(slot).isEmpty(),
                    "Baseball Bat granted attribute modifiers in slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void mainHandGrantsStrengthBonusMirroredToAttribute(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "baseball-bat-strength", 60.5D);
        try {
            int baseStrength = PlayerStatsService.getFinalValue(player, StatType.STRENGTH);
            helper.assertTrue(baseStrength == 0, "Test setup expected zero baseline Strength");

            setMainHand(player, new ItemStack(ModItems.BASEBALL_BAT.get()));

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH)
                            == baseStrength + BaseballBatItem.STRENGTH_BONUS,
                    "Wielding the Baseball Bat in main hand did not grant +2 Strength");
            AttributeInstance strengthAttribute = player.getAttribute(ModAttributes.STRENGTH.get());
            helper.assertTrue(
                    strengthAttribute != null
                            && strengthAttribute.getValue() == baseStrength + BaseballBatItem.STRENGTH_BONUS,
                    "Mirrored Strength attribute (client-visible value) did not match the final stat");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void offHandGrantsNoStrengthBonusOrWeaponAttributes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "baseball-bat-offhand", 63.5D);
        try {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BASEBALL_BAT.get()));
            MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(
                    player, EquipmentSlot.OFFHAND, ItemStack.EMPTY,
                    new ItemStack(ModItems.BASEBALL_BAT.get())));
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 0,
                    "Baseball Bat in the off hand incorrectly granted the Strength bonus");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void backToInventoryImmediatelyRemovesStrengthBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "baseball-bat-inventory", 66.5D);
        try {
            setMainHand(player, new ItemStack(ModItems.BASEBALL_BAT.get()));
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH)
                            == BaseballBatItem.STRENGTH_BONUS,
                    "Test setup did not grant the Strength bonus while wielded");

            setMainHand(player, ItemStack.EMPTY);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 0,
                    "Moving the Baseball Bat out of the main hand did not immediately remove the Strength bonus");
            AttributeInstance strengthAttribute = player.getAttribute(ModAttributes.STRENGTH.get());
            helper.assertTrue(
                    strengthAttribute != null && strengthAttribute.getValue() == 0,
                    "Mirrored Strength attribute did not immediately follow the hand swap");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void repeatedHandSwapsNeverStackStrengthBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "baseball-bat-swap", 69.5D);
        try {
            ItemStack bat = new ItemStack(ModItems.BASEBALL_BAT.get());
            ItemStack other = new ItemStack(net.minecraft.world.item.Items.STICK);
            for (int i = 0; i < 5; i++) {
                setMainHand(player, bat);
                helper.assertTrue(
                        PlayerStatsService.getFinalValue(player, StatType.STRENGTH)
                                == BaseballBatItem.STRENGTH_BONUS,
                        "Strength bonus incorrectly stacked after repeated hand swaps, iteration " + i);
                setMainHand(player, other);
                helper.assertTrue(
                        PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 0,
                        "Strength bonus was not removed after switching away, iteration " + i);
            }
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void singleMeleeHitConsumesExactlyOneDurability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "baseball-bat-durability", 72.5D);
        Zombie target = zombie(level, attacker, 3.0D);
        try {
            ItemStack stack = new ItemStack(ModItems.BASEBALL_BAT.get());
            setMainHand(attacker, stack);

            BaseballBatItem baseballBatItem = (BaseballBatItem) ModItems.BASEBALL_BAT.get();
            baseballBatItem.hurtEnemy(stack, target, attacker);

            helper.assertTrue(stack.getDamageValue() == 1,
                    "A single successful melee hit must consume exactly 1 durability, found "
                            + stack.getDamageValue());
            helper.succeed();
        } finally {
            target.discard();
            attacker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void breaksAtZeroDurability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "baseball-bat-break", 75.5D);
        Zombie target = zombie(level, attacker, 3.0D);
        try {
            ItemStack stack = new ItemStack(ModItems.BASEBALL_BAT.get());
            setMainHand(attacker, stack);

            BaseballBatItem baseballBatItem = (BaseballBatItem) ModItems.BASEBALL_BAT.get();
            for (int i = 0; i < BaseballBatItem.MAX_DURABILITY - 1; i++) {
                baseballBatItem.hurtEnemy(stack, target, attacker);
            }
            helper.assertTrue(!stack.isEmpty() && stack.getDamageValue() == BaseballBatItem.MAX_DURABILITY - 1,
                    "Baseball Bat should not yet be broken one hit before max durability, found damage "
                            + stack.getDamageValue());

            baseballBatItem.hurtEnemy(stack, target, attacker);
            helper.assertTrue(stack.getCount() == 0,
                    "Baseball Bat must break (be consumed) once durability reaches exactly 80 hits");
            helper.succeed();
        } finally {
            target.discard();
            attacker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void doesNotExtendSwordItemOrGrantToolMiningActions(GameTestHelper helper) {
        helper.assertTrue(
                BaseballBatItem.class.getSuperclass() == net.minecraft.world.item.Item.class,
                "Baseball Bat must extend Item directly, not SwordItem/DiggerItem "
                        + "(those grant unrequested sweep/knockback/mining behavior)");

        ItemStack stack = new ItemStack(ModItems.BASEBALL_BAT.get());
        float destroySpeed = stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
        helper.assertTrue(destroySpeed <= 1.0F,
                "Baseball Bat must not have any special block-destroying speed, found " + destroySpeed);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void modelLangAndTextureResourcesArePresent(GameTestHelper helper) {
        String model = readResourceAsString(
                "/assets/goetyarkham/models/item/baseball_bat.json");
        helper.assertTrue(model.contains("\"parent\": \"minecraft:item/handheld\""),
                "Baseball Bat item model must use the minecraft:item/handheld parent");
        helper.assertTrue(model.contains("\"goetyarkham:item/baseball_bat\""),
                "Baseball Bat item model must reference the goetyarkham:item/baseball_bat texture");

        String zhCn = readResourceAsString("/assets/goetyarkham/lang/zh_cn.json");
        helper.assertTrue(zhCn.contains("\"item.goetyarkham.baseball_bat\": \"球棒\""),
                "zh_cn.json is missing the Baseball Bat item name");
        helper.assertTrue(zhCn.contains("\"tooltip.goetyarkham.baseball_bat.main_hand\""),
                "zh_cn.json is missing the Baseball Bat main-hand tooltip key");

        String enUs = readResourceAsString("/assets/goetyarkham/lang/en_us.json");
        helper.assertTrue(enUs.contains("\"item.goetyarkham.baseball_bat\": \"Baseball Bat\""),
                "en_us.json is missing the Baseball Bat item name");
        helper.assertTrue(enUs.contains("\"tooltip.goetyarkham.baseball_bat.main_hand\""),
                "en_us.json is missing the Baseball Bat main-hand tooltip key");

        helper.assertTrue(
                resourceExistsAndNonEmpty("/assets/goetyarkham/textures/item/baseball_bat.png"),
                "baseball_bat.png texture is missing or empty");
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

    private static String readResourceAsString(String path) {
        try (InputStream stream = BaseballBatGameTests.class.getResourceAsStream(path)) {
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
        try (InputStream stream = BaseballBatGameTests.class.getResourceAsStream(path)) {
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
