package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.chaosbag.ChaosBagApi;
import com.casper.goetyarkham.chaosbag.ChaosBagState;
import com.casper.goetyarkham.chaosbag.ChaosBaseValueSource;
import com.casper.goetyarkham.chaosbag.ChaosCheckModifier;
import com.casper.goetyarkham.chaosbag.ChaosCheckRequest;
import com.casper.goetyarkham.chaosbag.ChaosCheckResult;
import com.casper.goetyarkham.chaosbag.ChaosCheckService;
import com.casper.goetyarkham.chaosbag.ChaosToken;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LockpicksGameTests {
    /**
     * Shared with every test in this class that forces the global chaos bag
     * to a guaranteed draw, mirroring {@code MedicalTextsGameTests}: the
     * chaos bag is one global, server-wide instance, and GameTest runs every
     * test sharing the default batch concurrently, so bag-mutating tests
     * must share one batch to avoid interleaving with each other.
     */
    private static final String CHAOS_BAG_TEST_BATCH = "goetyarkham:lockpicks_chaos_bag";
    private static final ResourceLocation CHECK_SOURCE =
            ResourceLocation.fromNamespaceAndPath(GoetyArkham.MOD_ID, "lockpicks_test");

    private LockpicksGameTests() {
    }

    @GameTest(template = "empty")
    public static void lockpicksRegistrationTagsAndTooltip(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "lockpicks");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.LOCKPICKS.get())),
                "Lockpicks registry ID mismatch");

        LockpicksItem item = ModItems.LOCKPICKS.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Lockpicks must not stack");
        helper.assertTrue(stack.getMaxDamage() == LockpicksItem.MAX_DURABILITY
                        && LockpicksItem.MAX_DURABILITY == 3,
                "Lockpicks max durability must be 3");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(CurioSlotIds.HANDS)),
                "Lockpicks item tag must expose only the hands slot");

        // A dedicated GameTest server never runs on Dist.CLIENT, so
        // ShiftTooltipHelper.isShiftDown() always reports false here: this
        // exercises the non-Shift branch, ending in the hold-shift prompt
        // rather than the durability-consumption detail lines.
        List<Component> normalTooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), normalTooltip, TooltipFlag.NORMAL);
        helper.assertTrue(normalTooltip.size() == 4,
                "Lockpicks normal tooltip line count mismatch");
        helper.assertTrue("Slot: Hands".equals(normalTooltip.get(0).getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(normalTooltip.get(0).getStyle().getColor()),
                "Lockpicks slot line mismatch");
        helper.assertTrue(CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY.equals(
                        ((TranslatableContents) normalTooltip.get(1).getContents()).getKey())
                        && TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                                .equals(normalTooltip.get(1).getStyle().getColor()),
                "Lockpicks when-worn heading is not the shared yellow heading");
        helper.assertTrue(
                "Add your Agility to your Intellect.".equals(normalTooltip.get(2).getString()),
                "Lockpicks effect line text mismatch");
        helper.assertTrue(
                "tooltip.goetyarkham.lockpicks.hold_shift".equals(
                        ((TranslatableContents) normalTooltip.get(3).getContents()).getKey()),
                "Lockpicks did not fall back to the hold-shift prompt line");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockpicksAgilityZeroAddsNoIntellect(GameTestHelper helper) {
        TestPlayer player = testPlayer(helper.getLevel(), "lockpicks-agility-zero");
        try {
            equip(player, helper);
            PlayerStatsService.setBase(player, StatType.AGILITY, 0);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT) == 0,
                    "Lockpicks added an Intellect bonus with zero Agility");
            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty")
    public static void lockpicksAgilityThreeAddsThreeIntellect(GameTestHelper helper) {
        TestPlayer player = testPlayer(helper.getLevel(), "lockpicks-agility-three");
        try {
            int baseIntellect = PlayerStatsService.getFinalValue(player, StatType.INTELLECT);
            equip(player, helper);
            PlayerStatsService.setBase(player, StatType.AGILITY, 3);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT)
                            == baseIntellect + 3,
                    "Lockpicks did not add exactly +3 Intellect for +3 Agility");
            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty")
    public static void lockpicksBonusTracksAgilityChangesLive(GameTestHelper helper) {
        TestPlayer player = testPlayer(helper.getLevel(), "lockpicks-live-update");
        try {
            equip(player, helper);
            PlayerStatsService.setBase(player, StatType.AGILITY, 2);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT) == 2,
                    "Initial Agility-derived Intellect bonus was wrong");

            // No curio-change/equipment-refresh event is fired here: the
            // bonus must already track a bare Agility change on its own.
            PlayerStatsService.setBase(player, StatType.AGILITY, 7);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT) == 7,
                    "Intellect bonus did not update immediately after Agility changed");

            PlayerStatsService.setBase(player, StatType.AGILITY, -2);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT) == -2,
                    "Intellect bonus did not track a decreased Agility value");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty")
    public static void lockpicksBonusRemovedImmediatelyOnUnequip(GameTestHelper helper) {
        TestPlayer player = testPlayer(helper.getLevel(), "lockpicks-unequip");
        try {
            PlayerStatsService.setBase(player, StatType.AGILITY, 4);
            int baseIntellect = PlayerStatsService.getFinalValue(player, StatType.INTELLECT);
            helper.assertTrue(baseIntellect == 0,
                    "Test setup expected a zero baseline Intellect");

            ICurioStacksHandler hands = equip(player, helper);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT) == 4,
                    "Equipping Lockpicks did not add the Agility bonus");

            hands.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT) == 0,
                    "Unequipping Lockpicks left a residual Intellect bonus");
            helper.assertTrue(
                    PlayerStatsService.get(player)
                            .map(stats -> stats.get(StatType.INTELLECT).base())
                            .orElse(-1) == 0,
                    "Lockpicks permanently modified the stored base Intellect");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty")
    public static void lockpicksBonusNeverDoubleCountsAcrossLifecycleEvents(
            GameTestHelper helper) {
        TestPlayer player = testPlayer(helper.getLevel(), "lockpicks-lifecycle");
        try {
            equip(player, helper);
            PlayerStatsService.setBase(player, StatType.AGILITY, 5);

            int first = PlayerStatsService.getFinalValue(player, StatType.INTELLECT);
            // Nothing in this mechanism is persisted or accumulated: reading
            // it repeatedly (standing in for re-reads across login, respawn,
            // and dimension-change reconciles, none of which this bonus
            // needs to hook into) must always recompute the same value.
            int second = PlayerStatsService.getFinalValue(player, StatType.INTELLECT);
            int third = PlayerStatsService.getFinalValue(player, StatType.INTELLECT);
            helper.assertTrue(first == 5 && second == 5 && third == 5,
                    "Repeated reads accumulated the Agility bonus instead of"
                            + " recomputing it fresh");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty", batch = CHAOS_BAG_TEST_BATCH)
    public static void lockpicksNonIntellectCheckDoesNotConsumeDurability(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        TestPlayer player = testPlayer(level, "lockpicks-non-intellect");
        ResourceLocation forcedSource = null;
        try {
            ICurioStacksHandler hands = equip(player, helper);
            ItemStack lockpicks = hands.getStacks().getStackInSlot(0);

            forcedSource = forceEffectiveBag(server, ChaosToken.number(0), "non-intellect");
            ChaosCheckResult result = runCheck(
                    player, ChaosBaseValueSource.FIXED, 0, 0, ChaosToken.number(0));
            helper.assertTrue(result.success(), "Test setup expected a successful check");
            helper.assertTrue(lockpicks.getDamageValue() == 0,
                    "A non-Intellect check consumed Lockpicks durability");

            helper.succeed();
        } finally {
            if (forcedSource != null) {
                ChaosBagApi.undoSource(server, forcedSource);
            }
            discard(player);
        }
    }

    @GameTest(template = "empty", batch = CHAOS_BAG_TEST_BATCH)
    public static void lockpicksFailedIntellectCheckDoesNotConsumeDurability(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        TestPlayer player = testPlayer(level, "lockpicks-failed-check");
        ResourceLocation forcedSource = null;
        try {
            ICurioStacksHandler hands = equip(player, helper);
            ItemStack lockpicks = hands.getStacks().getStackInSlot(0);

            forcedSource = forceEffectiveBag(server, ChaosToken.AUTO_FAIL, "failure");
            ChaosCheckResult result = runCheck(
                    player, ChaosBaseValueSource.INTELLECT, 0, 0, ChaosToken.AUTO_FAIL);
            helper.assertTrue(!result.success(), "Test setup expected a failed check");
            helper.assertTrue(lockpicks.getDamageValue() == 0,
                    "A failed Intellect check consumed Lockpicks durability");

            helper.succeed();
        } finally {
            if (forcedSource != null) {
                ChaosBagApi.undoSource(server, forcedSource);
            }
            discard(player);
        }
    }

    @GameTest(template = "empty", batch = CHAOS_BAG_TEST_BATCH)
    public static void lockpicksSuccessMarginsZeroToTwoConsumeExactlyOneDurability(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        TestPlayer player = testPlayer(level, "lockpicks-margin-0-2");
        ResourceLocation forcedSource = null;
        try {
            ICurioStacksHandler hands = equip(player, helper);
            ItemStack lockpicks = hands.getStacks().getStackInSlot(0);

            for (int margin = 0; margin <= 2; margin++) {
                int before = lockpicks.getDamageValue();
                forcedSource = forceEffectiveBag(
                        server, ChaosToken.number(margin), "margin-" + margin);
                ChaosCheckResult result = runCheck(
                        player, ChaosBaseValueSource.INTELLECT, 0, 0,
                        ChaosToken.number(margin));
                helper.assertTrue(result.success() && result.finalValue() == margin,
                        "Test setup for margin " + margin + " was not a success at that margin");
                helper.assertTrue(lockpicks.getDamageValue() == before + 1,
                        "A success with margin " + margin
                                + " did not consume exactly 1 durability"
                                + " (single-stage check must never double- or"
                                + " zero-deduct)");

                ChaosBagApi.undoSource(server, forcedSource);
                forcedSource = null;
                // Restore the point just consumed so each margin is tested
                // independently against the same starting durability.
                repairOnePoint(lockpicks);
            }

            helper.succeed();
        } finally {
            if (forcedSource != null) {
                ChaosBagApi.undoSource(server, forcedSource);
            }
            discard(player);
        }
    }

    @GameTest(template = "empty", batch = CHAOS_BAG_TEST_BATCH)
    public static void lockpicksSuccessMarginThreeOrMoreDoesNotConsumeDurability(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        TestPlayer player = testPlayer(level, "lockpicks-margin-3plus");
        ResourceLocation forcedSource = null;
        try {
            ICurioStacksHandler hands = equip(player, helper);
            ItemStack lockpicks = hands.getStacks().getStackInSlot(0);

            for (int margin : new int[] {3, 10}) {
                forcedSource = forceEffectiveBag(
                        server, ChaosToken.number(margin), "margin-" + margin);
                ChaosCheckResult result = runCheck(
                        player, ChaosBaseValueSource.INTELLECT, 0, 0,
                        ChaosToken.number(margin));
                helper.assertTrue(result.success() && result.finalValue() == margin,
                        "Test setup for margin " + margin + " was not a success at that margin");
                helper.assertTrue(lockpicks.getDamageValue() == 0,
                        "A success with margin " + margin + " incorrectly consumed durability");

                ChaosBagApi.undoSource(server, forcedSource);
                forcedSource = null;
            }

            helper.succeed();
        } finally {
            if (forcedSource != null) {
                ChaosBagApi.undoSource(server, forcedSource);
            }
            discard(player);
        }
    }

    @GameTest(template = "empty", batch = CHAOS_BAG_TEST_BATCH)
    public static void lockpicksThirdQualifyingCheckBreaksItAndRemovesBonus(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        TestPlayer player = testPlayer(level, "lockpicks-break");
        ResourceLocation forcedSource = null;
        try {
            ICurioStacksHandler hands = equip(player, helper);
            PlayerStatsService.setBase(player, StatType.AGILITY, 6);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT) == 6,
                    "Test setup expected the Agility bonus to be applied");

            for (int check = 1; check <= LockpicksItem.MAX_DURABILITY; check++) {
                forcedSource = forceEffectiveBag(server, ChaosToken.number(0), "break-" + check);
                // Target equals the boosted Intellect value (6) so the
                // guaranteed +0 token draw lands exactly on margin 0.
                ChaosCheckResult result = runCheck(
                        player, ChaosBaseValueSource.INTELLECT, 0, 6, ChaosToken.number(0));
                helper.assertTrue(result.success() && result.finalValue() - result.targetValue() == 0,
                        "Qualifying check " + check + " unexpectedly failed or missed margin 0");
                ChaosBagApi.undoSource(server, forcedSource);
                forcedSource = null;

                ItemStack remaining = hands.getStacks().getStackInSlot(0);
                if (check < LockpicksItem.MAX_DURABILITY) {
                    helper.assertTrue(!remaining.isEmpty()
                                    && remaining.getDamageValue() == check,
                            "Lockpicks durability after check " + check
                                    + " is wrong or the item broke early");
                } else {
                    helper.assertTrue(remaining.isEmpty(),
                            "Lockpicks did not break on its "
                                    + LockpicksItem.MAX_DURABILITY + "rd qualifying check");
                }
            }

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT) == 0,
                    "The Agility-into-Intellect bonus was not removed immediately"
                            + " after Lockpicks broke");

            helper.succeed();
        } finally {
            if (forcedSource != null) {
                ChaosBagApi.undoSource(server, forcedSource);
            }
            discard(player);
        }
    }

    private static ChaosCheckResult runCheck(
            ServerPlayer player,
            ChaosBaseValueSource source,
            int fixedBaseValue,
            int targetValue,
            ChaosToken forcedToken) {
        ChaosCheckRequest request = ChaosCheckService.createRequest(
                player,
                CHECK_SOURCE,
                source,
                fixedBaseValue,
                targetValue,
                List.<ChaosCheckModifier>of(),
                ChaosBagApi.snapshot(player.getServer()),
                List.of(forcedToken),
                player.getRandom()::nextInt);
        return ChaosCheckService.resolveAndApply(player, request);
    }

    /**
     * Removes every effective token, then adds only {@code guaranteedToken}
     * (tagged under a unique test source), so the very next forced draw is
     * guaranteed to find a matching token in the temporary bag. Mirrors
     * {@code MedicalTextsGameTests#forceEffectiveBag} exactly.
     */
    private static ResourceLocation forceEffectiveBag(
            MinecraftServer server, ChaosToken guaranteedToken, String label) {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "lockpicks_test_" + label + "_" + UUID.randomUUID());
        Map<ChaosToken, Integer> originalCounts = new HashMap<>();
        for (ChaosToken token : ChaosBagApi.getEffectiveConfiguration(server)) {
            originalCounts.merge(token, 1, Integer::sum);
        }
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

    /** Undoes exactly 1 point of {@code hurtAndBreak} damage applied by a prior check. */
    private static void repairOnePoint(ItemStack stack) {
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - 1));
    }

    private static ICurioStacksHandler equip(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler hands = handler(player, CurioSlotIds.HANDS, helper);
        hands.getStacks().setStackInSlot(0, new ItemStack(ModItems.LOCKPICKS.get()));
        return hands;
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

    private static TestPlayer testPlayer(ServerLevel level, String name) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(0.0D, 1.0D, 0.0D);
        return player;
    }

    private static void discard(TestPlayer player) {
        player.discard();
    }

    /**
     * Deliberately never registered in {@code level.players()}, per {@code
     * MedicalTextsGameTests}: nothing this test exercises needs the player
     * spatially discoverable, and leaving an untracked stand-in in that list
     * is a known crash hazard (see project memory on GameTest TestPlayer
     * gotchas).
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
