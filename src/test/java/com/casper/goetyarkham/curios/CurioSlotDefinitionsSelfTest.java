package com.casper.goetyarkham.curios;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that the source-controlled Curios data matches the stable Java slot catalog.
 */
public final class CurioSlotDefinitionsSelfTest {
    private static final Map<String, String> ENGLISH_NAMES = Map.of(
            CurioSlotIds.TOKEN, "Token",
            CurioSlotIds.FOCUS, "Focus",
            CurioSlotIds.TAROT, "Tarot Card",
            CurioSlotIds.ASSET, "Asset",
            CurioSlotIds.TALENT, "Talent",
            CurioSlotIds.WEAKNESS, "Weakness"
    );

    private static final Map<String, String> CHINESE_NAMES = Map.of(
            CurioSlotIds.TOKEN, "信物",
            CurioSlotIds.FOCUS, "聚晶",
            CurioSlotIds.TAROT, "塔罗牌",
            CurioSlotIds.ASSET, "资产",
            CurioSlotIds.TALENT, "天赋",
            CurioSlotIds.WEAKNESS, "弱点"
    );

    private CurioSlotDefinitionsSelfTest() {
    }

    public static void main(String[] args) throws IOException {
        verifyCatalog();
        verifySlotDefinitions();
        verifyPlayerBinding();
        verifyLanguage("en_us", ENGLISH_NAMES);
        verifyLanguage("zh_cn", CHINESE_NAMES);
        System.out.println("CurioSlotDefinitionsSelfTest: all checks passed");
    }

    private static void verifyCatalog() {
        Set<String> uniqueIds = new HashSet<>(CurioSlotIds.ALL);
        assertEquals(CurioSlotIds.ALL.size(), uniqueIds.size(), "slot catalog has unique IDs");
        assertEquals(uniqueIds, CurioSlotIds.BASE_SIZES.keySet(), "slot size catalog matches ID catalog");
    }

    private static void verifySlotDefinitions() throws IOException {
        for (String slotId : CurioSlotIds.ALL) {
            JsonObject slot = readJson("/data/goetyarkham/curios/slots/" + slotId + ".json");
            assertEquals(
                    CurioSlotIds.BASE_SIZES.get(slotId),
                    slot.get("size").getAsInt(),
                    slotId + " base size"
            );
            assertEquals("SET", slot.get("operation").getAsString(), slotId + " size operation");
            assertFalse(slot.has("replace"), slotId + " must merge without replace");
        }
    }

    private static void verifyPlayerBinding() throws IOException {
        JsonObject binding = readJson("/data/goetyarkham/curios/entities/player.json");
        JsonArray entities = binding.getAsJsonArray("entities");
        assertEquals(1, entities.size(), "player binding entity count");
        assertEquals("minecraft:player", entities.get(0).getAsString(), "player binding entity");
        assertFalse(binding.has("replace"), "player binding must merge without replace");

        JsonArray slots = binding.getAsJsonArray("slots");
        Set<String> uniqueSlots = new HashSet<>();
        slots.forEach(element -> uniqueSlots.add(element.getAsString()));
        assertEquals(slots.size(), uniqueSlots.size(), "player binding has no duplicate slot IDs");
        assertEquals(new HashSet<>(CurioSlotIds.ALL), uniqueSlots, "player binding covers the slot catalog");
    }

    private static void verifyLanguage(String language, Map<String, String> expectedNames)
            throws IOException {
        JsonObject translations = readJson("/assets/goetyarkham/lang/" + language + ".json");
        expectedNames.forEach((slotId, expectedName) -> assertEquals(
                expectedName,
                translations.get("curios.identifier." + slotId).getAsString(),
                language + " translation for " + slotId
        ));
    }

    private static JsonObject readJson(String resourcePath) throws IOException {
        try (InputStream stream = CurioSlotDefinitionsSelfTest.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new AssertionError("Missing resource: " + resourcePath);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static void assertFalse(boolean value, String label) {
        if (value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
