package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.GoetyArkham;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

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
        helper.succeed();
    }
}
