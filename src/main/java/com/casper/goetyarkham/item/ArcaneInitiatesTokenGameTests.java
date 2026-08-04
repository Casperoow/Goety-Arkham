package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.SanityService;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.List;
import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArcaneInitiatesTokenGameTests {
    private ArcaneInitiatesTokenGameTests() {
    }

    @GameTest(template = "empty")
    public static void arcaneInitiatesTokenGrantsFocusSlotAndAttributes(
            GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "arcane_initiates_token");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.ARCANE_INITIATES_TOKEN.get())),
                "Arcane Initiate's Token registry ID mismatch");

        ArcaneInitiatesTokenItem item = ModItems.ARCANE_INITIATES_TOKEN.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Arcane Initiate's Token must not stack");

        List<Component> tooltip = new java.util.ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 5,
                "Arcane Initiate's Token tooltip line count mismatch");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Arcane Initiate's Token when-worn heading is not yellow");
        for (int i = 1; i <= 4; i++) {
            helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                            .equals(tooltip.get(i).getStyle().getColor()),
                    "Arcane Initiate's Token effect line " + i + " is not gray");
        }
        helper.assertTrue("Gain 1 additional Focus slot".equals(tooltip.get(1).getString())
                        && "+1 Max Health".equals(tooltip.get(2).getString())
                        && "+2 Max Sanity".equals(tooltip.get(3).getString())
                        && "+2 Will".equals(tooltip.get(4).getString()),
                "Arcane Initiate's Token English tooltip text mismatch");

        var acceptedSlots = CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(
                        java.util.Set.of(CurioSlotIds.TOKEN)),
                "Arcane Initiate's Token item tag must expose only the token slot");

        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-token-wearer", 1.5D);
        try {
            ICurioStacksHandler tokenHandler =
                    handler(wearer, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler focusHandler =
                    handler(wearer, CurioSlotIds.FOCUS, helper);
            int baseFocusSlots = focusHandler.getStacks().getSlots();
            double baseMaxHealth = wearer.getAttribute(Attributes.MAX_HEALTH).getValue();
            int baseMaxSanity = SanityService.getMaximumSanity(wearer);
            int baseWillpower = PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER);

            tokenHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            if (wearer.getHealth() < wearer.getMaxHealth()) {
                wearer.setHealth(wearer.getMaxHealth());
            }

            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Equipping did not add exactly one focus slot");
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaxHealth + ArcaneInitiatesTokenItem.MAX_HEALTH_BONUS,
                    "Arcane Initiate's Token did not add exactly +1 Max Health");
            helper.assertTrue(SanityService.getMaximumSanity(wearer)
                            == baseMaxSanity + ArcaneInitiatesTokenItem.MAX_SANITY_BONUS,
                    "Arcane Initiate's Token did not add exactly +2 Max Sanity");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower + ArcaneInitiatesTokenItem.WILLPOWER_BONUS,
                    "Arcane Initiate's Token did not add exactly +2 Willpower");

            // Repeated reconciliation (login/respawn/dimension-change stand-in)
            // must never duplicate the slot or the attribute bonuses.
            ArcaneInitiatesTokenService.reconcile(wearer);
            ArcaneInitiatesTokenService.reconcile(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Repeated reconciliation duplicated the focus slot");
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaxHealth + ArcaneInitiatesTokenItem.MAX_HEALTH_BONUS,
                    "Repeated reconciliation duplicated the Max Health bonus");

            // Base focus slots and any pre-existing contents must be untouched.
            helper.assertTrue(focusHandler.getStacks().getStackInSlot(0).isEmpty()
                            && focusHandler.getStacks().getStackInSlot(1).isEmpty(),
                    "Equipping altered the base focus slots' contents");

            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Unequipping did not remove the granted focus slot");
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaxHealth,
                    "Unequipping left a residual Max Health bonus");
            helper.assertTrue(SanityService.getMaximumSanity(wearer) == baseMaxSanity,
                    "Unequipping left a residual Max Sanity bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(wearer, StatType.WILLPOWER)
                            == baseWillpower,
                    "Unequipping left a residual Willpower bonus");

            // Repeated equip/unequip cycles must not drift the slot count.
            tokenHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            tokenHandler.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Repeated equip/unequip cycles left a residual focus slot");

            helper.succeed();
        } finally {
            level.players().remove(wearer);
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void arcaneInitiatesTokenSafelyRecallsFocusOnUnequip(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-token-recall", 1.5D);
        try {
            ICurioStacksHandler tokenHandler =
                    handler(wearer, CurioSlotIds.TOKEN, helper);
            ICurioStacksHandler focusHandler =
                    handler(wearer, CurioSlotIds.FOCUS, helper);
            int baseFocusSlots = focusHandler.getStacks().getSlots();

            tokenHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.ARCANE_INITIATES_TOKEN.get()));
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Equipping did not add the extra focus slot for the recall test");

            // The extra slot is always the highest-numbered one after a grow.
            int extraSlot = focusHandler.getStacks().getSlots() - 1;
            ItemStack placed = new ItemStack(net.minecraft.world.item.Items.STICK, 3);
            focusHandler.getStacks().setStackInSlot(extraSlot, placed);
            helper.assertTrue(!focusHandler.getStacks()
                            .getStackInSlot(extraSlot).isEmpty(),
                    "Test setup failed to place an item in the extra focus slot");

            int inventoryBefore = wearer.getInventory().countItem(Items.STICK);
            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            // The unequip shrink queues the orphaned stack via Curios' own
            // loseInvalidStack/handleInvalidStacks recall path, which only
            // hands it to the wearer's inventory on Curios' own next tick
            // pass; settle twice to cross both boundaries deterministically.
            settleCurioChange(wearer);
            settleCurioChange(wearer);

            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Unequipping did not remove the granted focus slot in the recall test");
            helper.assertTrue(wearer.getInventory().countItem(Items.STICK)
                            == inventoryBefore + 3,
                    "The focus item in the vacated slot was not safely recalled"
                            + " to the wearer's inventory instead of being deleted");

            helper.succeed();
        } finally {
            level.players().remove(wearer);
            wearer.discard();
        }
    }

    @GameTest(template = "empty")
    public static void arcaneInitiatesTokenFocusSlotStacksWithOtherProviders(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer wearer = testPlayer(level, "arcane-token-stacking", 1.5D);
        UUID otherProviderId = UUID.randomUUID();
        try {
            ICurioStacksHandler tokenHandler =
                    handler(wearer, CurioSlotIds.TOKEN, helper);
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(wearer)
                    .resolve().orElse(null);
            helper.assertTrue(inventory != null,
                    "Missing Curios inventory for stacking test");
            ICurioStacksHandler focusHandler =
                    handler(wearer, CurioSlotIds.FOCUS, helper);
            int baseFocusSlots = focusHandler.getStacks().getSlots();

            // Simulate a second, independent focus-slot-granting Curio using
            // the same permanent-slot-modifier mechanism.
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.FOCUS, otherProviderId, "test:other_focus_provider",
                    1.0D, AttributeModifier.Operation.ADDITION);
            focusHandler.getStacks().getSlots();
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Test setup's synthetic focus-slot modifier did not apply");

            tokenHandler.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.ARCANE_INITIATES_TOKEN.get()));
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 2,
                    "Arcane Initiate's Token did not additively stack with"
                            + " another focus-slot provider");

            // Removing only the token must remove only its own +1, leaving
            // the other provider's slot untouched.
            tokenHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots + 1,
                    "Unequipping the token removed more than its own focus slot");

            inventory.removeSlotModifier(CurioSlotIds.FOCUS, otherProviderId);
            focusHandler.getStacks().getSlots();
            helper.assertTrue(focusHandler.getStacks().getSlots() == baseFocusSlots,
                    "Removing the other provider's modifier left a residual slot");

            helper.succeed();
        } finally {
            level.players().remove(wearer);
            wearer.discard();
        }
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        level.players().add(player);
        return player;
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

        @Override
        public void sendSystemMessage(Component message) {
            // GameTest players intentionally have no network connection.
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
