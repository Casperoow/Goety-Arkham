package com.casper.goetyarkham.sanity;

import com.casper.goetyarkham.network.ClientboundSanityPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import top.theillusivec4.curios.api.SlotContext;

import java.util.Optional;

public final class SanitySelfTest {
    private SanitySelfTest() {
    }

    public static void main(String[] args) throws Exception {
        initialAndClampRules();
        permanentLossRules();
        attributeAndMaximumRules();
        recoveryRules();
        collapseTransitionRules();
        collapseTickAndSoulRules();
        deathCloneRules();
        nbtRoundTrip();
        networkRoundTrip();
        clientBoundary();
        System.out.println("SanitySelfTest: all checks passed");
    }

    private static void initialAndClampRules() {
        PlayerSanityData data = new PlayerSanityData();
        assertEquals(10, data.getCurrentSanity(), "new-player current sanity");
        assertEquals(10, SanityMath.maximumSanity(10.0D, 0),
                "new-player maximum sanity");
        data.setCurrentSanity(100, 10);
        assertEquals(10, data.getCurrentSanity(), "upper current clamp");
        data.setCurrentSanity(-100, 10);
        assertEquals(0, data.getCurrentSanity(), "lower current clamp");
        assertEquals(1, SanityMath.maximumSanity(1.0D, 9),
                "final maximum floor");
    }

    private static void permanentLossRules() {
        PlayerSanityData data = new PlayerSanityData();
        assertEquals(9, data.setPermanentMaxLoss(99),
                "permanent damage clamps input above nine");
        assertEquals(9, data.getPermanentMaxLoss(), "permanent loss upper clamp");
        assertEquals(0, data.setPermanentMaxLoss(20),
                "damage at nine has zero actual change");
        assertEquals(-9, data.setPermanentMaxLoss(-20),
                "permanent loss lower clamp change");
        assertEquals(0, data.getPermanentMaxLoss(), "permanent loss lower clamp");

        CompoundTag bad = new CompoundTag();
        bad.putInt("currentSanity", 10);
        bad.putInt("permanentMaxLoss", 1000);
        PlayerSanityData loaded = new PlayerSanityData();
        loaded.deserializeNBT(bad, 1);
        assertEquals(9, loaded.getPermanentMaxLoss(), "NBT loss clamp");
        assertEquals(1, loaded.getCurrentSanity(), "NBT current maximum clamp");
    }

    private static void attributeAndMaximumRules() {
        RangedAttribute attribute = new RangedAttribute(
                "attribute.name.goetyarkham.max_sanity", 10.0D, 1.0D, 1024.0D);
        assertEquals(10.0D, attribute.getDefaultValue(), "MAX_SANITY base value");
        AttributeInstance instance = new AttributeInstance(attribute, ignored -> {
        });
        PlayerSanityData data = new PlayerSanityData();
        assertEquals(1, SanityMath.maximumSanity(10.0D, 9), "10 minus 9");
        assertEquals(6, SanityMath.maximumSanity(15.0D, 9), "15 minus 9");

        AttributeModifier plusFive = new AttributeModifier(
                "sanity-test", 5.0D, AttributeModifier.Operation.ADDITION);
        instance.addTransientModifier(plusFive);
        assertEquals(15, SanityMath.maximumSanity(instance.getValue(), 0),
                "attribute modifier increases maximum");
        data.setCurrentSanity(15, 15);
        instance.removeModifier(plusFive.getId());
        int lowered = SanityMath.maximumSanity(instance.getValue(), 0);
        data.clampToMaximum(lowered);
        assertEquals(10, data.getCurrentSanity(),
                "modifier removal clamps current to lower maximum");

        data.setCurrentSanity(7, 10);
        instance.addTransientModifier(plusFive);
        data.clampToMaximum(SanityMath.maximumSanity(instance.getValue(), 0));
        assertEquals(7, data.getCurrentSanity(),
                "raising maximum does not restore current");

        SlotContext slot0 = new SlotContext("talent", null, 0, false, true);
        SlotContext slot1 = new SlotContext("talent", null, 1, false, true);
        ResourceLocation source = new ResourceLocation("goetyarkham", "test_curio");
        AttributeModifier first = SanityAttributeModifiers.maxSanityAddition(
                source, slot0, 2.0D);
        AttributeModifier firstAgain = SanityAttributeModifiers.maxSanityAddition(
                source, slot0, 2.0D);
        AttributeModifier secondSlot = SanityAttributeModifiers.maxSanityAddition(
                source, slot1, 2.0D);
        assertEquals(first.getId(), firstAgain.getId(), "stable Curios modifier UUID");
        assertFalse(first.getId().equals(secondSlot.getId()),
                "different Curios slots have different UUIDs");
        assertEquals(AttributeModifier.Operation.ADDITION, first.getOperation(),
                "Curios maximum modifier operation");
    }

