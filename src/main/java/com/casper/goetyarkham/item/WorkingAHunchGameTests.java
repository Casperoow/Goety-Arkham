package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.EquipmentStatsService;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
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
 * Covers {@link WorkingAHunchItem}: registration, the Asset Curios tag,
 * tooltip layout, the live +4 Intellect / +1 Attack Speed while worn (and
 * their immediate removal on unequip), refresh-safe (non-duplicating)
 * modifier application, and the required resource files.
 */
@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkingAHunchGameTests {
    private static final String BATCH = "goetyarkham:working_a_hunch";

    private WorkingAHunchGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void registrationTagsAndTooltip(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "working_a_hunch");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.WORKING_A_HUNCH.get())),
                "Working a Hunch registry ID mismatch");

        WorkingAHunchItem item = ModItems.WORKING_A_HUNCH.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Working a Hunch must not stack");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(CurioSlotIds.ASSET)),
                "Working a Hunch item tag must expose only the asset slot");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 4,
                "Working a Hunch tooltip line count mismatch");
        helper.assertTrue(tooltip.get(0).getContents()
                        instanceof TranslatableContents slotHeading
                        && "tooltip.goetyarkham.working_a_hunch.slot".equals(slotHeading.getKey())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(0).getStyle().getColor()),
                "Working a Hunch slot line mismatch");
        helper.assertTrue(tooltip.get(1).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY.equals(heading.getKey())
                        && TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                                .equals(tooltip.get(1).getStyle().getColor()),
                "Working a Hunch when-worn heading is not the shared yellow heading");
        helper.assertTrue("+4 Intellect".equals(tooltip.get(2).getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(2).getStyle().getColor()),
                "Working a Hunch Intellect tooltip line mismatch");
        helper.assertTrue("+1 Attack Speed".equals(tooltip.get(3).getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(3).getStyle().getColor()),
                "Working a Hunch Attack Speed tooltip line mismatch");

        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void cosmeticSlotGrantsNoBonuses(GameTestHelper helper) {
        WorkingAHunchItem item = ModItems.WORKING_A_HUNCH.get();
        ItemStack stack = new ItemStack(item);
        TestPlayer player = testPlayer(helper.getLevel(), "working-a-hunch-cosmetic");
        try {
            SlotContext cosmeticAsset = new SlotContext(
                    CurioSlotIds.ASSET, player, 0, true, true);
            for (StatType stat : StatType.values()) {
                helper.assertTrue(item.getEquipmentStatModifier(
                                stat, cosmeticAsset, stack) == 0,
                        "Cosmetic asset slot granted a " + stat + " bonus");
            }
            helper.assertTrue(item.getAttributeModifiers(
                            cosmeticAsset, UUID.randomUUID(), stack).isEmpty(),
                    "Cosmetic asset slot granted an Attack Speed bonus");

            SlotContext nonAsset = new SlotContext(
                    CurioSlotIds.NECKLACE, player, 0, false, true);
            helper.assertTrue(!item.canEquip(nonAsset, stack),
                    "Working a Hunch accepted a non-asset slot");
            helper.assertTrue(item.getEquipmentStatModifier(
                            StatType.INTELLECT, nonAsset, stack) == 0,
                    "Non-asset slot granted an Intellect bonus");
            helper.assertTrue(item.getAttributeModifiers(
                            nonAsset, UUID.randomUUID(), stack).isEmpty(),
                    "Non-asset slot granted an Attack Speed bonus");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void distinctSlotIndicesGetDistinctModifierIds(GameTestHelper helper) {
        WorkingAHunchItem item = ModItems.WORKING_A_HUNCH.get();
        ItemStack stack = new ItemStack(item);
        TestPlayer player = testPlayer(helper.getLevel(), "working-a-hunch-uuids");
        try {
            SlotContext first = new SlotContext(CurioSlotIds.ASSET, player, 0, false, true);
            SlotContext second = new SlotContext(CurioSlotIds.ASSET, player, 1, false, true);

            AttributeModifier firstModifier = item.getAttributeModifiers(
                            first, UUID.randomUUID(), stack)
                    .get(Attributes.ATTACK_SPEED).stream().findFirst().orElse(null);
            AttributeModifier secondModifier = item.getAttributeModifiers(
                            second, UUID.randomUUID(), stack)
                    .get(Attributes.ATTACK_SPEED).stream().findFirst().orElse(null);
            AttributeModifier firstAgain = item.getAttributeModifiers(
                            first, UUID.randomUUID(), stack)
                    .get(Attributes.ATTACK_SPEED).stream().findFirst().orElse(null);

            helper.assertTrue(firstModifier != null && secondModifier != null,
                    "Working a Hunch did not supply an Attack Speed modifier for both slots");
            helper.assertTrue(firstModifier.getAmount() == WorkingAHunchItem.ATTACK_SPEED_BONUS
                            && firstModifier.getOperation()
                                    == AttributeModifier.Operation.ADDITION,
                    "Working a Hunch Attack Speed modifier mismatch");
            helper.assertTrue(!firstModifier.getId().equals(secondModifier.getId()),
                    "Two asset slot indices did not receive distinct Attack Speed modifier UUIDs");
            helper.assertTrue(firstModifier.getId().equals(firstAgain.getId()),
                    "The same slot index produced a different modifier UUID on a repeated read");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void runtimeStackingAndLifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "working-a-hunch-runtime");
        try {
            ICurioStacksHandler asset = handler(wearer, CurioSlotIds.ASSET, helper);

            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            int baseStoredIntellect = PlayerStatsService.get(wearer)
                    .map(stats -> stats.get(StatType.INTELLECT).base())
                    .orElse(-1);
            double baseAttackSpeed = wearer.getAttribute(Attributes.ATTACK_SPEED).getValue();

            // Not equipped yet: baseline only.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT) == baseIntellect,
                    "Test setup expected the pre-equip Intellect baseline");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue() == baseAttackSpeed,
                    "Test setup expected the pre-equip Attack Speed baseline");

            // Equip: exactly +4 Intellect / +1 Attack Speed.
            asset.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.WORKING_A_HUNCH.get()));
            settleCurioChange(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + WorkingAHunchItem.INTELLECT_BONUS,
                    "Working a Hunch did not add exactly +4 Intellect");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed + WorkingAHunchItem.ATTACK_SPEED_BONUS,
                    "Working a Hunch did not add exactly +1 Attack Speed");

            // Repeated refreshes (standing in for reopening the curio screen,
            // login, respawn, and dimension-change reconciles) must never
            // duplicate either bonus.
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + WorkingAHunchItem.INTELLECT_BONUS,
                    "Repeated refreshes duplicated the Intellect bonus");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed + WorkingAHunchItem.ATTACK_SPEED_BONUS,
                    "Repeated refreshes duplicated the Attack Speed bonus");

            // Unequip: both bonuses vanish immediately, and the stored base
            // Intellect (the authoritative, persisted value) is untouched.
            asset.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect,
                    "Unequipping Working a Hunch left a residual Intellect bonus");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed,
                    "Unequipping Working a Hunch left a residual Attack Speed bonus");
            helper.assertTrue(
                    PlayerStatsService.get(wearer)
                            .map(stats -> stats.get(StatType.INTELLECT).base())
                            .orElse(-1) == baseStoredIntellect,
                    "Working a Hunch permanently modified the stored base Intellect");

            helper.succeed();
        } finally {
            discard(wearer);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void modelLangAndTextureResourcesArePresent(GameTestHelper helper) {
        String model = readResourceAsString(
                "/assets/goetyarkham/models/item/working_a_hunch.json");
        helper.assertTrue(model.contains("\"parent\": \"minecraft:item/generated\""),
                "Working a Hunch item model must use the minecraft:item/generated parent");
        helper.assertTrue(model.contains("\"goetyarkham:item/working_a_lunch\""),
                "Working a Hunch item model must reference the real working_a_lunch texture");

        String zhCn = readResourceAsString("/assets/goetyarkham/lang/zh_cn.json");
        helper.assertTrue(zhCn.contains("\"item.goetyarkham.working_a_hunch\": \"专业的直觉\""),
                "zh_cn.json is missing the Working a Hunch item name");
        helper.assertTrue(zhCn.contains("\"tooltip.goetyarkham.working_a_hunch.slot\""),
                "zh_cn.json is missing the Working a Hunch slot tooltip key");

        String enUs = readResourceAsString("/assets/goetyarkham/lang/en_us.json");
        helper.assertTrue(enUs.contains("\"item.goetyarkham.working_a_hunch\": \"Working a Hunch\""),
                "en_us.json is missing the Working a Hunch item name");
        helper.assertTrue(enUs.contains("\"tooltip.goetyarkham.working_a_hunch.slot\""),
                "en_us.json is missing the Working a Hunch slot tooltip key");

        String assetTag = readResourceAsString("/data/curios/tags/items/asset.json");
        helper.assertTrue(assetTag.contains("\"goetyarkham:working_a_hunch\""),
                "curios:tags/items/asset.json is missing the Working a Hunch entry");

        helper.assertTrue(
                resourceExistsAndNonEmpty("/assets/goetyarkham/textures/item/working_a_lunch.png"),
                "working_a_lunch.png texture is missing or empty");
        helper.succeed();
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

    private static TestPlayer testPlayer(ServerLevel level, String name) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(0.0D, 1.0D, 0.0D);
        return player;
    }

    private static void discard(TestPlayer player) {
        PlayerStatsService.reset(player);
        player.discard();
    }

    private static String readResourceAsString(String path) {
        try (InputStream stream = WorkingAHunchGameTests.class.getResourceAsStream(path)) {
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
        try (InputStream stream = WorkingAHunchGameTests.class.getResourceAsStream(path)) {
            return stream != null && stream.read() != -1;
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Deliberately never registered in {@code level.players()}, per the
     * project's GameTest TestPlayer gotchas: a connectionless stand-in left
     * in that list is a live crash hazard. Curios still applies/removes the
     * real {@code AttributeInstance} modifier off the manually-posted tick
     * event ({@link #settleCurioChange}) without needing the player to be
     * level-tracked.
     */
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
