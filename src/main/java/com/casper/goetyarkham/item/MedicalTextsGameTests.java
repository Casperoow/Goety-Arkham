package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.chaosbag.ChaosBagApi;
import com.casper.goetyarkham.chaosbag.ChaosBagState;
import com.casper.goetyarkham.chaosbag.ChaosToken;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Known flakiness: {@code player.hurt(...)} occasionally returns {@code
 * false} for a disconnected GameTest stand-in even with every local
 * invulnerability field clean (verified directly, repeatedly, by logging its
 * return value). Every other GameTest for this whole modpack shares the
 * default batch and runs concurrently within it, and this environment
 * bundles 20+ third-party mods; some mod elsewhere in that batch
 * intermittently cancels an unrelated {@code LivingAttackEvent} in a way
 * that is outside this addon's control and reproducible only by timing, not
 * by anything in Medical Texts' own logic (the exact same {@code
 * player.hurt(player.damageSources().magic(), amount)} call already backs
 * {@code ChaosCheckService}'s existing DAMAGE consequence). If a failure
 * outcome test in this class ever reports "did not deal exactly 1 damage",
 * rerun once before assuming a real regression.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MedicalTextsGameTests {
    private MedicalTextsGameTests() {
    }

    @GameTest(template = "empty")
    public static void medicalTextsRegistrationTagsAndTooltip(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "medical_texts");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.MEDICAL_TEXTS.get())),
                "Medical Texts registry ID mismatch");

        MedicalTextsItem item = ModItems.MEDICAL_TEXTS.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Medical Texts must not stack");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(
                        Set.of(CurioSlotIds.HANDS, CurioSlotIds.BOOK)),
                "Medical Texts item tag must expose exactly hands and book");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 3,
                "Medical Texts tooltip line count mismatch");
        helper.assertTrue("Slot: Hands, Book".equals(tooltip.get(0).getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(0).getStyle().getColor()),
                "Medical Texts slot line mismatch");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "Medical Texts when-worn heading is not yellow");

        TranslatableContents effectContents =
                (TranslatableContents) tooltip.get(2).getContents();
        helper.assertTrue(
                "tooltip.goetyarkham.medical_texts.effect".equals(effectContents.getKey()),
                "Medical Texts effect line does not use its own translation key");
        Object[] args = effectContents.getArgs();
        helper.assertTrue(args.length == 1 && args[0] instanceof Component,
                "Medical Texts effect line must carry exactly one Component argument");
        Component keybindArgument = (Component) args[0];
        helper.assertTrue(
                keybindArgument.getContents() instanceof KeybindContents keybind
                        && MedicalTextsItem.ABILITY_KEY_TRANSLATION_KEY.equals(keybind.getName()),
                "Medical Texts tooltip does not dynamically reference the ability keybind");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void medicalTextsApplyOutcomeGrantsHealOrDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "medical-texts-outcome");
        try {
            player.setHealth(player.getMaxHealth() - 5.0F);
            MedicalTextsAbilityService.applyOutcome(player, true);
            // Instant Health only actually heals on the entity's next tick
            // (MobEffectInstance#tick -> MobEffect#applyEffectTick); a
            // GameTest stand-in is never ticked, so this checks the effect
            // itself was granted rather than waiting for a heal that would
            // only ever land during real, ticked gameplay.
            MobEffectInstance healEffect = player.getEffect(MobEffects.HEAL);
            helper.assertTrue(healEffect != null && healEffect.getAmplifier() == 0,
                    "A successful outcome did not grant Instant Health I");

            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            MedicalTextsAbilityService.applyOutcome(player, false);
            helper.assertTrue(
                    Math.abs(player.getHealth()
                            - (player.getMaxHealth() - MedicalTextsAbilityService.FAILURE_DAMAGE))
                            < 0.001F,
                    "A failed outcome did not deal exactly 1 damage");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty")
    public static void medicalTextsAbilityRequiresActualCurioEquipment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "medical-texts-not-equipped");
        try {
            player.setHealth(player.getMaxHealth());
            float baseline = player.getHealth();

            // Never placed anywhere: a press must be a complete no-op.
            MedicalTextsAbilityService.tryUse(player);
            helper.assertTrue(player.getHealth() == baseline
                            && !player.getCooldowns().isOnCooldown(ModItems.MEDICAL_TEXTS.get()),
                    "An unequipped Medical Texts triggered the ability");

            // Main inventory only (not a Curios slot) must not count as equipped.
            player.getInventory().add(new ItemStack(ModItems.MEDICAL_TEXTS.get()));
            MedicalTextsAbilityService.tryUse(player);
            helper.assertTrue(player.getHealth() == baseline
                            && !player.getCooldowns().isOnCooldown(ModItems.MEDICAL_TEXTS.get()),
                    "Medical Texts sitting in the main inventory triggered the ability");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    /**
     * The chaos bag ({@link ChaosBagApi}) is one global, server-wide
     * instance. GameTest runs tests within a single (default) batch
     * concurrently for speed, so any test that temporarily forces the
     * effective bag to a single guaranteed token must share one batch with
     * every other test doing the same, or their mutations can interleave.
     */
    private static final String CHAOS_BAG_TEST_BATCH = "goetyarkham:medical_texts_chaos_bag";

    @GameTest(template = "empty", batch = CHAOS_BAG_TEST_BATCH)
    public static void medicalTextsAbilitySucceedsInHandsAndBookTriggersOnceForTwoCopies(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        TestPlayer player = testPlayer(level, "medical-texts-succeed");
        ResourceLocation forcedSource = null;
        try {
            ensureBookSlot(player, helper);
            ICurioStacksHandler hands = handler(player, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler book = handler(player, CurioSlotIds.BOOK, helper);

            hands.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.MEDICAL_TEXTS.get()));

            forcedSource = forceEffectiveBag(server, ChaosToken.ELDER_SIGN, "success");
            player.setHealth(player.getMaxHealth() - 5.0F);
            MedicalTextsAbilityService.tryUse(player);
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.MEDICAL_TEXTS.get()),
                    "A successful use did not start the cooldown");
            MobEffectInstance healEffect = player.getEffect(MobEffects.HEAL);
            helper.assertTrue(healEffect != null && healEffect.getAmplifier() == 0,
                    "Wearing Medical Texts in hands with a guaranteed-success draw"
                            + " did not grant Instant Health I");
            ChaosBagApi.undoSource(server, forcedSource);
            forcedSource = null;
            expireCooldown(player);

            // Equip a second physical copy into book as well, so both slots
            // are simultaneously occupied by separate stacks.
            book.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.MEDICAL_TEXTS.get()));

            forcedSource = forceEffectiveBag(server, ChaosToken.AUTO_FAIL, "failure");
            player.setHealth(player.getMaxHealth());
            float beforeDamage = player.getHealth();
            MedicalTextsAbilityService.tryUse(player);
            helper.assertTrue(
                    Math.abs(player.getHealth()
                            - (beforeDamage - MedicalTextsAbilityService.FAILURE_DAMAGE))
                            < 0.001F,
                    "Wearing two copies (hands and book) with a guaranteed-failure draw"
                            + " did not deal exactly one check's worth of damage"
                            + " (a single key press must never run more than one check)");

            helper.succeed();
        } finally {
            if (forcedSource != null) {
                ChaosBagApi.undoSource(server, forcedSource);
            }
            discard(player);
        }
    }

    @GameTest(template = "empty", batch = CHAOS_BAG_TEST_BATCH)
    public static void medicalTextsCooldownBlocksRepeatsAndSurvivesSlotMoves(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        TestPlayer player = testPlayer(level, "medical-texts-cooldown");
        ResourceLocation forcedSource = null;
        try {
            ensureBookSlot(player, helper);
            ICurioStacksHandler hands = handler(player, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler book = handler(player, CurioSlotIds.BOOK, helper);
            ItemStack stack = new ItemStack(ModItems.MEDICAL_TEXTS.get());
            hands.getStacks().setStackInSlot(0, stack);

            forcedSource = forceEffectiveBag(server, ChaosToken.AUTO_FAIL, "cooldown");
            player.setHealth(player.getMaxHealth());

            MedicalTextsAbilityService.tryUse(player);
            float afterFirstUse = player.getHealth();
            helper.assertTrue(
                    Math.abs(afterFirstUse
                            - (player.getMaxHealth() - MedicalTextsAbilityService.FAILURE_DAMAGE))
                            < 0.001F,
                    "First use did not deal damage");
            helper.assertTrue(
                    player.getCooldowns().isOnCooldown(ModItems.MEDICAL_TEXTS.get()),
                    "Failure did not start the cooldown");

            // Partially decay the cooldown, then repeat-press: this simulates
            // both a spammed/duplicated client packet and a stale cooldown
            // check being re-validated server-side.
            player.getCooldowns().tick();
            player.getCooldowns().tick();
            float percentAfterPartialDecay = player.getCooldowns()
                    .getCooldownPercent(ModItems.MEDICAL_TEXTS.get(), 0.0F);

            MedicalTextsAbilityService.tryUse(player);
            helper.assertTrue(player.getHealth() == afterFirstUse,
                    "A repeated press while on cooldown ran a second check");
            helper.assertTrue(
                    Math.abs(player.getCooldowns().getCooldownPercent(
                            ModItems.MEDICAL_TEXTS.get(), 0.0F) - percentAfterPartialDecay)
                            < 0.001F,
                    "A blocked repeat press refreshed the cooldown timer");

            // Moving the stack from hands to book must not clear or bypass
            // the still-active cooldown.
            hands.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            book.getStacks().setStackInSlot(0, stack);
            MedicalTextsAbilityService.tryUse(player);
            helper.assertTrue(player.getHealth() == afterFirstUse,
                    "Moving the equipped copy from hands to book bypassed the cooldown");

            // Only once the cooldown fully expires does another press work again.
            expireCooldown(player);
            // Vanilla's own post-hit invulnerability window (Entity#invulnerableTime,
            // 20 ticks) only ever decays inside a real per-tick entity tick,
            // which a GameTest stand-in never receives. It is always far
            // shorter than this ability's own 100-tick cooldown, so in real,
            // ticked gameplay it is guaranteed to have already lapsed by now;
            // this mirrors that natural passage of time rather than
            // bypassing anything the ability itself is responsible for.
            player.invulnerableTime = 0;
            MedicalTextsAbilityService.tryUse(player);
            helper.assertTrue(
                    Math.abs(player.getHealth()
                            - (afterFirstUse - MedicalTextsAbilityService.FAILURE_DAMAGE))
                            < 0.001F,
                    "A press after the cooldown expired did not run a new check");

            helper.succeed();
        } finally {
            if (forcedSource != null) {
                ChaosBagApi.undoSource(server, forcedSource);
            }
            discard(player);
        }
    }

    /**
     * Removes every effective token, then adds only {@code guaranteedToken}
     * (tagged under a unique test source), so the very next chaos draw is
     * deterministic regardless of the real random source. The original
     * global chaos bag is restored via {@link ChaosBagApi#undoSource} once
     * the caller is done, exactly like {@code ChaosBagCommand}'s own
     * token-mutation commands.
     */
    private static ResourceLocation forceEffectiveBag(
            MinecraftServer server, ChaosToken guaranteedToken, String label) {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "medical_texts_test_" + label + "_" + UUID.randomUUID());
        Map<ChaosToken, Integer> originalCounts = new HashMap<>();
        for (ChaosToken token : ChaosBagApi.getEffectiveConfiguration(server)) {
            originalCounts.merge(token, 1, Integer::sum);
        }
        // Added before anything is removed, so the bag is never left
        // effectively empty mid-mutation (which the underlying state
        // rejects).
        requireSuccess(ChaosBagApi.addTokens(server, guaranteedToken, 10, source), "add");
        originalCounts.forEach((token, count) -> requireSuccess(
                ChaosBagApi.removeTokens(server, token, count, source), "remove"));
        return source;
    }

    private static void requireSuccess(ChaosBagState.OperationResult result, String label) {
        if (!result.success()) {
            throw new AssertionError(
                    "Chaos bag test mutation failed (" + label + "): " + result.message());
        }
    }

    private static void expireCooldown(ServerPlayer player) {
        for (int tick = 0; tick < MedicalTextsAbilityService.COOLDOWN_TICKS; tick++) {
            player.getCooldowns().tick();
        }
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

    /**
     * The {@code book} slot's base size is 0 (see {@link CurioSlotIds}); it
     * only ever gains capacity from another item's dynamic slot modifier.
     * Tests grant one slot directly, the same way {@code WendysAmuletService}
     * grants its weakness slot, rather than depending on some other item.
     */
    private static void ensureBookSlot(ServerPlayer player, GameTestHelper helper) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player)
                .resolve()
                .orElse(null);
        helper.assertTrue(inventory != null, "Missing Curios inventory");
        inventory.addPermanentSlotModifier(
                CurioSlotIds.BOOK,
                UUID.randomUUID(),
                "goetyarkham:medical_texts_test_book_slot",
                1.0D,
                AttributeModifier.Operation.ADDITION);
        // getSlots() applies a pending Curios resize synchronously.
        inventory.getStacksHandler(CurioSlotIds.BOOK)
                .ifPresent(ICurioStacksHandler::getSlots);
    }

    /**
     * Deliberately never registered in {@code level.players()}. Nothing in
     * {@code MedicalTextsAbilityService} reads that list (unlike, e.g.,
     * Rita Chandler's Token's aura), and a connectionless test player left in
     * it is live ammunition for a server crash: {@code ChunkMap} broadcasts
     * every newly tracked nearby entity (including an ordinary block-drop
     * item) to every entry in {@code level.players()} via
     * {@code connection.send(...)}, which NPEs the instant that connection
     * is null.
     */
    private static TestPlayer testPlayer(ServerLevel level, String name) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(0.0D, 1.0D, 0.0D);
        return player;
    }

    private static void discard(TestPlayer player) {
        player.discard();
    }

    /**
     * A raw {@code ServerPlayer} built outside the normal login flow has no
     * network connection, so anything that tries to sync to a client
     * (chat messages, effect add/remove packets) must be short-circuited
     * here - the same pattern {@code RitaChandlersTokenGameTests} already
     * uses. Cooldowns need the same treatment: vanilla's
     * {@code ServerPlayer} normally backs {@link #getCooldowns()} with a
     * connection-syncing {@code ServerItemCooldowns}, so this overrides
     * {@link #createItemCooldowns()} to use the plain, non-syncing base
     * class instead, while still exercising the exact same
     * {@code isOnCooldown}/{@code addCooldown} logic
     * {@code MedicalTextsAbilityService} depends on.
     */
    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
            clearSpawnInvulnerability(this);
        }

        @Override
        protected ItemCooldowns createItemCooldowns() {
            return new ItemCooldowns();
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

        /**
         * Vanilla gives every freshly constructed {@code ServerPlayer} 60
         * ticks of spawn invulnerability, normally worn down by the regular
         * per-tick server loop a real, connected player goes through. A
         * GameTest stand-in is never ticked that way, so without this it
         * would stay permanently immune to {@link #hurt}, silently breaking
         * any test asserting the ability's failure damage actually lands.
         */
        private static void clearSpawnInvulnerability(ServerPlayer player) {
            try {
                Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                field.setAccessible(true);
                field.setInt(player, 0);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException(exception);
            }
        }
    }
}
