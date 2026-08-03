package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioEquipRules;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.stats.EquipmentStatsService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
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
import java.util.Set;
import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PoliceBadgeGameTests {
    private PoliceBadgeGameTests() {
    }

    @GameTest(template = "empty")
    public static void policeBadgeGrantsAndRemovesStatsWhenWorn(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "police_badge");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.POLICE_BADGE.get())),
                "Police Badge registry ID mismatch");

        PoliceBadgeItem item = ModItems.POLICE_BADGE.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Police Badge must not stack");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 4,
                "Police Badge tooltip line count mismatch");
        helper.assertTrue(CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY.equals(
                        ((net.minecraft.network.chat.contents.TranslatableContents)
                                tooltip.get(0).getContents()).getKey()),
                "Police Badge does not use the shared when-worn heading");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Police Badge when-worn heading is not yellow");
        for (int i = 1; i <= 3; i++) {
            helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                            .equals(tooltip.get(i).getStyle().getColor()),
                    "Police Badge effect line " + i + " is not gray");
        }
        helper.assertTrue("When worn:".equals(tooltip.get(0).getString())
                        && "+1 Strength".equals(tooltip.get(1).getString())
                        && "+1 Agility".equals(tooltip.get(2).getString())
                        && "+2 Will".equals(tooltip.get(3).getString()),
                "Police Badge English tooltip text mismatch");

        java.util.Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(CurioSlotIds.CHARM)),
                "Police Badge item tag must expose only the charm slot");

        ServerPlayer wearer = new TestPlayer(helper.getLevel(), "police-badge-wearer");
        helper.getLevel().players().add(wearer);
        try {
            ICurioStacksHandler charm = handler(wearer, CurioSlotIds.CHARM, helper);

            int baseStrength = PlayerStatsService.getFinalValue(wearer, StatType.STRENGTH);
            int baseAgility = PlayerStatsService.getFinalValue(wearer, StatType.AGILITY);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            charm.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.STRENGTH) == baseStrength + 1,
                    "Police Badge did not add exactly one final Strength");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.AGILITY) == baseAgility + 1,
                    "Police Badge did not add exactly one final Agility");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWillpower + 2,
                    "Police Badge did not add exactly two final Willpower");

            EquipmentStatsService.refresh(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.STRENGTH) == baseStrength + 1,
                    "Repeated equipment refresh duplicated the Police Badge bonus");

            charm.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.STRENGTH) == baseStrength,
                    "Unequipping the Police Badge left a residual Strength bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.AGILITY) == baseAgility,
                    "Unequipping the Police Badge left a residual Agility bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWillpower,
                    "Unequipping the Police Badge left a residual Willpower bonus");
            helper.assertTrue(PlayerStatsService.get(wearer)
                            .map(stats -> stats.get(StatType.STRENGTH).base())
                            .orElse(0) == baseStrength,
                    "Police Badge changed the stored base Strength");
            helper.succeed();
        } finally {
            helper.getLevel().players().remove(wearer);
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void policeBadgeCannotBeEquippedTwiceByOnePlayer(GameTestHelper helper) {
        ServerPlayer wearer = new TestPlayer(helper.getLevel(), "police-badge-duplicate");
        helper.getLevel().players().add(wearer);
        try {
            ICurioStacksHandler charm = handler(wearer, CurioSlotIds.CHARM, helper);
            helper.assertTrue(charm.getStacks().getSlots() >= 2,
                    "Test requires at least two charm slots");

            ItemStack first = new ItemStack(ModItems.POLICE_BADGE.get());
            ItemStack second = new ItemStack(ModItems.POLICE_BADGE.get());
            // Different NBT and a custom name must not exempt the second copy.
            second.setHoverName(Component.literal("Renamed Badge"));

            helper.assertTrue(charm.getStacks().isItemValid(0, first),
                    "An empty charm slot rejected the first Police Badge");
            charm.getStacks().setStackInSlot(0, first);
            settleCurioChange(wearer);

            helper.assertTrue(!charm.getStacks().isItemValid(1, second),
                    "A second Police Badge with different NBT was not rejected"
                            + " by another charm slot");

            // The stack must not have been consumed, duplicated, dropped, or
            // have replaced the first equipped Police Badge.
            helper.assertTrue(second.getCount() == 1,
                    "Rejected Police Badge stack was consumed");
            helper.assertTrue(charm.getStacks().getStackInSlot(0).is(
                            ModItems.POLICE_BADGE.get()),
                    "Rejected equip attempt overwrote the already-equipped Police Badge");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER)
                            == PlayerStatsService.get(wearer)
                            .map(stats -> stats.get(StatType.WILLPOWER).base())
                            .orElse(0) + 2,
                    "Attempting a second Police Badge changed the stat total"
                            + " (must remain a single +2 Willpower bonus)");

            // A same-slot revalidation of the badge already worn there must
            // not be treated as a duplicate of itself.
            helper.assertTrue(charm.getStacks().isItemValid(0, first),
                    "Revalidating the already-equipped slot incorrectly"
                            + " flagged it as a duplicate");

            // The shared rule inspects every Curios slot, not just the one
            // requested, and ignores slot-type identity: even a Police
            // Badge tested against a differently-identified slot for the
            // same wearer must be treated as a duplicate.
            SlotContext otherSlotType = new SlotContext(
                    "necklace", wearer, 0, false, true);
            helper.assertTrue(CurioEquipRules.isDuplicateElsewhere(otherSlotType, second),
                    "Cross-slot-type duplicate of an equipped Police Badge was"
                            + " not detected");

            // Two different mod Curios must remain independently equippable.
            ItemStack statue = new ItemStack(ModItems.GROTESQUE_STATUE.get());
            helper.assertTrue(charm.getStacks().isItemValid(1, statue),
                    "A different Goety: Arkham charm item was incorrectly"
                            + " treated as a duplicate of the Police Badge");

            // A foreign (non-Goety: Arkham) item must never be restricted by
            // this rule, even if it were somehow already present elsewhere.
            ItemStack foreignItem = new ItemStack(Items.DIAMOND);
            helper.assertTrue(!CurioEquipRules.isOwnedByThisMod(foreignItem.getItem()),
                    "A vanilla item was incorrectly classified as mod-owned");
            helper.assertTrue(!CurioEquipRules.isDuplicateElsewhere(otherSlotType, foreignItem),
                    "The duplicate rule incorrectly restricted a non-mod item");

            helper.succeed();
        } finally {
            helper.getLevel().players().remove(wearer);
            wearer.discard();
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

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }
    }
}