    private static void recoveryRules() {
        assertTrue(SanityMath.canFoodRestore(6, 10), "6/10 food restore");
        assertFalse(SanityMath.canFoodRestore(5, 10), "5/10 no food restore");
        assertTrue(SanityMath.canFoodRestore(8, 15), "8/15 food restore");
        assertFalse(SanityMath.canFoodRestore(7, 15), "7/15 no food restore");
        assertFalse(SanityMath.canFoodRestore(0, 10), "food cannot restore zero");

        PlayerSanityData sleeping = new PlayerSanityData();
        sleeping.setCurrentSanity(0, 10);
        sleeping.setCurrentSanity(1, 10);
        assertEquals(1, sleeping.getCurrentSanity(), "sleep can restore zero to one");

        PlayerSanityData restoredMaximum = new PlayerSanityData();
        restoredMaximum.setPermanentMaxLoss(5);
        restoredMaximum.setCurrentSanity(3, 5);
        assertEquals(-2, restoredMaximum.setPermanentMaxLoss(3),
                "permanent restore reports actual amount as signed model change");
        int raisedMaximum = SanityMath.maximumSanity(10.0D,
                restoredMaximum.getPermanentMaxLoss());
        restoredMaximum.clampToMaximum(raisedMaximum);
        assertEquals(3, restoredMaximum.getCurrentSanity(),
                "permanent restore raises max without healing");
    }

    private static void collapseTransitionRules() {
        PlayerSanityData data = new PlayerSanityData();
        PlayerSanityData.SanityTransition first = data.setCurrentSanity(0, 10);
        assertTrue(first.enteredCollapse(), "first zero activates weakness once");
        assertFalse(data.setCurrentSanity(0, 10).enteredCollapse(),
                "remaining at zero does not reactivate");
        data.setCurrentSanity(1, 10);
        assertTrue(data.setCurrentSanity(0, 10).enteredCollapse(),
                "recover then deplete activates again");

        CompoundTag savedAtZero = data.serializeNBT();
        PlayerSanityData loadedAtZero = new PlayerSanityData();
        loadedAtZero.deserializeNBT(savedAtZero, 10);
        assertTrue(loadedAtZero.isCollapseActive(), "saved zero reload remains active");
        assertFalse(loadedAtZero.setCurrentSanity(0, 10).enteredCollapse(),
                "zero reload does not activate weakness again");
    }

    private static void collapseTickAndSoulRules() {
        assertEquals(10, SanityConstants.COLLAPSE_SOUL_COST,
                "collapse removes ten soul per settlement");
        assertTrue(SanityConstants.COLLAPSE_HUNGER_DURATION_TICKS >= 40
                        && SanityConstants.COLLAPSE_HUNGER_DURATION_TICKS <= 60,
                "collapse hunger duration is within 40..60 ticks");
        PlayerSanityData data = new PlayerSanityData();
        data.setCurrentSanity(0, 10);
        for (int tick = 1; tick < 20; tick++) {
            assertFalse(data.advanceCollapseTick(), "no early soul drain tick " + tick);
        }
        assertTrue(data.advanceCollapseTick(), "soul drains on tick twenty");
        for (int tick = 1; tick < 20; tick++) {
            assertFalse(data.advanceCollapseTick(), "second interval no early drain " + tick);
        }
        assertTrue(data.advanceCollapseTick(), "second soul drain on next tick twenty");
        assertTrue(SanityCollapseRules.isSoulDepleted(true, 0),
                "zero soul triggers death");
        assertTrue(SanityCollapseRules.isSoulDepleted(false, 100),
                "missing container is zero soul");
        assertFalse(SanityCollapseRules.isSoulDepleted(true, 1),
                "positive soul does not trigger death");
    }

