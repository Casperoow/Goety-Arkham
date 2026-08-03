package com.casper.goetyarkham.loneliness;

import com.casper.goetyarkham.network.ClientboundLonelinessPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public final class LonelinessSelfTest {
    private LonelinessSelfTest() {
    }

    public static void main(String[] args) {
        initialAndClampRules();
        settlementRules();
        copyRules();
        overflowRecoveryRules();
        nbtRoundTrip();
        networkRoundTrip();
        System.out.println("LonelinessSelfTest: all checks passed");
    }

    private static void initialAndClampRules() {
        PlayerLonelinessData data = new PlayerLonelinessData();
        assertEquals(0, data.getLoneliness(), "new-player loneliness");

        CompoundTag negative = new CompoundTag();
        negative.putInt("loneliness", -5);
        PlayerLonelinessData loaded = new PlayerLonelinessData();
        loaded.deserializeNBT(negative);
        assertEquals(0, loaded.getLoneliness(), "negative loneliness clamps to zero");
    }

    private static void settlementRules() {
        PlayerLonelinessData data = new PlayerLonelinessData();
        assertFalse(data.addOne(), "first stack does not settle");
        assertEquals(1, data.getLoneliness(), "one stack");
        assertFalse(data.addOne(), "second stack does not settle");
        assertFalse(data.addOne(), "third stack does not settle");
        assertFalse(data.addOne(), "fourth stack does not settle");
        assertEquals(4, data.getLoneliness(), "four stacks is the ceiling");
        assertTrue(data.addOne(), "fifth stack settles");
        assertEquals(0, data.getLoneliness(),
                "loneliness is never observable at five, only zero after settlement");

        // Settlement is a one-shot event per crossing; the counter climbs
        // again normally afterward.
        assertFalse(data.addOne(), "post-settlement stack does not immediately resettle");
        assertEquals(1, data.getLoneliness(), "post-settlement stack count");
    }

    private static void copyRules() {
        PlayerLonelinessData source = new PlayerLonelinessData();
        source.addOne();
        source.addOne();
        source.addOne();
        PlayerLonelinessData target = new PlayerLonelinessData();
        target.copyFrom(source);
        assertEquals(3, target.getLoneliness(), "copyFrom preserves stack count");
    }

    private static void overflowRecoveryRules() {
        CompoundTag corrupt = new CompoundTag();
        corrupt.putInt("loneliness", 9);
        PlayerLonelinessData data = new PlayerLonelinessData();
        data.deserializeNBT(corrupt);
        assertEquals(4, data.getLoneliness(),
                "anomalous saved value clamps to the ceiling pending settlement");
        assertTrue(data.consumePendingOverflowSettle(),
                "anomalous saved value flags exactly one pending settlement");
        assertFalse(data.consumePendingOverflowSettle(),
                "pending settlement is consumed only once");
        assertFalse(data.consumePendingOverflowSettle(),
                "repeated reconciliation stays idempotent");
    }

    private static void nbtRoundTrip() {
        PlayerLonelinessData original = new PlayerLonelinessData();
        original.addOne();
        original.addOne();
        PlayerLonelinessData loaded = new PlayerLonelinessData();
        loaded.deserializeNBT(original.serializeNBT());
        assertEquals(original.getLoneliness(), loaded.getLoneliness(),
                "NBT round trip preserves stack count");
    }

    private static void networkRoundTrip() {
        LonelinessSnapshot expected = new LonelinessSnapshot(3);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundLonelinessPacket.encode(
                new ClientboundLonelinessPacket(expected), buffer);
        LonelinessSnapshot actual =
                ClientboundLonelinessPacket.decode(buffer).snapshot();
        assertEquals(expected, actual, "network snapshot codec");
        buffer.release();
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
