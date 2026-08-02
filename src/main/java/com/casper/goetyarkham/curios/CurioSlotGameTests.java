package com.casper.goetyarkham.curios;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.mojang.authlib.GameProfile;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.illager_treachery.GrotesqueStatueProtectionService;
import com.casper.goetyarkham.item.GrotesqueStatueItem;
import com.casper.goetyarkham.item.ModItems;
import com.casper.goetyarkham.soul.SoulEnergyPoolService;
import com.casper.goetyarkham.soul.SoulStorageTooltip;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