    private static void deathCloneRules() {
        PlayerSanityData oldData = collapsedData(0);
        PlayerSanityData newData = new PlayerSanityData();
        SanityEvents.CloneResult first = SanityEvents.copyForClone(
                Optional.of(oldData), Optional.of(newData), true, 10.0D);
        assertEquals(1, first.permanentLossAdded(),
                "zero-sanity death adds permanent loss");
        assertEquals(1, newData.getPermanentMaxLoss(), "death new permanent loss");
        assertEquals(9, newData.getCurrentSanity(), "death refills to new maximum");
        assertFalse(newData.isCollapseActive(), "death resets collapse state");
        assertEquals(0, newData.getCollapseTickCounter(), "death resets counter");

        SanityEvents.CloneResult duplicate = SanityEvents.copyForClone(
                Optional.of(oldData), Optional.of(newData), true, 10.0D);
        assertTrue(duplicate.duplicatePrevented(), "duplicate death settlement prevented");
        assertEquals(1, newData.getPermanentMaxLoss(),
                "duplicate death does not add permanent loss");

        PlayerSanityData lossEight = collapsedData(8);
        PlayerSanityData toNine = new PlayerSanityData();
        SanityEvents.copyForClone(Optional.of(lossEight), Optional.of(toNine), true, 10.0D);
        assertEquals(9, toNine.getPermanentMaxLoss(), "loss eight becomes nine");
        assertEquals(1, toNine.getCurrentSanity(), "loss nine respawns one of one");

        PlayerSanityData lossNine = collapsedData(9);
        PlayerSanityData staysNine = new PlayerSanityData();
        SanityEvents.copyForClone(Optional.of(lossNine), Optional.of(staysNine), true, 15.0D);
        assertEquals(9, staysNine.getPermanentMaxLoss(), "loss nine never increases");
        assertEquals(6, staysNine.getCurrentSanity(), "loss nine with attribute fifteen refills six");

        PlayerSanityData maximumOne = collapsedData(8);
        PlayerSanityData noHiddenLoss = new PlayerSanityData();
        SanityEvents.copyForClone(
                Optional.of(maximumOne), Optional.of(noHiddenLoss), true, 9.0D);
        assertEquals(8, noHiddenLoss.getPermanentMaxLoss(),
                "maximum one creates no hidden loss");

        PlayerSanityData ordinaryOld = new PlayerSanityData();
        ordinaryOld.setPermanentMaxLoss(3);
        ordinaryOld.setCurrentSanity(4, 7);
        PlayerSanityData ordinaryNew = new PlayerSanityData();
        SanityEvents.copyForClone(
                Optional.of(ordinaryOld), Optional.of(ordinaryNew), true, 10.0D);
        assertEquals(4, ordinaryNew.getCurrentSanity(),
                "ordinary death preserves current sanity");
        assertEquals(3, ordinaryNew.getPermanentMaxLoss(),
                "ordinary death preserves permanent loss");
    }

    private static PlayerSanityData collapsedData(int permanentLoss) {
        PlayerSanityData data = new PlayerSanityData();
        data.setPermanentMaxLoss(permanentLoss);
        data.setCurrentSanity(0, SanityMath.maximumSanity(10.0D, permanentLoss));
        return data;
    }

    private static void nbtRoundTrip() {
        PlayerSanityData original = new PlayerSanityData();
        original.setPermanentMaxLoss(4);
        original.setCurrentSanity(0, 6);
        for (int tick = 0; tick < 13; tick++) {
            original.advanceCollapseTick();
        }
        PlayerSanityData loaded = new PlayerSanityData();
        loaded.deserializeNBT(original.serializeNBT(), 6);
        assertEquals(original.getCurrentSanity(), loaded.getCurrentSanity(),
                "NBT current round trip");
        assertEquals(original.getPermanentMaxLoss(), loaded.getPermanentMaxLoss(),
                "NBT permanent loss round trip");
        assertEquals(original.isCollapseActive(), loaded.isCollapseActive(),
                "NBT collapse active round trip");
        assertEquals(original.getCollapseTickCounter(), loaded.getCollapseTickCounter(),
                "NBT collapse counter round trip");
        for (int tick = 0; tick < 6; tick++) {
            assertFalse(loaded.advanceCollapseTick(),
                    "loaded counter does not drain early " + tick);
        }
        assertTrue(loaded.advanceCollapseTick(),
                "loaded counter continues to its next drain");
    }

    private static void networkRoundTrip() {
        SanitySnapshot expected = new SanitySnapshot(6, 15, 3);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundSanityPacket.encode(new ClientboundSanityPacket(expected), buffer);
        SanitySnapshot actual = ClientboundSanityPacket.decode(buffer).snapshot();
        assertEquals(expected, actual, "network snapshot codec");
        buffer.release();
    }

    private static void clientBoundary() throws Exception {
        Class<?> hud = Class.forName(
                "com.casper.goetyarkham.client.SanityHud", false,
                SanitySelfTest.class.getClassLoader());
        OnlyIn onlyIn = hud.getAnnotation(OnlyIn.class);
        assertTrue(onlyIn != null && onlyIn.value() == Dist.CLIENT,
                "HUD class is client-only");
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertFalse(boolean value, String label) {
        assertTrue(!value, label);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
