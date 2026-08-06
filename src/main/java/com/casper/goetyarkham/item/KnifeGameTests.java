package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.entity.ThrownKnifeEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/**
 * Covers {@link KnifeItem} itself: registration, stack/durability limits,
 * main-hand-only attribute modifiers, and the SPEAR-charge throw funnel
 * ({@link KnifeItem#use} / {@link KnifeItem#releaseUsing}) up to the point a
 * {@link ThrownKnifeEntity} is spawned. In-flight/impact behavior has its
 * own dedicated coverage in {@code ThrownKnifeGameTests} (same package as
 * the entity, so it can exercise the protected {@code onHitEntity}/{@code
 * onHitBlock} hooks directly).
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnifeGameTests {
    private static final String BATCH = "goetyarkham:knife";

    private KnifeGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void registrationDurabilityAndStackSizeAreExact(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "knife");
        helper.assertTrue(
                expectedId.equals(ForgeRegistries.ITEMS.getKey(ModItems.KNIFE.get())),
                "Knife registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.KNIFE.get());
        helper.assertTrue(stack.getMaxStackSize() == 1, "Knife must not stack");
        helper.assertTrue(stack.getMaxDamage() == KnifeItem.MAX_DURABILITY,
                "Knife max durability must be exactly 250");
        helper.assertTrue(
                stack.getItem().getUseAnimation(stack) == UseAnim.SPEAR,
                "Knife must use the spear (trident-style) charge animation");
        helper.succeed();
    }

    /**
     * Uses a fresh {@link AttributeMap} built straight from {@link
     * Player#createAttributes()} - vanilla's own canonical default attack
     * attributes - rather than a live {@code TestPlayer}'s attribute
     * instances, which in this modpack's GameTest environment can already
     * carry other mods' base-value adjustments unrelated to this feature.
     * This keeps the assertion tied only to {@link KnifeItem}'s own math.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void mainHandGrantsExactFinalAttackDamageAndSpeed(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.KNIFE.get());
        AttributeMap attributes = new AttributeMap(Player.createAttributes().build());
        attributes.addTransientAttributeModifiers(
                stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
        helper.assertTrue(
                attributes.getValue(Attributes.ATTACK_DAMAGE) == KnifeItem.FINAL_ATTACK_DAMAGE,
                "Main-hand final Attack Damage must be exactly 3.5, found "
                        + attributes.getValue(Attributes.ATTACK_DAMAGE));
        helper.assertTrue(
                attributes.getValue(Attributes.ATTACK_SPEED) == KnifeItem.FINAL_ATTACK_SPEED,
                "Main-hand final Attack Speed must be exactly 2.0, found "
                        + attributes.getValue(Attributes.ATTACK_SPEED));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void nonMainHandSlotsGrantNoWeaponAttributes(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.KNIFE.get());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND) {
                continue;
            }
            helper.assertTrue(
                    stack.getAttributeModifiers(slot).isEmpty(),
                    "Knife granted attribute modifiers in slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void offHandUseNeverStartsCharging(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "knife-offhand-use", 30.5D);
        try {
            ItemStack stack = new ItemStack(ModItems.KNIFE.get());
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
            KnifeItem knifeItem = (KnifeItem) ModItems.KNIFE.get();
            InteractionResultHolder<ItemStack> result =
                    knifeItem.use(level, player, InteractionHand.OFF_HAND);
            helper.assertTrue(
                    result.getResult() != InteractionResult.CONSUME,
                    "Off-hand use() must not start a charge");
            helper.assertTrue(!player.isUsingItem(),
                    "Off-hand use() must never put the player into the using-item state");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void chargeBelowThresholdDoesNotThrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "knife-undercharged", 60.5D);
        try {
            ItemStack stack = new ItemStack(ModItems.KNIFE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            KnifeItem knifeItem = (KnifeItem) ModItems.KNIFE.get();
            int useDuration = knifeItem.getUseDuration(stack);
            // 9 charged ticks: one short of the 10-tick threshold.
            int timeLeft = useDuration - 9;
            int knivesBefore = countThrownKnives(level, player);

            knifeItem.releaseUsing(stack, level, player, timeLeft);

            helper.assertTrue(!player.getMainHandItem().isEmpty(),
                    "An under-charged release must leave the knife in hand");
            helper.assertTrue(countThrownKnives(level, player) == knivesBefore,
                    "An under-charged release must not spawn a thrown knife");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void fullChargeThrowsAndRemovesFromSurvivalMainHand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "knife-full-charge", 90.5D);
        try {
            ItemStack stack = new ItemStack(ModItems.KNIFE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.getAbilities().instabuild = false;
            KnifeItem knifeItem = (KnifeItem) ModItems.KNIFE.get();
            int useDuration = knifeItem.getUseDuration(stack);
            int timeLeft = useDuration - KnifeItem.THROW_CHARGE_THRESHOLD_TICKS;
            int knivesBefore = countThrownKnives(level, player);

            knifeItem.releaseUsing(stack, level, player, timeLeft);

            helper.assertTrue(player.getMainHandItem().isEmpty(),
                    "A fully-charged survival release must remove the knife from the main hand");
            helper.assertTrue(countThrownKnives(level, player) == knivesBefore + 1,
                    "A fully-charged release must spawn exactly one thrown knife");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void creativeThrowKeepsOriginalKnifeAndDoesNotDamageIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "knife-creative", 120.5D);
        try {
            ItemStack stack = new ItemStack(ModItems.KNIFE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.getAbilities().instabuild = true;
            KnifeItem knifeItem = (KnifeItem) ModItems.KNIFE.get();
            int useDuration = knifeItem.getUseDuration(stack);
            int timeLeft = useDuration - KnifeItem.THROW_CHARGE_THRESHOLD_TICKS;
            int knivesBefore = countThrownKnives(level, player);

            knifeItem.releaseUsing(stack, level, player, timeLeft);

            helper.assertTrue(!player.getMainHandItem().isEmpty()
                            && player.getMainHandItem().getCount() == 1
                            && player.getMainHandItem().getDamageValue() == 0,
                    "A creative-mode release must keep the original, undamaged knife in hand");
            helper.assertTrue(countThrownKnives(level, player) == knivesBefore + 1,
                    "A creative-mode release must still spawn a thrown knife to attack with");
            List<ThrownKnifeEntity> spawned = level.getEntitiesOfClass(
                    ThrownKnifeEntity.class, player.getBoundingBox().inflate(6.0D));
            helper.assertTrue(
                    spawned.stream().anyMatch(k -> knifeCreativeFlag(k)),
                    "The spawned thrown knife must be flagged as a creative throw");
            helper.succeed();
        } finally {
            player.getAbilities().instabuild = false;
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void thrownKnifePreservesDamageEnchantmentsAndCustomName(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "knife-preserve", 150.5D);
        try {
            ItemStack stack = new ItemStack(ModItems.KNIFE.get());
            stack.setDamageValue(37);
            stack.enchant(Enchantments.UNBREAKING, 2);
            stack.setHoverName(Component.literal("Test Knife"));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            KnifeItem knifeItem = (KnifeItem) ModItems.KNIFE.get();
            int useDuration = knifeItem.getUseDuration(stack);
            int timeLeft = useDuration - KnifeItem.THROW_CHARGE_THRESHOLD_TICKS;

            knifeItem.releaseUsing(stack, level, player, timeLeft);

            ThrownKnifeEntity thrown = level.getEntitiesOfClass(
                            ThrownKnifeEntity.class, player.getBoundingBox().inflate(6.0D))
                    .stream().findFirst().orElse(null);
            helper.assertTrue(thrown != null, "No thrown knife entity found after release");
            ItemStack carried = thrown.getKnifeItem();
            helper.assertTrue(carried.getDamageValue() == 37,
                    "Thrown knife lost its pre-throw durability damage");
            helper.assertTrue(carried.getEnchantmentLevel(Enchantments.UNBREAKING) == 2,
                    "Thrown knife lost its Unbreaking enchantment");
            helper.assertTrue(
                    carried.getHoverName().getString().equals("Test Knife"),
                    "Thrown knife lost its custom name");

            // Round-trip through NBT (stand-in for the entity's own save/load).
            ItemStack roundTripped = ItemStack.of(carried.save(new CompoundTag()));
            helper.assertTrue(roundTripped.getDamageValue() == 37
                            && roundTripped.getEnchantmentLevel(Enchantments.UNBREAKING) == 2
                            && roundTripped.getHoverName().getString().equals("Test Knife"),
                    "Thrown knife's carried stack did not survive an NBT round trip");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void powerArrowsAppliesAtEnchantingTableAndBoostsOnlyThrownDamage(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "knife-power", 180.5D);
        try {
            ItemStack stack = new ItemStack(ModItems.KNIFE.get());
            KnifeItem knifeItem = (KnifeItem) ModItems.KNIFE.get();
            helper.assertTrue(
                    knifeItem.canApplyAtEnchantingTable(stack, Enchantments.POWER_ARROWS),
                    "Power must be legal to apply to a Knife at the enchanting table");

            // Sharpness (a melee-only enchantment by vanilla convention) has
            // no bearing on this item's own thrown-damage formula, and Power
            // never touches the main-hand melee attribute modifiers - the
            // two are computed by entirely separate code paths.
            ItemStack plainStack = stack.copy();
            ThrownKnifeEntity plainThrow = new ThrownKnifeEntity(level, player, plainStack);
            helper.assertTrue(plainThrow.getBaseDamage() == KnifeItem.THROWN_BASE_DAMAGE,
                    "An unenchanted knife's thrown base damage must be exactly 3.5");
            plainThrow.discard();

            ItemStack poweredStack = stack.copy();
            poweredStack.enchant(Enchantments.POWER_ARROWS, 2);
            ThrownKnifeEntity poweredThrow = new ThrownKnifeEntity(level, player, poweredStack);
            double expected = KnifeItem.THROWN_BASE_DAMAGE + (2 * 0.5D + 0.5D);
            helper.assertTrue(poweredThrow.getBaseDamage() == expected,
                    "Power II must add exactly 1.5 to the thrown base damage (0.5*level+0.5),"
                            + " found " + poweredThrow.getBaseDamage());
            poweredThrow.discard();
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    private static int countThrownKnives(ServerLevel level, ServerPlayer near) {
        return level.getEntitiesOfClass(
                ThrownKnifeEntity.class, near.getBoundingBox().inflate(8.0D)).size();
    }

    private static boolean knifeCreativeFlag(ThrownKnifeEntity entity) {
        CompoundTag tag = new CompoundTag();
        entity.addAdditionalSaveData(tag);
        return tag.getBoolean("CreativeThrow");
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
