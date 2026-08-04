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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagnifyingGlassGameTests {
    private MagnifyingGlassGameTests() {
    }

    @GameTest(template = "empty")
    public static void magnifyingGlassRegistrationTagsAndTooltip(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "magnifying_glass");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.MAGNIFYING_GLASS.get())),
                "Magnifying Glass registry ID mismatch");

        MagnifyingGlassItem item = ModItems.MAGNIFYING_GLASS.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Magnifying Glass must not stack");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(CurioSlotIds.HANDS)),
                "Magnifying Glass item tag must expose only the hands slot");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 4,
                "Magnifying Glass tooltip line count mismatch");
        helper.assertTrue("Slot: Hands".equals(tooltip.get(0).getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(0).getStyle().getColor()),
                "Magnifying Glass slot line mismatch");
        helper.assertTrue(tooltip.get(1).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY.equals(heading.getKey())
                        && TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                                .equals(tooltip.get(1).getStyle().getColor()),
                "Magnifying Glass when-worn heading is not the shared yellow heading");
        helper.assertTrue("+1 Intellect".equals(tooltip.get(2).getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(2).getStyle().getColor()),
                "Magnifying Glass Intellect tooltip line mismatch");
        helper.assertTrue("+1 Attack Speed".equals(tooltip.get(3).getString())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                                .equals(tooltip.get(3).getStyle().getColor()),
                "Magnifying Glass Attack Speed tooltip line mismatch");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void magnifyingGlassCosmeticSlotGrantsNoBonuses(GameTestHelper helper) {
        MagnifyingGlassItem item = ModItems.MAGNIFYING_GLASS.get();
        ItemStack stack = new ItemStack(item);
        TestPlayer player = testPlayer(helper.getLevel(), "magnifying-glass-cosmetic");
        try {
            SlotContext cosmeticHands = new SlotContext(
                    CurioSlotIds.HANDS, player, 0, true, true);
            for (StatType stat : StatType.values()) {
                helper.assertTrue(item.getEquipmentStatModifier(
                                stat, cosmeticHands, stack) == 0,
                        "Cosmetic hands slot granted a " + stat + " bonus");
            }
            helper.assertTrue(item.getAttributeModifiers(
                            cosmeticHands, UUID.randomUUID(), stack).isEmpty(),
                    "Cosmetic hands slot granted an Attack Speed bonus");

            SlotContext nonHands = new SlotContext(
                    CurioSlotIds.NECKLACE, player, 0, false, true);
            helper.assertTrue(!item.canEquip(nonHands, stack),
                    "Magnifying Glass accepted a non-hands slot");
            helper.assertTrue(item.getEquipmentStatModifier(
                            StatType.INTELLECT, nonHands, stack) == 0,
                    "Non-hands slot granted an Intellect bonus");
            helper.assertTrue(item.getAttributeModifiers(
                            nonHands, UUID.randomUUID(), stack).isEmpty(),
                    "Non-hands slot granted an Attack Speed bonus");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty")
    public static void magnifyingGlassDistinctSlotIndicesGetDistinctModifierIds(
            GameTestHelper helper) {
        MagnifyingGlassItem item = ModItems.MAGNIFYING_GLASS.get();
        ItemStack stack = new ItemStack(item);
        TestPlayer player = testPlayer(helper.getLevel(), "magnifying-glass-uuids");
        try {
            SlotContext first = new SlotContext(CurioSlotIds.HANDS, player, 0, false, true);
            SlotContext second = new SlotContext(CurioSlotIds.HANDS, player, 1, false, true);

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
                    "Magnifying Glass did not supply an Attack Speed modifier for both slots");
            helper.assertTrue(firstModifier.getAmount() == MagnifyingGlassItem.ATTACK_SPEED_BONUS
                            && firstModifier.getOperation()
                                    == AttributeModifier.Operation.ADDITION,
                    "Magnifying Glass Attack Speed modifier mismatch");
            helper.assertTrue(!firstModifier.getId().equals(secondModifier.getId()),
                    "Two hands slot indices did not receive distinct Attack Speed modifier UUIDs");
            helper.assertTrue(firstModifier.getId().equals(firstAgain.getId()),
                    "The same slot index produced a different modifier UUID on a repeated read");

            helper.succeed();
        } finally {
            discard(player);
        }
    }

    @GameTest(template = "empty")
    public static void magnifyingGlassRuntimeStackingAndLifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "magnifying-glass-runtime");
        try {
            ICurioStacksHandler hands = handler(wearer, CurioSlotIds.HANDS, helper);

            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            int baseStoredIntellect = PlayerStatsService.get(wearer)
                    .map(stats -> stats.get(StatType.INTELLECT).base())
                    .orElse(-1);
            double baseAttackSpeed = wearer.getAttribute(Attributes.ATTACK_SPEED).getValue();

            // Equip one: exactly +1/+1.
            hands.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.MAGNIFYING_GLASS.get()));
            settleCurioChange(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 1,
                    "One Magnifying Glass did not add exactly +1 Intellect");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed + 1.0D,
                    "One Magnifying Glass did not add exactly +1 Attack Speed");

            // Equip a second one in the other hands slot: stacks to +2/+2.
            hands.getStacks().setStackInSlot(
                    1, new ItemStack(ModItems.MAGNIFYING_GLASS.get()));
            settleCurioChange(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 2,
                    "Two Magnifying Glasses did not stack to +2 Intellect");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed + 2.0D,
                    "Two Magnifying Glasses did not stack to +2 Attack Speed");

            // Repeated refreshes (standing in for reopening the curio screen,
            // login, respawn, and dimension-change reconciles) must never
            // duplicate either bonus.
            EquipmentStatsService.refresh(wearer);
            EquipmentStatsService.refresh(wearer);
            settleCurioChange(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 2,
                    "Repeated refreshes duplicated the Intellect bonus");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed + 2.0D,
                    "Repeated refreshes duplicated the Attack Speed bonus");

            // Unequip one: removes exactly one share, not both.
            hands.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 1,
                    "Unequipping one Magnifying Glass did not remove exactly one Intellect share");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed + 1.0D,
                    "Unequipping one Magnifying Glass did not remove exactly one Attack Speed share");

            // Unequip the last one: back to the exact pre-equip baseline.
            hands.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect,
                    "Unequipping the last Magnifying Glass left a residual Intellect bonus");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed,
                    "Unequipping the last Magnifying Glass left a residual Attack Speed bonus");
            helper.assertTrue(
                    PlayerStatsService.get(wearer)
                            .map(stats -> stats.get(StatType.INTELLECT).base())
                            .orElse(-1) == baseStoredIntellect,
                    "Magnifying Glass permanently modified the stored base Intellect");

            helper.succeed();
        } finally {
            discard(wearer);
        }
    }

    @GameTest(template = "empty")
    public static void magnifyingGlassCoexistsWithOtherStatAndAttributeSources(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "magnifying-glass-coexist");
        UUID otherAttackSpeedSource = UUID.randomUUID();
        try {
            ICurioStacksHandler hands = handler(wearer, CurioSlotIds.HANDS, helper);
            ICurioStacksHandler necklace = handler(wearer, CurioSlotIds.NECKLACE, helper);

            int baseIntellect = PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT);
            double baseAttackSpeed = wearer.getAttribute(Attributes.ATTACK_SPEED).getValue();

            // An unrelated Attack Speed source (standing in for another mod's
            // equipment) must stack additively alongside Magnifying Glass's
            // own modifier rather than being overwritten by it.
            wearer.getAttribute(Attributes.ATTACK_SPEED).addTransientModifier(
                    new AttributeModifier(otherAttackSpeedSource, "test:other_attack_speed",
                            0.5D, AttributeModifier.Operation.ADDITION));

            // Rabbit's Foot independently grants +1 Intellect via the same
            // EquipmentStatModifier mechanism, from a different slot.
            necklace.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.RABBIT_FOOT.get()));
            hands.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.MAGNIFYING_GLASS.get()));
            settleCurioChange(wearer);

            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect + 2,
                    "Magnifying Glass's Intellect bonus did not coexist additively"
                            + " with Rabbit's Foot's own Intellect bonus");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed + 1.5D,
                    "Magnifying Glass's Attack Speed bonus did not coexist additively"
                            + " with an unrelated Attack Speed source");

            hands.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            necklace.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            wearer.getAttribute(Attributes.ATTACK_SPEED)
                    .removeModifier(otherAttackSpeedSource);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(wearer, StatType.INTELLECT)
                            == baseIntellect,
                    "Unequipping both Curios left a residual Intellect bonus");
            helper.assertTrue(
                    wearer.getAttribute(Attributes.ATTACK_SPEED).getValue()
                            == baseAttackSpeed,
                    "Unequipping and clearing the other source left a residual Attack Speed bonus");

            helper.succeed();
        } finally {
            discard(wearer);
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

    private static void settleCurioChange(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
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
     * Deliberately never registered in {@code level.players()}, per the
     * project's GameTest TestPlayer gotchas: a connectionless stand-in left
     * in that list is a live crash hazard the moment any unrelated nearby
     * entity (e.g. a falling block from another mod's test) gets broadcast.
     * Curios still applies/removes the real {@code AttributeInstance}
     * modifier off the manually-posted tick event ({@link
     * #settleCurioChange}) without needing the player to be level-tracked.
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
