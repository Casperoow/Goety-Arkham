package com.casper.goetyarkham.curios;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.mojang.authlib.GameProfile;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.attribute.ModAttributes;
import com.casper.goetyarkham.illager_treachery.GrotesqueStatueProtectionService;
import com.casper.goetyarkham.item.ElderSignAmuletItem;
import com.casper.goetyarkham.item.GrotesqueStatueItem;
import com.casper.goetyarkham.item.HolyRosaryItem;
import com.casper.goetyarkham.item.BulletproofVestItem;
import com.casper.goetyarkham.item.LeatherCoatItem;
import com.casper.goetyarkham.item.CurioTooltipHelper;
import com.casper.goetyarkham.item.DiscOfItzamnaEffectService;
import com.casper.goetyarkham.item.DiscOfItzamnaItem;
import com.casper.goetyarkham.item.ModCreativeModeTabs;
import com.casper.goetyarkham.item.ModItems;
import com.casper.goetyarkham.item.RabbitFootItem;
import com.casper.goetyarkham.sanity.SanityChangeCause;
import com.casper.goetyarkham.sanity.SanityMath;
import com.casper.goetyarkham.sanity.SanityService;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.soul.SoulStorageTooltip;
import com.casper.goetyarkham.stats.EquipmentStatsService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CurioSlotGameTests {
    private CurioSlotGameTests() {
    }

    @GameTest(template = "empty")
    public static void baseSlotsAreRegisteredAndBoundToPlayers(GameTestHelper helper) {
        Map<String, ISlotType> registeredSlots = CuriosApi.getSlots(helper.getLevel());
        Map<String, ISlotType> playerSlots = CuriosApi.getEntitySlots(
                EntityType.PLAYER,
                helper.getLevel()
        );

        CurioSlotIds.BASE_SIZES.forEach((slotId, minimumSize) -> {
            ISlotType registeredSlot = registeredSlots.get(slotId);
            helper.assertTrue(
                    registeredSlot != null,
                    "Missing registered Curios slot: " + slotId
            );
            helper.assertTrue(
                    registeredSlot.getSize() >= minimumSize,
                    "Curios slot " + slotId + " has size " + registeredSlot.getSize()
                            + ", expected at least " + minimumSize
            );

            ISlotType playerSlot = playerSlots.get(slotId);
            helper.assertTrue(
                    playerSlot != null,
                    "Curios slot is not bound to minecraft:player: " + slotId
            );
            helper.assertTrue(
                    playerSlot.getSize() >= minimumSize,
                    "Player Curios slot " + slotId + " has size " + playerSlot.getSize()
                            + ", expected at least " + minimumSize
            );
        });

        List<ISlotType> runtimeSlots = CurioSlotReport.playerSlots(helper.getLevel());
        String slotSummary = runtimeSlots.stream()
                .map(slot -> slot.getIdentifier()
                        + "=" + slot.getSize()
                        + "@order:" + slot.getOrder()
                        + "@" + slot.getIcon())
                .collect(Collectors.joining(", "));
        GoetyArkham.LOGGER.info(
                "[CurioSlotGameTest] Complete runtime player slots (size@order@icon): {}",
                slotSummary
        );
        for (ISlotType slot : runtimeSlots) {
            String translationKey = CurioSlotReport.translationKey(slot);
            if (Language.getInstance().has(translationKey)) {
                GoetyArkham.LOGGER.info(
                        "[CurioSlotGameTest] Runtime slot: id={}, name={}, size={}, order={}",
                        slot.getIdentifier(),
                        Language.getInstance().getOrDefault(translationKey),
                        slot.getSize(),
                        slot.getOrder());
            } else {
                GoetyArkham.LOGGER.warn(
                        "[CurioSlotGameTest] Runtime slot: id={}, missing_translation={}, size={}, order={}",
                        slot.getIdentifier(), translationKey,
                        slot.getSize(), slot.getOrder());
            }
        }
        verifyGrotesqueStatueRuntimeWiring(helper);
        verifyDiscRuntimeWiring(helper);
        verifyHolyRosaryRuntimeWiring(helper);
        verifyRabbitFootRuntimeWiring(helper);
        verifyElderSignAmuletRuntimeWiring(helper);
        verifyLeatherCoatRuntimeWiring(helper);
        verifyBulletproofVestRuntimeWiring(helper);
        verifyCreativeTabContents(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grotesqueStatueUsesUnifiedSoulPool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PoolTestPlayer player = new PoolTestPlayer(level, "statue-pool-equipped");
        PoolTestPlayer inventoryOnly = new PoolTestPlayer(level, "statue-pool-inventory");
        level.players().add(player);
        level.players().add(inventoryOnly);
        try {
            var charm = CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(
                            CurioSlotIds.CHARM))
                    .orElse(null);
            helper.assertTrue(charm != null,
                    "Test player is missing the charm Curios handler");

            ItemStack emptyStatue = new ItemStack(ModItems.GROTESQUE_STATUE.get());
            ModItems.GROTESQUE_STATUE.get().setTagTick(emptyStatue);
            charm.getStacks().setStackInSlot(0, emptyStatue);
            player.getInventory().setItem(0, soulTotem(5_000));
            SoulEnergyPoolService.refresh(player);
            helper.assertTrue(ITotem.currentSouls(emptyStatue) == 0,
                    "Equipped test statue must start empty");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(player) == 5_000,
                    "Unified test pool did not include the separate soul Totem");

            GrotesqueStatueProtectionService.EventSession event =
                    GrotesqueStatueProtectionService.beginEvent();
            helper.assertTrue(event.tryProtect(player),
                    "Empty equipped statue did not use the funded unified pool");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(player) == 4_000,
                    "Unified pool did not pay exactly 1000 souls");
            helper.assertTrue(ITotem.currentSouls(emptyStatue) == 0,
                    "Protection directly changed the empty statue instead of pool order");
            helper.assertTrue(ModItems.GROTESQUE_STATUE.get()
                            .getBarWidth(emptyStatue) == 0,
                    "Statue bar displayed the funded unified pool instead of stack soul");
            helper.assertTrue(event.tryProtect(player),
                    "Repeated event query did not retain personal immunity");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(player) == 4_000,
                    "Repeated event query charged the unified pool twice");

            SoulEnergyPoolService.setSoul(player, 999);
            int underfundedBefore = SoulEnergyPoolService.getCurrentSoul(player);
            helper.assertTrue(!GrotesqueStatueProtectionService.beginEvent()
                            .tryProtect(player),
                    "999-soul unified pool incorrectly granted protection");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(player)
                            == underfundedBefore,
                    "Underfunded unified pool was partially charged");

            SoulEnergyPoolService.setSoul(player, 1_000);
            helper.assertTrue(GrotesqueStatueProtectionService.beginEvent()
                            .tryProtect(player),
                    "Exactly 1000 unified souls did not grant protection");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(player) == 0,
                    "Exact unified-pool payment did not leave zero souls");

            ItemStack secondStatue = new ItemStack(ModItems.GROTESQUE_STATUE.get());
            ModItems.GROTESQUE_STATUE.get().setTagTick(secondStatue);
            charm.getStacks().setStackInSlot(1, secondStatue);
            SoulEnergyPoolService.setSoul(player, 5_000);
            event = GrotesqueStatueProtectionService.beginEvent();
            helper.assertTrue(event.tryProtect(player),
                    "Two equipped statues failed to grant one protection");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(player) == 4_000,
                    "Two equipped statues charged more than one fixed cost");
            helper.assertTrue(ITotem.currentSouls(emptyStatue) == 4_000
                            && ModItems.GROTESQUE_STATUE.get()
                            .getBarWidth(emptyStatue) == 10,
                    "Statue bar did not follow its stack after unified pool order charged it");

            ItemStack inventoryStatue = new ItemStack(ModItems.GROTESQUE_STATUE.get());
            ModItems.GROTESQUE_STATUE.get().setTagTick(inventoryStatue);
            ITotem.setSoulsamount(inventoryStatue, 5_000);
            inventoryOnly.getInventory().setItem(0, inventoryStatue);
            SoulEnergyPoolService.refresh(inventoryOnly);
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(inventoryOnly)
                            == 5_000,
                    "Hotbar statue did not retain normal unified-pool participation");
            helper.assertTrue(!GrotesqueStatueProtectionService.beginEvent()
                            .tryProtect(inventoryOnly),
                    "Unequipped hotbar statue incorrectly granted protection");
            helper.assertTrue(SoulEnergyPoolService.getCurrentSoul(inventoryOnly)
                            == 5_000,
                    "Unequipped statue caused an invalid pool charge");
            helper.assertTrue(player.protectionMessages == 3,
                    "Successful protections did not emit exactly one message each");
            helper.succeed();
        } finally {
            level.players().remove(player);
            level.players().remove(inventoryOnly);
            player.discard();
            inventoryOnly.discard();
        }
    }

    @GameTest(template = "empty")
    public static void resourceSlotIsHiddenUntilDynamicallyGranted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer wearer = new PoolTestPlayer(level, "resource-slot-wearer");
        level.players().add(wearer);
        UUID modifierId = UUID.randomUUID();
        try {
            var resource = CuriosApi.getCuriosInventory(wearer).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.RESOURCE))
                    .orElse(null);
            helper.assertTrue(resource != null,
                    "Test wearer is missing the resource Curios handler");
            helper.assertTrue(resource.getSlots() == 0,
                    "resource slot must start hidden with zero capacity");

            var hands = CuriosApi.getCuriosInventory(wearer).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(CurioSlotIds.HANDS))
                    .orElse(null);
            helper.assertTrue(hands != null,
                    "Test wearer is missing the hands Curios handler");
            hands.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.HOLY_ROSARY.get()));
            settleCurioChange(wearer);

            DynamicCurioSlotContributionService.reconcile(
                    wearer,
                    CurioSlotIds.RESOURCE,
                    modifierId,
                    "goetyarkham:test_resource_provider",
                    ModItems.HOLY_ROSARY,
                    List.of(CurioSlotIds.HANDS),
                    1.0D);
            helper.assertTrue(resource.getSlots() == 1,
                    "resource slot did not become visible once a dynamic modifier"
                            + " granted capacity");

            resource.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.HOLY_ROSARY.get()));
            helper.assertTrue(!resource.getStacks().getStackInSlot(0).isEmpty(),
                    "Could not place an item into the dynamically granted resource slot");

            int inventoryCountBefore = countMatchingStacks(
                    wearer, ModItems.HOLY_ROSARY.get());

            hands.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            DynamicCurioSlotContributionService.reconcile(
                    wearer,
                    CurioSlotIds.RESOURCE,
                    modifierId,
                    "goetyarkham:test_resource_provider",
                    ModItems.HOLY_ROSARY,
                    List.of(CurioSlotIds.HANDS),
                    1.0D);

            helper.assertTrue(resource.getSlots() == 0,
                    "resource slot did not hide again once the dynamic modifier"
                            + " was removed");
            int inventoryCountAfter = countMatchingStacks(
                    wearer, ModItems.HOLY_ROSARY.get());
            helper.assertTrue(inventoryCountAfter == inventoryCountBefore + 1,
                    "Item left in the shrinking resource slot was not safely"
                            + " returned to the player");

            helper.succeed();
        } finally {
            level.players().remove(wearer);
            wearer.discard();
        }
    }

    private static int countMatchingStacks(
            ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void verifyGrotesqueStatueRuntimeWiring(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "grotesque_statue");
        helper.assertTrue(
                expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.GROTESQUE_STATUE.get())),
                "Grotesque Statue registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.GROTESQUE_STATUE.get());
        GrotesqueStatueItem item = ModItems.GROTESQUE_STATUE.get();
        item.setTagTick(stack);
        helper.assertTrue(ITotem.currentSouls(stack) == 0,
                "Grotesque Statue must start with zero souls");
        helper.assertTrue(ITotem.maximumSouls(stack) == GrotesqueStatueItem.MAX_SOULS,
                "Grotesque Statue maximum soul NBT mismatch");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(!tooltip.isEmpty(),
                "Grotesque Statue soul tooltip is missing");
        helper.assertTrue(tooltip.get(0).getContents() instanceof TranslatableContents contents
                        && SoulStorageTooltip.TRANSLATION_KEY.equals(contents.getKey()),
                "Grotesque Statue does not reuse Goety's soul tooltip key");
        helper.assertTrue(tooltip.get(0).getStyle().isEmpty(),
                "Grotesque Statue applies an extra style to Goety's soul tooltip");

        verifyGrotesqueStatueSoulBar(helper, item, stack);

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(java.util.Set.of(
                        CurioSlotIds.CHARM)),
                "Grotesque Statue item tag must expose only the charm slot");

        ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
        helper.assertTrue(curio != null,
                "Grotesque Statue Curios capability was not registered");
        LivingEntity wearer = EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(wearer != null, "Could not create Curios test wearer");
        helper.assertTrue(curio.canEquip(new SlotContext(
                        CurioSlotIds.CHARM, wearer, 0, false, true)),
                "Grotesque Statue rejected the charm slot");
        helper.assertTrue(!curio.canEquip(new SlotContext(
                        CurioSlotIds.NECKLACE, wearer, 0, false, true)),
                "Grotesque Statue accepted the necklace slot");
    }

    private static void verifyDiscRuntimeWiring(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "disc_of_itzamna");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.DISC_OF_ITZAMNA.get())),
                "Disc of Itzamna registry ID mismatch");

        ItemStack stack = new ItemStack(ModItems.DISC_OF_ITZAMNA.get());
        DiscOfItzamnaItem item = ModItems.DISC_OF_ITZAMNA.get();
        helper.assertTrue(stack.getMaxDamage()
                        == DiscOfItzamnaItem.MAX_DURABILITY,
                "Disc of Itzamna maximum durability mismatch");
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Disc of Itzamna must not stack");
        helper.assertTrue(Enchantments.UNBREAKING.canEnchant(stack),
                "Disc of Itzamna rejected the vanilla Unbreaking enchantment");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 2,
                "Disc of Itzamna tooltip line count mismatch");
        helper.assertTrue(tooltip.get(0).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY
                        .equals(heading.getKey()),
                "Disc of Itzamna does not use the shared when-worn heading");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Disc of Itzamna when-worn heading is not yellow");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "Disc of Itzamna effect body is not gray");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(
                        CurioSlotIds.CHARM)),
                "Disc of Itzamna item tag must expose only the charm slot");

        ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
        helper.assertTrue(curio != null,
                "Disc of Itzamna Curios capability was not registered");
        ServerPlayer wearer = new PoolTestPlayer(
                helper.getLevel(), "disc-slot-wearer");
        try {
            helper.assertTrue(curio.canEquip(new SlotContext(
                            CurioSlotIds.CHARM, wearer, 0, false, true)),
                    "Disc of Itzamna rejected the charm slot");
            helper.assertTrue(!curio.canEquip(new SlotContext(
                            CurioSlotIds.NECKLACE, wearer, 0, false, true)),
                    "Disc of Itzamna accepted the necklace slot");

            wearer.getInventory().setItem(0, stack.copy());
            helper.assertTrue(
                    !DiscOfItzamnaEffectService.isWearingActiveDisc(wearer),
                    "Inventory-only Disc of Itzamna incorrectly activated");

            var charm = CuriosApi.getCuriosInventory(wearer).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(
                            CurioSlotIds.CHARM))
                    .orElse(null);
            helper.assertTrue(charm != null,
                    "Disc test wearer is missing the charm Curios handler");
            charm.getStacks().setStackInSlot(0, stack.copy());
            helper.assertTrue(
                    DiscOfItzamnaEffectService.isWearingActiveDisc(wearer),
                    "Charm-equipped Disc of Itzamna did not activate");
        } finally {
            wearer.discard();
        }
    }

    private static void verifyHolyRosaryRuntimeWiring(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "holy_rosary");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.HOLY_ROSARY.get())),
                "Holy Rosary registry ID mismatch");

        HolyRosaryItem item = ModItems.HOLY_ROSARY.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Holy Rosary must not stack");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 3,
                "Holy Rosary tooltip line count mismatch");
        helper.assertTrue(tooltip.get(0).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY
                        .equals(heading.getKey()),
                "Holy Rosary does not use the shared when-worn heading");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Holy Rosary when-worn heading is not yellow");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(1).getStyle().getColor())
                        && TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(2).getStyle().getColor()),
                "Holy Rosary effect lines are not gray");
        helper.assertTrue("When worn:".equals(tooltip.get(0).getString())
                        && "+2 Max Sanity".equals(tooltip.get(1).getString())
                        && "+1 Will".equals(tooltip.get(2).getString()),
                "Holy Rosary English tooltip text mismatch");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(
                        CurioSlotIds.HANDS)),
                "Holy Rosary item tag must expose only the hands slot");

        ServerPlayer wearer = new PoolTestPlayer(
                helper.getLevel(), "holy-rosary-wearer");
        helper.getLevel().players().add(wearer);
        try {
            ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
            helper.assertTrue(curio != null,
                    "Holy Rosary Curios capability was not registered");
            SlotContext firstHand = new SlotContext(
                    CurioSlotIds.HANDS, wearer, 0, false, true);
            SlotContext secondHand = new SlotContext(
                    CurioSlotIds.HANDS, wearer, 1, false, true);
            helper.assertTrue(curio.canEquip(firstHand),
                    "Holy Rosary rejected the hands slot");
            helper.assertTrue(!curio.canEquip(new SlotContext(
                            CurioSlotIds.NECKLACE, wearer, 0, false, true)),
                    "Holy Rosary accepted a non-hands slot");

            var firstModifiers = curio.getAttributeModifiers(
                    firstHand, UUID.randomUUID());
            var secondModifiers = curio.getAttributeModifiers(
                    secondHand, UUID.randomUUID());
            AttributeModifier firstSanity = firstModifiers.get(
                    ModAttributes.MAX_SANITY.get()).stream().findFirst().orElse(null);
            AttributeModifier secondSanity = secondModifiers.get(
                    ModAttributes.MAX_SANITY.get()).stream().findFirst().orElse(null);
            helper.assertTrue(firstSanity != null
                            && firstSanity.getAmount() == HolyRosaryItem.MAX_SANITY_BONUS
                            && firstSanity.getOperation()
                            == AttributeModifier.Operation.ADDITION,
                    "Holy Rosary maximum-sanity modifier mismatch");
            helper.assertTrue(secondSanity != null
                            && !firstSanity.getId().equals(secondSanity.getId()),
                    "Two hands slots do not receive distinct sanity modifier UUIDs");

            var hands = CuriosApi.getCuriosInventory(wearer).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(
                            CurioSlotIds.HANDS))
                    .orElse(null);
            helper.assertTrue(hands != null,
                    "Holy Rosary test wearer is missing the hands handler");
            int baseWill = PlayerStatsService.get(wearer)
                    .map(stats -> stats.get(StatType.WILLPOWER).base())
                    .orElse(0);
            double baseMaximum = SanityService.getMaximumAttributeValue(wearer);

            hands.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWill + 1,
                    "One Holy Rosary did not add exactly one final Will");
            helper.assertTrue(SanityService.getMaximumAttributeValue(wearer)
                            == baseMaximum + 2.0D,
                    "One Holy Rosary did not add exactly two maximum sanity");
            helper.assertTrue(SanityService.addPermanentMaxLoss(
                            wearer, 3, SanityChangeCause.COMMAND) == 3,
                    "Holy Rosary test could not apply permanent sanity loss");
            helper.assertTrue(SanityService.getMaximumSanity(wearer)
                            == SanityMath.maximumSanity(
                            baseMaximum + HolyRosaryItem.MAX_SANITY_BONUS, 3),
                    "Holy Rosary maximum bonus bypassed permanent sanity loss");

            hands.getStacks().setStackInSlot(1, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWill + 2,
                    "Two Holy Rosaries did not stack final Will");
            helper.assertTrue(SanityService.getMaximumAttributeValue(wearer)
                            == baseMaximum + 4.0D,
                    "Two Holy Rosaries did not stack maximum sanity");
            EquipmentStatsService.refresh(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWill + 2,
                    "Repeated equipment refresh duplicated Will");

            int twoRosaryMaximum = SanityService.getMaximumSanity(wearer);
            SanityService.setSanity(
                    wearer, twoRosaryMaximum, SanityChangeCause.COMMAND);
            hands.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWill + 1,
                    "Unequipping one Holy Rosary did not remove one final Will");
            helper.assertTrue(SanityService.getMaximumAttributeValue(wearer)
                            == baseMaximum + 2.0D,
                    "Unequipping one Holy Rosary did not remove two maximum sanity");
            helper.assertTrue(SanityService.getCurrentSanity(wearer)
                            <= SanityService.getMaximumSanity(wearer),
                    "Current sanity exceeded the lowered effective maximum");
            helper.assertTrue(SanityService.getPermanentMaxLoss(wearer) == 3,
                    "Holy Rosary changed permanent sanity loss");
            helper.assertTrue(PlayerStatsService.get(wearer)
                            .map(stats -> stats.get(StatType.WILLPOWER).base())
                            .orElse(0) == baseWill,
                    "Holy Rosary changed stored base Will");
        } finally {
            helper.getLevel().players().remove(wearer);
            wearer.discard();
        }
    }

    private static void verifyRabbitFootRuntimeWiring(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "rabbit_foot");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.RABBIT_FOOT.get())),
                "Rabbit's Foot registry ID mismatch");

        RabbitFootItem item = ModItems.RABBIT_FOOT.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Rabbit's Foot must not stack");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 6,
                "Rabbit's Foot tooltip line count mismatch");
        helper.assertTrue(tooltip.get(0).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY
                        .equals(heading.getKey()),
                "Rabbit's Foot does not use the shared when-worn heading");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Rabbit's Foot when-worn heading is not yellow");
        for (int i = 1; i <= 5; i++) {
            helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                            .equals(tooltip.get(i).getStyle().getColor()),
                    "Rabbit's Foot effect line " + i + " is not gray");
        }
        helper.assertTrue("When worn:".equals(tooltip.get(0).getString())
                        && "+1 Strength".equals(tooltip.get(1).getString())
                        && "+1 Agility".equals(tooltip.get(2).getString())
                        && "+1 Will".equals(tooltip.get(3).getString())
                        && "+1 Intellect".equals(tooltip.get(4).getString())
                        && "+1 Luck".equals(tooltip.get(5).getString()),
                "Rabbit's Foot English tooltip text mismatch");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(
                        CurioSlotIds.NECKLACE)),
                "Rabbit's Foot item tag must expose only the necklace slot");

        ServerPlayer wearer = new PoolTestPlayer(
                helper.getLevel(), "rabbit-foot-wearer");
        helper.getLevel().players().add(wearer);
        try {
            ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
            helper.assertTrue(curio != null,
                    "Rabbit's Foot Curios capability was not registered");
            SlotContext firstNecklace = new SlotContext(
                    CurioSlotIds.NECKLACE, wearer, 0, false, true);
            SlotContext secondNecklace = new SlotContext(
                    CurioSlotIds.NECKLACE, wearer, 1, false, true);
            SlotContext cosmeticNecklace = new SlotContext(
                    CurioSlotIds.NECKLACE, wearer, 0, true, true);
            helper.assertTrue(curio.canEquip(firstNecklace),
                    "Rabbit's Foot rejected the necklace slot");
            helper.assertTrue(!curio.canEquip(new SlotContext(
                            CurioSlotIds.HANDS, wearer, 0, false, true)),
                    "Rabbit's Foot accepted a non-necklace slot");

            for (StatType stat : StatType.values()) {
                helper.assertTrue(item.getEquipmentStatModifier(
                                stat, cosmeticNecklace, stack) == 0,
                        "Cosmetic necklace slot granted a " + stat + " bonus");
            }
            helper.assertTrue(item.getAttributeModifiers(
                            cosmeticNecklace, UUID.randomUUID(), stack).isEmpty(),
                    "Cosmetic necklace slot granted a Luck bonus");

            var firstModifiers = curio.getAttributeModifiers(
                    firstNecklace, UUID.randomUUID());
            var secondModifiers = curio.getAttributeModifiers(
                    secondNecklace, UUID.randomUUID());
            AttributeModifier firstLuck = firstModifiers.get(
                    Attributes.LUCK).stream().findFirst().orElse(null);
            AttributeModifier secondLuck = secondModifiers.get(
                    Attributes.LUCK).stream().findFirst().orElse(null);
            helper.assertTrue(firstLuck != null
                            && firstLuck.getAmount() == RabbitFootItem.LUCK_BONUS
                            && firstLuck.getOperation()
                            == AttributeModifier.Operation.ADDITION,
                    "Rabbit's Foot Luck modifier mismatch");
            helper.assertTrue(secondLuck != null
                            && !firstLuck.getId().equals(secondLuck.getId()),
                    "Two necklace slots do not receive distinct Luck modifier UUIDs");

            var necklace = CuriosApi.getCuriosInventory(wearer).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(
                            CurioSlotIds.NECKLACE))
                    .orElse(null);
            helper.assertTrue(necklace != null,
                    "Rabbit's Foot test wearer is missing the necklace handler");

            int baseStrength = PlayerStatsService.getFinalValue(
                    wearer, StatType.STRENGTH);
            int baseAgility = PlayerStatsService.getFinalValue(
                    wearer, StatType.AGILITY);
            int baseWillpower = PlayerStatsService.getFinalValue(
                    wearer, StatType.WILLPOWER);
            int baseIntellect = PlayerStatsService.getFinalValue(
                    wearer, StatType.INTELLECT);
            double baseLuck = wearer.getAttribute(Attributes.LUCK).getValue();

            necklace.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.STRENGTH) == baseStrength + 1,
                    "One Rabbit's Foot did not add exactly one final Strength");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.AGILITY) == baseAgility + 1,
                    "One Rabbit's Foot did not add exactly one final Agility");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWillpower + 1,
                    "One Rabbit's Foot did not add exactly one final Will");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.INTELLECT) == baseIntellect + 1,
                    "One Rabbit's Foot did not add exactly one final Intellect");
            helper.assertTrue(wearer.getAttribute(Attributes.LUCK).getValue()
                            == baseLuck + 1.0D,
                    "One Rabbit's Foot did not add exactly one Luck");

            necklace.getStacks().setStackInSlot(1, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.STRENGTH) == baseStrength + 2,
                    "Two Rabbit's Feet did not stack final Strength");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.AGILITY) == baseAgility + 2,
                    "Two Rabbit's Feet did not stack final Agility");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWillpower + 2,
                    "Two Rabbit's Feet did not stack final Will");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.INTELLECT) == baseIntellect + 2,
                    "Two Rabbit's Feet did not stack final Intellect");
            helper.assertTrue(wearer.getAttribute(Attributes.LUCK).getValue()
                            == baseLuck + 2.0D,
                    "Two Rabbit's Feet did not stack Luck");

            EquipmentStatsService.refresh(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.STRENGTH) == baseStrength + 2,
                    "Repeated equipment refresh duplicated Strength");
            helper.assertTrue(wearer.getAttribute(Attributes.LUCK).getValue()
                            == baseLuck + 2.0D,
                    "Repeated equipment refresh duplicated Luck");

            necklace.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.STRENGTH) == baseStrength + 1,
                    "Unequipping one Rabbit's Foot did not remove one Strength");
            helper.assertTrue(wearer.getAttribute(Attributes.LUCK).getValue()
                            == baseLuck + 1.0D,
                    "Unequipping one Rabbit's Foot did not remove one Luck");

            necklace.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.STRENGTH) == baseStrength,
                    "Unequipping the last Rabbit's Foot left a residual Strength bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.AGILITY) == baseAgility,
                    "Unequipping the last Rabbit's Foot left a residual Agility bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.WILLPOWER) == baseWillpower,
                    "Unequipping the last Rabbit's Foot left a residual Will bonus");
            helper.assertTrue(PlayerStatsService.getFinalValue(
                            wearer, StatType.INTELLECT) == baseIntellect,
                    "Unequipping the last Rabbit's Foot left a residual Intellect bonus");
            helper.assertTrue(wearer.getAttribute(Attributes.LUCK).getValue()
                            == baseLuck,
                    "Unequipping the last Rabbit's Foot left a residual Luck bonus");
            helper.assertTrue(PlayerStatsService.get(wearer)
                            .map(stats -> stats.get(StatType.STRENGTH).base())
                            .orElse(0) == baseStrength,
                    "Rabbit's Foot changed stored base Strength");
        } finally {
            helper.getLevel().players().remove(wearer);
            wearer.discard();
        }
    }

    private static void verifyElderSignAmuletRuntimeWiring(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "elder_sign_amulet");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.ELDER_SIGN_AMULET.get())),
                "Elder Sign Amulet registry ID mismatch");

        ElderSignAmuletItem item = ModItems.ELDER_SIGN_AMULET.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Elder Sign Amulet must not stack");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 3,
                "Elder Sign Amulet tooltip line count mismatch");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Elder Sign Amulet slot line is not gray");
        helper.assertTrue(tooltip.get(1).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY
                        .equals(heading.getKey()),
                "Elder Sign Amulet does not use the shared when-worn heading");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "Elder Sign Amulet when-worn heading is not yellow");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(2).getStyle().getColor()),
                "Elder Sign Amulet effect line is not gray");
        helper.assertTrue("Slot: Necklace".equals(tooltip.get(0).getString())
                        && "When worn:".equals(tooltip.get(1).getString())
                        && "+4 Max Sanity".equals(tooltip.get(2).getString()),
                "Elder Sign Amulet English tooltip text mismatch");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(
                        CurioSlotIds.NECKLACE)),
                "Elder Sign Amulet item tag must expose only the necklace slot");

        ServerPlayer wearer = new PoolTestPlayer(
                helper.getLevel(), "elder-sign-amulet-wearer");
        helper.getLevel().players().add(wearer);
        try {
            ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
            helper.assertTrue(curio != null,
                    "Elder Sign Amulet Curios capability was not registered");
            SlotContext firstNecklace = new SlotContext(
                    CurioSlotIds.NECKLACE, wearer, 0, false, true);
            SlotContext secondNecklace = new SlotContext(
                    CurioSlotIds.NECKLACE, wearer, 1, false, true);
            SlotContext cosmeticNecklace = new SlotContext(
                    CurioSlotIds.NECKLACE, wearer, 0, true, true);
            helper.assertTrue(curio.canEquip(firstNecklace),
                    "Elder Sign Amulet rejected the necklace slot");
            helper.assertTrue(!curio.canEquip(new SlotContext(
                            CurioSlotIds.HANDS, wearer, 0, false, true)),
                    "Elder Sign Amulet accepted a non-necklace slot");
            helper.assertTrue(item.getAttributeModifiers(
                            cosmeticNecklace, UUID.randomUUID(), stack).isEmpty(),
                    "Cosmetic necklace slot granted a maximum-sanity bonus");

            var firstModifiers = curio.getAttributeModifiers(
                    firstNecklace, UUID.randomUUID());
            var secondModifiers = curio.getAttributeModifiers(
                    secondNecklace, UUID.randomUUID());
            AttributeModifier firstSanity = firstModifiers.get(
                    ModAttributes.MAX_SANITY.get()).stream().findFirst().orElse(null);
            AttributeModifier secondSanity = secondModifiers.get(
                    ModAttributes.MAX_SANITY.get()).stream().findFirst().orElse(null);
            helper.assertTrue(firstSanity != null
                            && firstSanity.getAmount()
                            == ElderSignAmuletItem.MAX_SANITY_BONUS
                            && firstSanity.getOperation()
                            == AttributeModifier.Operation.ADDITION,
                    "Elder Sign Amulet maximum-sanity modifier mismatch");
            helper.assertTrue(secondSanity != null
                            && !firstSanity.getId().equals(secondSanity.getId()),
                    "Two necklace slots do not receive distinct sanity modifier UUIDs");
            AttributeModifier repeatSanity = curio.getAttributeModifiers(
                            firstNecklace, UUID.randomUUID())
                    .get(ModAttributes.MAX_SANITY.get())
                    .stream().findFirst().orElse(null);
            helper.assertTrue(repeatSanity != null
                            && repeatSanity.getId().equals(firstSanity.getId()),
                    "Refreshing the same necklace slot changed the sanity modifier UUID");

            var necklace = CuriosApi.getCuriosInventory(wearer).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(
                            CurioSlotIds.NECKLACE))
                    .orElse(null);
            helper.assertTrue(necklace != null,
                    "Elder Sign Amulet test wearer is missing the necklace handler");

            double baseMaximum = SanityService.getMaximumAttributeValue(wearer);
            int baseMaximumSanity = SanityService.getMaximumSanity(wearer);
            SanityService.setSanity(
                    wearer, baseMaximumSanity, SanityChangeCause.COMMAND);

            necklace.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(SanityService.getMaximumAttributeValue(wearer)
                            == baseMaximum + ElderSignAmuletItem.MAX_SANITY_BONUS,
                    "Elder Sign Amulet did not add exactly four maximum sanity");
            helper.assertTrue(SanityService.getMaximumSanity(wearer)
                            == baseMaximumSanity + ElderSignAmuletItem.MAX_SANITY_BONUS,
                    "Elder Sign Amulet did not raise the effective maximum by four");
            helper.assertTrue(SanityService.getCurrentSanity(wearer)
                            == baseMaximumSanity,
                    "Equipping Elder Sign Amulet incorrectly restored lost sanity");

            EquipmentStatsService.refresh(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(SanityService.getMaximumAttributeValue(wearer)
                            == baseMaximum + ElderSignAmuletItem.MAX_SANITY_BONUS,
                    "Repeated equipment refresh duplicated the Elder Sign Amulet bonus");

            int equippedMaximum = SanityService.getMaximumSanity(wearer);
            SanityService.setSanity(
                    wearer, equippedMaximum, SanityChangeCause.COMMAND);
            helper.assertTrue(SanityService.getCurrentSanity(wearer) == equippedMaximum,
                    "Test setup could not fill current sanity to the equipped maximum");

            helper.assertTrue(SanityService.getPermanentMaxLoss(wearer) == 0,
                    "Test setup unexpectedly has permanent sanity loss");

            necklace.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(SanityService.getMaximumAttributeValue(wearer)
                            == baseMaximum,
                    "Unequipping Elder Sign Amulet did not remove the"
                            + " maximum-sanity bonus");
            helper.assertTrue(SanityService.getMaximumSanity(wearer)
                            == baseMaximumSanity,
                    "Unequipping Elder Sign Amulet left a residual"
                            + " maximum-sanity bonus");
            helper.assertTrue(SanityService.getCurrentSanity(wearer)
                            <= SanityService.getMaximumSanity(wearer),
                    "Current sanity exceeded the lowered effective maximum"
                            + " after unequip");
            helper.assertTrue(SanityService.getCurrentSanity(wearer)
                            == baseMaximumSanity,
                    "Unequipping Elder Sign Amulet did not clamp current sanity"
                            + " down to the new maximum");
            helper.assertTrue(SanityService.getPermanentMaxLoss(wearer) == 0,
                    "Elder Sign Amulet changed permanent sanity loss");
        } finally {
            helper.getLevel().players().remove(wearer);
            wearer.discard();
        }
    }

    private static void verifyLeatherCoatRuntimeWiring(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "leather_coat");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.LEATHER_COAT.get())),
                "Leather Coat registry ID mismatch");

        LeatherCoatItem item = ModItems.LEATHER_COAT.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Leather Coat must not stack");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 3,
                "Leather Coat tooltip line count mismatch");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Leather Coat slot line is not gray");
        helper.assertTrue(tooltip.get(1).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY
                        .equals(heading.getKey()),
                "Leather Coat does not use the shared when-worn heading");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "Leather Coat when-worn heading is not yellow");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(2).getStyle().getColor()),
                "Leather Coat effect line is not gray");
        helper.assertTrue("Slot: Body".equals(tooltip.get(0).getString())
                        && "When worn:".equals(tooltip.get(1).getString())
                        && "+20% Max Health".equals(tooltip.get(2).getString()),
                "Leather Coat English tooltip text mismatch");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(
                        CurioSlotIds.BODY)),
                "Leather Coat item tag must expose only the body slot");

        ServerPlayer wearer = new PoolTestPlayer(
                helper.getLevel(), "leather-coat-wearer");
        helper.getLevel().players().add(wearer);
        UUID otherEffectId = UUID.randomUUID();
        try {
            ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
            helper.assertTrue(curio != null,
                    "Leather Coat Curios capability was not registered");
            SlotContext firstBody = new SlotContext(
                    CurioSlotIds.BODY, wearer, 0, false, true);
            SlotContext cosmeticBody = new SlotContext(
                    CurioSlotIds.BODY, wearer, 0, true, true);
            helper.assertTrue(curio.canEquip(firstBody),
                    "Leather Coat rejected the body slot");
            helper.assertTrue(!curio.canEquip(new SlotContext(
                            CurioSlotIds.NECKLACE, wearer, 0, false, true)),
                    "Leather Coat accepted a non-body slot");
            helper.assertTrue(item.getAttributeModifiers(
                            cosmeticBody, UUID.randomUUID(), stack).isEmpty(),
                    "Cosmetic body slot granted a maximum-health bonus");

            var firstModifiers = curio.getAttributeModifiers(
                    firstBody, UUID.randomUUID());
            AttributeModifier firstHealth = firstModifiers.get(
                    Attributes.MAX_HEALTH).stream().findFirst().orElse(null);
            helper.assertTrue(firstHealth != null
                            && firstHealth.getAmount()
                            == LeatherCoatItem.MAX_HEALTH_MULTIPLIER
                            && firstHealth.getOperation()
                            == AttributeModifier.Operation.MULTIPLY_TOTAL,
                    "Leather Coat maximum-health modifier mismatch");
            AttributeModifier repeatHealth = curio.getAttributeModifiers(
                            firstBody, UUID.randomUUID())
                    .get(Attributes.MAX_HEALTH)
                    .stream().findFirst().orElse(null);
            helper.assertTrue(repeatHealth != null
                            && repeatHealth.getId().equals(firstHealth.getId()),
                    "Refreshing the same body slot changed the max-health modifier UUID");

            var body = CuriosApi.getCuriosInventory(wearer).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(
                            CurioSlotIds.BODY))
                    .orElse(null);
            helper.assertTrue(body != null,
                    "Leather Coat test wearer is missing the body handler");

            double baseMaximum = wearer.getAttribute(Attributes.MAX_HEALTH).getValue();
            helper.assertTrue(baseMaximum == 20.0D,
                    "Test setup expected the default 20 maximum health");
            wearer.setHealth(15.0F);

            body.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaximum * 1.2D,
                    "Leather Coat did not raise 20 maximum health to 24");
            helper.assertTrue(wearer.getHealth() == 15.0F,
                    "Equipping the Leather Coat incorrectly changed current health");

            EquipmentStatsService.refresh(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaximum * 1.2D,
                    "Repeated equipment refresh duplicated the Leather Coat bonus");

            wearer.setHealth((float) wearer.getAttribute(Attributes.MAX_HEALTH).getValue());
            body.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaximum,
                    "Unequipping the Leather Coat did not remove the maximum-health bonus");
            helper.assertTrue(wearer.getHealth() == (float) baseMaximum,
                    "Unequipping the Leather Coat did not clamp current health down"
                            + " to the restored maximum");

            body.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            body.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            body.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            body.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaximum,
                    "Repeated equip/unequip cycles left a residual Leather Coat bonus"
                            + " (28/32-style drift)");

            wearer.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(
                    new AttributeModifier(
                            otherEffectId,
                            "test:other_max_health_effect",
                            20.0D,
                            AttributeModifier.Operation.ADDITION));
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == 40.0D,
                    "Test setup could not raise maximum health to 40 via another effect");

            body.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == 48.0D,
                    "Leather Coat did not raise an already-boosted 40 maximum health to 48");

            body.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == 40.0D,
                    "Unequipping the Leather Coat did not fully remove its 20% bonus"
                            + " on top of another effect");
            wearer.getAttribute(Attributes.MAX_HEALTH).removeModifier(otherEffectId);
        } finally {
            helper.getLevel().players().remove(wearer);
            wearer.discard();
        }
    }

    private static void verifyBulletproofVestRuntimeWiring(GameTestHelper helper) {
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
                GoetyArkham.MOD_ID, "bulletproof_vest");
        helper.assertTrue(expectedId.equals(ForgeRegistries.ITEMS.getKey(
                        ModItems.BULLETPROOF_VEST.get())),
                "Bulletproof Vest registry ID mismatch");

        BulletproofVestItem item = ModItems.BULLETPROOF_VEST.get();
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 1,
                "Bulletproof Vest must not stack");

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, helper.getLevel(), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 3,
                "Bulletproof Vest tooltip line count mismatch");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(0).getStyle().getColor()),
                "Bulletproof Vest slot line is not gray");
        helper.assertTrue(tooltip.get(1).getContents()
                        instanceof TranslatableContents heading
                        && CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY
                        .equals(heading.getKey()),
                "Bulletproof Vest does not use the shared when-worn heading");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW)
                        .equals(tooltip.get(1).getStyle().getColor()),
                "Bulletproof Vest when-worn heading is not yellow");
        helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY)
                        .equals(tooltip.get(2).getStyle().getColor()),
                "Bulletproof Vest effect line is not gray");
        helper.assertTrue("Slot: Body".equals(tooltip.get(0).getString())
                        && "When worn:".equals(tooltip.get(1).getString())
                        && "+40% Max Health".equals(tooltip.get(2).getString()),
                "Bulletproof Vest English tooltip text mismatch");

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(Set.of(
                        CurioSlotIds.BODY)),
                "Bulletproof Vest item tag must expose only the body slot");

        ServerPlayer wearer = new PoolTestPlayer(
                helper.getLevel(), "bulletproof-vest-wearer");
        helper.getLevel().players().add(wearer);
        UUID otherEffectId = UUID.randomUUID();
        try {
            ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
            helper.assertTrue(curio != null,
                    "Bulletproof Vest Curios capability was not registered");
            SlotContext firstBody = new SlotContext(
                    CurioSlotIds.BODY, wearer, 0, false, true);
            SlotContext cosmeticBody = new SlotContext(
                    CurioSlotIds.BODY, wearer, 0, true, true);
            helper.assertTrue(curio.canEquip(firstBody),
                    "Bulletproof Vest rejected the body slot");
            helper.assertTrue(!curio.canEquip(new SlotContext(
                            CurioSlotIds.NECKLACE, wearer, 0, false, true)),
                    "Bulletproof Vest accepted a non-body slot");
            helper.assertTrue(item.getAttributeModifiers(
                            cosmeticBody, UUID.randomUUID(), stack).isEmpty(),
                    "Cosmetic body slot granted a maximum-health bonus");

            var firstModifiers = curio.getAttributeModifiers(
                    firstBody, UUID.randomUUID());
            AttributeModifier firstHealth = firstModifiers.get(
                    Attributes.MAX_HEALTH).stream().findFirst().orElse(null);
            helper.assertTrue(firstHealth != null
                            && firstHealth.getAmount()
                            == BulletproofVestItem.MAX_HEALTH_MULTIPLIER
                            && firstHealth.getOperation()
                            == AttributeModifier.Operation.MULTIPLY_TOTAL,
                    "Bulletproof Vest maximum-health modifier mismatch");
            AttributeModifier repeatHealth = curio.getAttributeModifiers(
                            firstBody, UUID.randomUUID())
                    .get(Attributes.MAX_HEALTH)
                    .stream().findFirst().orElse(null);
            helper.assertTrue(repeatHealth != null
                            && repeatHealth.getId().equals(firstHealth.getId()),
                    "Refreshing the same body slot changed the max-health modifier UUID");

            var body = CuriosApi.getCuriosInventory(wearer).resolve()
                    .flatMap(inventory -> inventory.getStacksHandler(
                            CurioSlotIds.BODY))
                    .orElse(null);
            helper.assertTrue(body != null,
                    "Bulletproof Vest test wearer is missing the body handler");

            double baseMaximum = wearer.getAttribute(Attributes.MAX_HEALTH).getValue();
            helper.assertTrue(baseMaximum == 20.0D,
                    "Test setup expected the default 20 maximum health");
            wearer.setHealth(15.0F);

            body.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaximum * 1.4D,
                    "Bulletproof Vest did not raise 20 maximum health to 28");
            helper.assertTrue(wearer.getHealth() == 15.0F,
                    "Equipping the Bulletproof Vest incorrectly changed current health");

            EquipmentStatsService.refresh(wearer);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaximum * 1.4D,
                    "Repeated equipment refresh duplicated the Bulletproof Vest bonus");

            wearer.setHealth((float) wearer.getAttribute(Attributes.MAX_HEALTH).getValue());
            body.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaximum,
                    "Unequipping the Bulletproof Vest did not remove the maximum-health bonus");
            helper.assertTrue(wearer.getHealth() == (float) baseMaximum,
                    "Unequipping the Bulletproof Vest did not clamp current health down"
                            + " to the restored maximum");

            body.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            body.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            body.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            body.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == baseMaximum,
                    "Repeated equip/unequip cycles left a residual Bulletproof Vest bonus"
                            + " (28/39.2-style drift)");

            wearer.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(
                    new AttributeModifier(
                            otherEffectId,
                            "test:other_max_health_effect",
                            20.0D,
                            AttributeModifier.Operation.ADDITION));
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == 40.0D,
                    "Test setup could not raise maximum health to 40 via another effect");

            body.getStacks().setStackInSlot(0, stack.copy());
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == 56.0D,
                    "Bulletproof Vest did not raise an already-boosted 40 maximum health to 56");

            body.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            settleCurioChange(wearer);
            helper.assertTrue(wearer.getAttribute(Attributes.MAX_HEALTH).getValue()
                            == 40.0D,
                    "Unequipping the Bulletproof Vest did not fully remove its 40% bonus"
                            + " on top of another effect");
            wearer.getAttribute(Attributes.MAX_HEALTH).removeModifier(otherEffectId);
        } finally {
            helper.getLevel().players().remove(wearer);
            wearer.discard();
        }
    }

    private static void settleCurioChange(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static void verifyCreativeTabContents(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(
                helper.getLevel().enabledFeatures(),
                false,
                helper.getLevel().registryAccess()
        );
        Set<net.minecraft.world.item.Item> displayed =
                ModCreativeModeTabs.GOETY_ARKHAM.get().getDisplayItems().stream()
                        .map(ItemStack::getItem)
                        .collect(Collectors.toSet());
        ModItems.ITEMS.getEntries().forEach(entry -> helper.assertTrue(
                displayed.contains(entry.get()),
                "Goety: Arkham creative tab is missing " + entry.getId()));
        helper.assertTrue(ModCreativeModeTabs.GOETY_ARKHAM.get()
                        .getIconItem().is(ModItems.DISC_OF_ITZAMNA.get()),
                "Goety: Arkham creative tab icon is not the Disc of Itzamna");
    }

    private static void verifyGrotesqueStatueSoulBar(
            GameTestHelper helper,
            GrotesqueStatueItem item,
            ItemStack stack) {
        assertSoulBar(helper, item, stack, 0, 0);
        assertSoulBar(helper, item, stack, 1_000, 3);
        assertSoulBar(helper, item, stack, 2_500, 7);
        assertSoulBar(helper, item, stack, 5_000, 13);

        ITotem.setSoulsamount(stack, 0);
        helper.assertTrue(stack.getCount() == 1,
                "Empty soul bar destroyed the Grotesque Statue");
        helper.assertTrue(!stack.isDamageableItem(),
                "Grotesque Statue soul bar became real item durability");
        helper.assertTrue(stack.getTag() != null
                        && !stack.getTag().contains("Damage"),
                "Grotesque Statue soul bar wrote vanilla Damage NBT");
    }

    private static void assertSoulBar(
            GameTestHelper helper,
            GrotesqueStatueItem item,
            ItemStack stack,
            int souls,
            int expectedWidth) {
        ITotem.setSoulsamount(stack, souls);
        helper.assertTrue(item.isBarVisible(stack),
                "Grotesque Statue soul bar is hidden at " + souls);
        helper.assertTrue(item.getBarWidth(stack) == expectedWidth,
                "Grotesque Statue soul bar width mismatch at " + souls);
        int expectedColor = Mth.hsvToRgb(
                (float) (souls / (double) GrotesqueStatueItem.MAX_SOULS) / 2.0F,
                1.0F,
                1.0F);
        helper.assertTrue(item.getBarColor(stack) == expectedColor,
                "Grotesque Statue soul bar color mismatch at " + souls);
    }

    private static ItemStack soulTotem(int souls) {
        ItemStack stack = new ItemStack(
                com.Polarice3.Goety.common.items.ModItems.TOTEM_OF_SOULS.get());
        ((ITotem) stack.getItem()).setTagTick(stack);
        ITotem.setSoulsamount(stack, souls);
        return stack;
    }

    private static final class PoolTestPlayer extends ServerPlayer {
        private int protectionMessages;

        private PoolTestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (message.getContents() instanceof TranslatableContents contents
                    && "message.goetyarkham.grotesque_statue.protected"
                    .equals(contents.getKey())) {
                protectionMessages++;
            }
        }

        @Override
        public void playNotifySound(
                SoundEvent sound,
                SoundSource source,
                float volume,
                float pitch) {
            // Avoid network access for the connection-less GameTest player.
        }
    }
}
