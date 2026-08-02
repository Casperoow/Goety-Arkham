package com.casper.goetyarkham.curios;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.item.GrotesqueStatueItem;
import com.casper.goetyarkham.item.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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

        String slotSummary = CurioSlotIds.ALL.stream()
                .map(slotId -> {
                    ISlotType slot = registeredSlots.get(slotId);
                    return slotId + "=" + slot.getSize() + "@" + slot.getIcon();
                })
                .collect(Collectors.joining(", "));
        GoetyArkham.LOGGER.info(
                "[CurioSlotGameTest] Verified player slots (size@icon): {}",
                slotSummary
        );
        verifyGrotesqueStatueRuntimeWiring(helper);
        helper.succeed();
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

        Map<String, ISlotType> acceptedSlots =
                CuriosApi.getItemStackSlots(stack, helper.getLevel());
        helper.assertTrue(acceptedSlots.keySet().equals(java.util.Set.of(
                        CurioSlotIds.NECKLACE)),
                "Grotesque Statue item tag must expose only the necklace slot");

        ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
        helper.assertTrue(curio != null,
                "Grotesque Statue Curios capability was not registered");
        LivingEntity wearer = EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(wearer != null, "Could not create Curios test wearer");
        helper.assertTrue(curio.canEquip(new SlotContext(
                        CurioSlotIds.NECKLACE, wearer, 0, false, true)),
                "Grotesque Statue rejected the necklace slot");
        helper.assertTrue(!curio.canEquip(new SlotContext(
                        CurioSlotIds.BODY, wearer, 0, false, true)),
                "Grotesque Statue accepted a non-necklace slot");
    }
}
