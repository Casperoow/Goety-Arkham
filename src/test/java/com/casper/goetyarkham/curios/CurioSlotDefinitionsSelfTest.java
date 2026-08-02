package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.command.CuriosCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;

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
        verifyGrotesqueStatueTag();
        verifyGoetySoulTooltip();
        verifyCommandTree();
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

    private static void verifyGrotesqueStatueTag() throws IOException {
        JsonObject charm = readJson("/data/curios/tags/items/charm.json");
        assertFalse(charm.get("replace").getAsBoolean(),
                "charm item tag must merge without replace");
        JsonArray values = charm.getAsJsonArray("values");
        assertEquals(1, values.size(), "Grotesque Statue Curios tag entry count");
        assertEquals("goetyarkham:grotesque_statue", values.get(0).getAsString(),
                "Grotesque Statue is tagged for charm");
        assertResourceValueAbsent(
                "data/curios/tags/items/necklace.json",
                "goetyarkham:grotesque_statue");
    }

    private static void verifyLanguage(String language, Map<String, String> expectedNames)
            throws IOException {
        JsonObject translations = readJson("/assets/goetyarkham/lang/" + language + ".json");
        assertFalse(translations.has("tooltip.goetyarkham.grotesque_statue.souls"),
                language + " must not duplicate Goety's soul tooltip translation");
        expectedNames.forEach((slotId, expectedName) -> assertEquals(
                expectedName,
                translations.get("curios.identifier." + slotId).getAsString(),
                language + " translation for " + slotId
        ));
        if ("zh_cn".equals(language)) {
            assertEquals(
                    "当你将要发生灾厄诡计时，从灵魂池消耗1000灵魂能量，免疫该次诡计。",
                    translations.get("tooltip.goetyarkham.grotesque_statue.effect")
                            .getAsString(),
                    "Chinese Grotesque Statue pool-payment tooltip");
            assertEquals(
                    "诡秘石像从灵魂池消耗了1000灵魂能量，免疫了本次灾厄诡计。",
                    translations.get("message.goetyarkham.grotesque_statue.protected")
                            .getAsString(),
                    "Chinese Grotesque Statue pool-payment message");
        } else if ("en_us".equals(language)) {
            assertEquals(
                    "When you would suffer an Illager Treachery, consume 1,000 Soul Energy from your Soul Energy Pool to become immune to that treachery.",
                    translations.get("tooltip.goetyarkham.grotesque_statue.effect")
                            .getAsString(),
                    "English Grotesque Statue pool-payment tooltip");
            assertEquals(
                    "The Grotesque Statue consumed 1,000 Soul Energy from your Soul Energy Pool and protected you from this Illager Treachery.",
                    translations.get("message.goetyarkham.grotesque_statue.protected")
                            .getAsString(),
                    "English Grotesque Statue pool-payment message");
        }
    }

    private static void verifyGoetySoulTooltip() throws IOException {
        String key = "info.goety.totem_of_souls.souls";
        assertEquals("§aSoul Energy: %d/%d§a",
                readJson("/assets/goety/lang/en_us.json").get(key).getAsString(),
                "Goety English soul tooltip contract");
        assertEquals("§a灵魂能量: %d/%d§a",
                readJson("/assets/goety/lang/zh_cn.json").get(key).getAsString(),
                "Goety Chinese soul tooltip contract");
    }

    private static void verifyCommandTree() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CuriosCommand.register(dispatcher);
        var root = dispatcher.getRoot().getChild("goetyarkham");
        assertFalse(root == null, "missing /goetyarkham command root");
        var curios = root.getChild("curios");
        assertFalse(curios == null, "missing /goetyarkham curios command");
        assertFalse(curios.getChild("slots") == null,
                "missing /goetyarkham curios slots command");
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

    private static void assertResourceValueAbsent(
            String resourcePath, String forbiddenValue) throws IOException {
        Enumeration<URL> resources = CurioSlotDefinitionsSelfTest.class
                .getClassLoader().getResources(resourcePath);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStream stream = resource.openStream();
                 InputStreamReader reader = new InputStreamReader(
                         stream, StandardCharsets.UTF_8)) {
                JsonObject tag = JsonParser.parseReader(reader).getAsJsonObject();
                if (tag.has("values")) {
                    for (var value : tag.getAsJsonArray("values")) {
                        assertFalse(forbiddenValue.equals(value.getAsString()),
                                "forbidden value in " + resourcePath + " at " + resource);
                    }
                }
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
