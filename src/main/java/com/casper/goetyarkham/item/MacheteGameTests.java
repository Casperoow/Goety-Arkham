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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Covers {@link MacheteItem}: registration, durability/stack limits,
 * main-hand-only Attack Damage/Speed, the live-computed Strength bonus
 * ({@link MacheteStrengthBonusService}), the Enemy-only bonus damage
 * ({@link MacheteMonsterDamageEvents}), sweeping-edge exclusion, other
 * sword enchantment compatibility, the swords item tag, cobweb drops, and
 * the required resource files.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MacheteGameTests {
    private static final String BATCH = "goetyarkham:machete";
    private static final float EPSILON = 1.0e-4F;

    private MacheteGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void registrationDurabilityAndStackSizeAreExact(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "machete");
        helper.assertTrue(
                expectedId.equals(ForgeRegistries.ITEMS.getKey(ModItems.MACHETE.get())),
                "Machete registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        helper.assertTrue(stack.getMaxStackSize() == 1, "Machete must not stack");
        helper.assertTrue(stack.getMaxDamage() == MacheteItem.MAX_DURABILITY,
                "Machete max durability must be exactly 250");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void mainHandGrantsExactFinalAttackDamageAndSpeed(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        AttributeMap attributes = new AttributeMap(Player.createAttributes().build());
        attributes.addTransientAttributeModifiers(
                stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
        helper.assertTrue(
                Math.abs(attributes.getValue(Attributes.ATTACK_DAMAGE) - MacheteItem.FINAL_ATTACK_DAMAGE) < EPSILON,
                "Main-hand final Attack Damage must be exactly 5.0, found "
                        + attributes.getValue(Attributes.ATTACK_DAMAGE));
        helper.assertTrue(
                Math.abs(attributes.getValue(Attributes.ATTACK_SPEED) - MacheteItem.FINAL_ATTACK_SPEED) < EPSILON,
                "Main-hand final Attack Speed must be exactly 1.4, found "
                        + attributes.getValue(Attributes.ATTACK_SPEED));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void nonMainHandSlotsGrantNoWeaponAttributes(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND) {
                continue;
            }
            helper.assertTrue(
                    stack.getAttributeModifiers(slot).isEmpty(),
                    "Machete granted attribute modifiers in slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void mainHandGrantsStrengthBonusMirroredToAttribute(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "machete-strength", 30.5D);
        try {
            int baseStrength = PlayerStatsService.getFinalValue(player, StatType.STRENGTH);
            helper.assertTrue(baseStrength == 0, "Test setup expected zero baseline Strength");

            setMainHand(player, new ItemStack(ModItems.MACHETE.get()));

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH)
                            == baseStrength + MacheteItem.STRENGTH_BONUS,
                    "Wielding the Machete in main hand did not grant +1 Strength");
            AttributeInstance strengthAttribute = player.getAttribute(ModAttributes.STRENGTH.get());
            helper.assertTrue(
                    strengthAttribute != null
                            && strengthAttribute.getValue() == baseStrength + MacheteItem.STRENGTH_BONUS,
                    "Mirrored Strength attribute (client-visible value) did not match the final stat");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void offHandGrantsNoStrengthBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "machete-offhand", 33.5D);
        try {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.MACHETE.get()));
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 0,
                    "Machete in the off hand incorrectly granted the Strength bonus");
            helper.succeed();
        } finally {
            PlayerStatsService.reset(player);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void switchingAwayFromMainHandRestoresStrengthImmediately(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "machete-switch-away", 36.5D);
        try {
            setMainHand(player, new ItemStack(ModItems.MACHETE.get()));
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH)
                            == MacheteItem.STRENGTH_BONUS,
                    "Test setup did not grant the Strength bonus while wielded");

            setMainHand(player, ItemStack.EMPTY);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.STRENGTH) == 0,
                    "Switching away from the Machete did not immediately remove the Strength bonus");
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
    public static void attackingEnemyGrantsTwoBonusDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "machete-enemy-bonus", 39.5D);
        Zombie target = zombie(level, attacker, 3.0D);
        try {
            setMainHand(attacker, new ItemStack(ModItems.MACHETE.get()));

            float baseAmount = 5.0F;
            LivingHurtEvent event = new LivingHurtEvent(
                    target, attacker.damageSources().playerAttack(attacker), baseAmount);
            MinecraftForge.EVENT_BUS.post(event);

            helper.assertTrue(
                    Math.abs(event.getAmount() - (baseAmount + MacheteItem.MONSTER_BONUS_DAMAGE)) < EPSILON,
                    "Attacking an Enemy with the Machete did not add exactly 2 bonus damage, found "
                            + event.getAmount());
            helper.succeed();
        } finally {
            target.discard();
            attacker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void attackingNonEnemyGrantsNoBonusDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "machete-nonenemy-bonus", 42.5D);
        Pig target = pig(level, attacker, 3.0D);
        try {
            setMainHand(attacker, new ItemStack(ModItems.MACHETE.get()));

            float baseAmount = 5.0F;
            LivingHurtEvent event = new LivingHurtEvent(
                    target, attacker.damageSources().playerAttack(attacker), baseAmount);
            MinecraftForge.EVENT_BUS.post(event);

            helper.assertTrue(
                    Math.abs(event.getAmount() - baseAmount) < EPSILON,
                    "Attacking a non-Enemy target incorrectly granted the +2 Machete bonus, found "
                            + event.getAmount());
            helper.succeed();
        } finally {
            target.discard();
            attacker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void indirectProjectileDamageIgnoresBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "machete-projectile-bonus", 45.5D);
        Zombie target = zombie(level, attacker, 3.0D);
        try {
            setMainHand(attacker, new ItemStack(ModItems.MACHETE.get()));

            AbstractArrow arrow = EntityType.ARROW.create(level);
            helper.assertTrue(arrow != null, "Could not create test arrow");
            arrow.setOwner(attacker);
            DamageSource arrowSource = attacker.damageSources().arrow(arrow, attacker);

            float baseAmount = 3.0F;
            LivingHurtEvent event = new LivingHurtEvent(target, arrowSource, baseAmount);
            MinecraftForge.EVENT_BUS.post(event);

            helper.assertTrue(
                    Math.abs(event.getAmount() - baseAmount) < EPSILON,
                    "An arrow fired by a Machete-wielding player incorrectly received the +2 bonus, found "
                            + event.getAmount());
            helper.succeed();
        } finally {
            target.discard();
            attacker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void singleMeleeHitConsumesExactlyOneDurability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "machete-durability", 48.5D);
        Zombie target = zombie(level, attacker, 3.0D);
        try {
            ItemStack stack = new ItemStack(ModItems.MACHETE.get());
            setMainHand(attacker, stack);

            SwordItem macheteItem = (SwordItem) ModItems.MACHETE.get();
            macheteItem.hurtEnemy(stack, target, attacker);

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
    public static void extraDamageDoesNotConsumeExtraDurability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer attacker = testPlayer(level, "machete-durability-bonus", 51.5D);
        Zombie target = zombie(level, attacker, 3.0D);
        try {
            ItemStack stack = new ItemStack(ModItems.MACHETE.get());
            setMainHand(attacker, stack);

            LivingHurtEvent event = new LivingHurtEvent(
                    target, attacker.damageSources().playerAttack(attacker), 5.0F);
            MinecraftForge.EVENT_BUS.post(event);

            helper.assertTrue(stack.getDamageValue() == 0,
                    "Merging the +2 bonus into a LivingHurtEvent must not itself consume durability, found "
                            + stack.getDamageValue());
            helper.succeed();
        } finally {
            target.discard();
            attacker.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void hasVanillaSweepingAttackWithoutSweepingEdgeEnchant(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        helper.assertTrue(ModItems.MACHETE.get() instanceof SwordItem,
                "Machete must extend SwordItem to reuse the vanilla sweeping attack implementation");
        helper.assertTrue(
                net.minecraft.world.item.enchantment.EnchantmentHelper
                        .getEnchantments(stack).isEmpty(),
                "A fresh Machete must have no default enchantments, including Sweeping Edge");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void sweepingEdgeCannotBeAppliedAtEnchantingTableOrAnvil(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        MacheteItem macheteItem = (MacheteItem) ModItems.MACHETE.get();
        helper.assertTrue(
                !macheteItem.canApplyAtEnchantingTable(stack, Enchantments.SWEEPING_EDGE),
                "Machete must reject Sweeping Edge at the enchanting table / anvil");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void otherSwordEnchantmentsRemainCompatible(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        MacheteItem macheteItem = (MacheteItem) ModItems.MACHETE.get();
        List<net.minecraft.world.item.enchantment.Enchantment> expectedCompatible = List.of(
                Enchantments.SHARPNESS,
                Enchantments.SMITE,
                Enchantments.BANE_OF_ARTHROPODS,
                Enchantments.KNOCKBACK,
                Enchantments.FIRE_ASPECT,
                Enchantments.MOB_LOOTING,
                Enchantments.UNBREAKING,
                Enchantments.MENDING,
                Enchantments.VANISHING_CURSE
        );
        for (net.minecraft.world.item.enchantment.Enchantment enchantment : expectedCompatible) {
            helper.assertTrue(
                    macheteItem.canApplyAtEnchantingTable(stack, enchantment),
                    "Machete must remain compatible with " + enchantment);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void isInSwordsTag(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        helper.assertTrue(stack.is(ItemTags.SWORDS),
                "Machete must be present in the minecraft:swords item tag");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void breakingCobwebDropsOnlyString(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(0, 4, 0);

        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                Blocks.COBWEB.defaultBlockState(), level, pos, null, null, stack);

        boolean droppedString = drops.stream().anyMatch(drop -> drop.is(net.minecraft.world.item.Items.STRING));
        boolean droppedCobweb = drops.stream().anyMatch(drop -> drop.is(Blocks.COBWEB.asItem()));
        helper.assertTrue(droppedString, "Breaking a cobweb with the Machete must drop String");
        helper.assertTrue(!droppedCobweb, "Breaking a cobweb with the Machete must not drop the Cobweb block itself");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void hasVanillaSwordCobwebDestroySpeed(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.MACHETE.get());
        float destroySpeed = stack.getDestroySpeed(Blocks.COBWEB.defaultBlockState());
        helper.assertTrue(destroySpeed > 1.0F,
                "Machete must destroy cobwebs faster than a bare hand/generic tool, found " + destroySpeed);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void modelLangAndTextureResourcesArePresent(GameTestHelper helper) {
        String model = readResourceAsString(
                "/assets/goetyarkham/models/item/machete.json");
        helper.assertTrue(model.contains("\"parent\": \"minecraft:item/handheld\""),
                "Machete item model must use the minecraft:item/handheld parent");
        helper.assertTrue(model.contains("\"goetyarkham:item/machete\""),
                "Machete item model must reference the goetyarkham:item/machete texture");

        String zhCn = readResourceAsString("/assets/goetyarkham/lang/zh_cn.json");
        helper.assertTrue(zhCn.contains("\"item.goetyarkham.machete\": \"大砍刀\""),
                "zh_cn.json is missing the Machete item name");
        helper.assertTrue(zhCn.contains("\"tooltip.goetyarkham.machete.main_hand\""),
                "zh_cn.json is missing the Machete main-hand tooltip key");

        String enUs = readResourceAsString("/assets/goetyarkham/lang/en_us.json");
        helper.assertTrue(enUs.contains("\"item.goetyarkham.machete\": \"Machete\""),
                "en_us.json is missing the Machete item name");
        helper.assertTrue(enUs.contains("\"tooltip.goetyarkham.machete.main_hand\""),
                "en_us.json is missing the Machete main-hand tooltip key");

        String swordsTag = readResourceAsString("/data/minecraft/tags/items/swords.json");
        helper.assertTrue(swordsTag.contains("\"goetyarkham:machete\""),
                "minecraft:tags/items/swords.json is missing the Machete entry");

        helper.assertTrue(
                resourceExistsAndNonEmpty("/assets/goetyarkham/textures/item/machete.png"),
                "machete.png texture is missing or empty");
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
        try (InputStream stream = MacheteGameTests.class.getResourceAsStream(path)) {
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
        try (InputStream stream = MacheteGameTests.class.getResourceAsStream(path)) {
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
