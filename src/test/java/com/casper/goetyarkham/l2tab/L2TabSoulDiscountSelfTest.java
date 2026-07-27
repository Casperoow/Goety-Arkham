package com.casper.goetyarkham.l2tab;

import com.casper.goetyarkham.revelation.RevelationAttributeBridge;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class L2TabSoulDiscountSelfTest {
    private static final String CONFIG_PATH =
            "data/goetyarkham/l2tabs_config/attribute_entry/player_stats.json";
    private static final String SOUL_DISCOUNT_ATTRIBUTE_ID =
            "goety_revelation:soul_decrease_reduction";
    private static final String SOUL_DISCOUNT_TRANSLATION_KEY =
            "attribute.name.goety_revelation.soul_decrease_reduction";
    private static final double EPSILON = 1.0E-9D;

    private L2TabSoulDiscountSelfTest() {
    }

    public static void run() {
        JsonObject config = loadConfig();
        JsonObject entry = findEntry(config.getAsJsonArray("list"));
        double intrinsic = entry.get("intrinsic").getAsDouble();

        assertClose(-1.0D, intrinsic, "L2Tab soul discount intrinsic");
        assertTrue(entry.get("usePercent").getAsBoolean(),
                "L2Tab soul discount uses percentage formatting");
        int order = entry.get("order").getAsInt();
        assertTrue(order > 19500 && order < 20000,
                "L2Tab soul discount order is between multiplier and soul capacity");
        assertTranslation(
                "assets/goetyarkham/lang/en_us.json", "Soul Discount");
        assertTranslation(
                "assets/goetyarkham/lang/zh_cn.json", "灵魂折扣");

        AttributeInstance soulDecreaseReduction = new AttributeInstance(
                new RangedAttribute(
                        "test.soul_decrease_reduction", 1.0D, -32767.0D, 2.0D),
                ignored -> {
                }
        );

        assertDisplay(soulDecreaseReduction, intrinsic, 0, 0.0D);
        assertDisplay(soulDecreaseReduction, intrinsic, 20, 20.0D);
        assertDisplay(soulDecreaseReduction, intrinsic, 50, 50.0D);
        assertDisplay(soulDecreaseReduction, intrinsic, 60, 50.0D);
        assertClose(10.0D,
                RevelationAttributeBridge.calculateIntellectSpellPowerContribution(60),
                "intellect 60 overflow spell power remains unchanged");

        RevelationAttributeBridge.updateIntellectSoulDiscountModifier(
                soulDecreaseReduction, 60);
        RevelationAttributeBridge.updateIntellectSoulDiscountModifier(
                soulDecreaseReduction, 60);
        assertClose(50.0D, l2TabPercent(soulDecreaseReduction.getValue(), intrinsic),
                "repeated refresh does not stack the displayed discount");

        RevelationAttributeBridge.updateIntellectSoulDiscountModifier(
                soulDecreaseReduction, 20);
        assertClose(20.0D, l2TabPercent(soulDecreaseReduction.getValue(), intrinsic),
                "lower intellect replaces the displayed discount");
    }

    private static void assertDisplay(
            AttributeInstance attribute,
            double intrinsic,
            int intellect,
            double expectedPercent) {
        RevelationAttributeBridge.updateIntellectSoulDiscountModifier(attribute, intellect);
        assertClose(expectedPercent, l2TabPercent(attribute.getValue(), intrinsic),
                "displayed soul discount at intellect " + intellect);
    }

    /**
     * L2Tabs 0.3.3 renders (attribute value + intrinsic), then multiplies by
     * 100 when usePercent is enabled.
     */
    private static double l2TabPercent(double attributeValue, double intrinsic) {
        return (attributeValue + intrinsic) * 100.0D;
    }

    private static JsonObject loadConfig() {
        return loadJson(CONFIG_PATH, "L2Tab config");
    }

    private static void assertTranslation(String path, String expected) {
        JsonObject language = loadJson(path, "language file");
        String actual = language.get(SOUL_DISCOUNT_TRANSLATION_KEY).getAsString();
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "soul discount translation in " + path
                            + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static JsonObject loadJson(String path, String label) {
        ClassLoader loader = L2TabSoulDiscountSelfTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("missing " + label + " resource: " + path);
            }
            return JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            ).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("failed to load " + label + ": " + path, exception);
        }
    }

    private static JsonObject findEntry(JsonArray entries) {
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (SOUL_DISCOUNT_ATTRIBUTE_ID.equals(entry.get("id").getAsString())) {
                return entry;
            }
        }
        throw new AssertionError(
                "missing L2Tab attribute entry: " + SOUL_DISCOUNT_ATTRIBUTE_ID);
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
