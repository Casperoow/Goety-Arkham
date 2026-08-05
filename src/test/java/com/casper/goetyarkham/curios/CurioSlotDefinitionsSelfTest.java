package com.casper.goetyarkham.curios;

import com.casper.goetyarkham.command.CuriosCommand;
import com.casper.goetyarkham.item.CurioTooltipHelper;
import com.casper.goetyarkham.item.SignatureWeaknessTooltipHelper;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * Verifies that the source-controlled Curios data matches the stable Java slot catalog.
 */
public final class CurioSlotDefinitionsSelfTest {
    private static final Map<String, String> ENGLISH_NAMES = Map.of(
            CurioSlotIds.TOKEN, "Token",
            CurioSlotIds.BOOK, "Book",
            CurioSlotIds.FOCUS, "Focus",
            CurioSlotIds.TAROT, "Tarot Card",
            CurioSlotIds.ASSET, "Asset",
            CurioSlotIds.TALENT, "Talent",
            CurioSlotIds.WEAKNESS, "Weakness",
            CurioSlotIds.SKILL_BONUS, "Bonus Slot"
    );

    private static final Map<String, String> CHINESE_NAMES = Map.of(
            CurioSlotIds.TOKEN, "信物",
            CurioSlotIds.BOOK, "书籍",
            CurioSlotIds.FOCUS, "聚晶",
            CurioSlotIds.TAROT, "塔罗牌",
            CurioSlotIds.ASSET, "资产",
            CurioSlotIds.TALENT, "天赋",
            CurioSlotIds.WEAKNESS, "弱点",
            CurioSlotIds.SKILL_BONUS, "加成栏位"
    );

    private CurioSlotDefinitionsSelfTest() {
    }

    public static void main(String[] args) throws IOException {
        verifyCatalog();
        verifySlotDefinitions();
        verifySkillBonusSlotDefinition();
        verifyPlayerBinding();
        verifyCharmItemTag();
        verifyHandsItemTag();
        verifyBodyItemTag();
        verifyTokenItemTag();
        verifyHeirloomItemTags();
        verifyFocusItemTag();
        verifyBookItemTag();
        verifySkillBonusItemTag();
        verifyBossOrEliteTag();
        verifyItemResources();
        verifySharedTooltipFormatting();
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

    private static void verifySkillBonusSlotDefinition() throws IOException {
        JsonObject slot = readJson(
                "/data/goetyarkham/curios/slots/skill_bonus.json");
        assertEquals(165, slot.get("order").getAsInt(),
                "skill_bonus slot order (after asset at 160, before talent at 170)");
        assertEquals("goetyarkham:slot/skill_bonus",
                slot.get("icon").getAsString(), "skill_bonus slot icon");
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

    private static void verifyCharmItemTag() throws IOException {
        JsonObject charm = readJson("/data/curios/tags/items/charm.json");
        assertFalse(charm.get("replace").getAsBoolean(),
                "charm item tag must merge without replace");
        JsonArray values = charm.getAsJsonArray("values");
        Set<String> entries = new HashSet<>();
        values.forEach(element -> entries.add(element.getAsString()));
        assertEquals(Set.of(
                        "goetyarkham:grotesque_statue",
                        "goetyarkham:disc_of_itzamna",
                        "goetyarkham:wendys_amulet",
                        "goetyarkham:police_badge"
                ),
                entries,
                "Goety: Arkham charm item tag entries");
        assertResourceValueAbsent(
                "data/curios/tags/items/necklace.json",
                "goetyarkham:grotesque_statue");
        assertResourceValueAbsent(
                "data/curios/tags/items/necklace.json",
                "goetyarkham:disc_of_itzamna");
        assertResourceValueAbsent(
                "data/curios/tags/items/necklace.json",
                "goetyarkham:wendys_amulet");
        assertResourceValueAbsent(
                "data/curios/tags/items/necklace.json",
                "goetyarkham:police_badge");
    }

    private static void verifyHandsItemTag() throws IOException {
        JsonObject hands = readJson("/data/curios/tags/items/hands.json");
        assertFalse(hands.get("replace").getAsBoolean(),
                "hands item tag must merge without replace");
        assertEquals(List.of(
                        "goetyarkham:holy_rosary",
                        "goetyarkham:medical_texts",
                        "goetyarkham:book_of_shadows",
                        "goetyarkham:old_book_of_lore",
                        "goetyarkham:lockpicks",
                        "goetyarkham:magnifying_glass",
                        "goetyarkham:encyclopedia"),
                hands.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Hands tag entries");
        for (String slotId : CurioSlotIds.ALL) {
            if (!CurioSlotIds.HANDS.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:holy_rosary");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:lockpicks");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:magnifying_glass");
            }
            // Medical Texts, the Book of Shadows, the Old Book of Lore, and
            // the Encyclopedia are deliberately dual-tagged: each must
            // appear in hands and book, and nowhere else.
            if (!CurioSlotIds.HANDS.equals(slotId) && !CurioSlotIds.BOOK.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:medical_texts");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:book_of_shadows");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:old_book_of_lore");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:encyclopedia");
            }
        }
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:holy_rosary");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:lockpicks");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:magnifying_glass");
    }

    private static void verifyBodyItemTag() throws IOException {
        JsonObject body = readJson("/data/curios/tags/items/body.json");
        assertFalse(body.get("replace").getAsBoolean(),
                "body item tag must merge without replace");
        assertEquals(List.of(
                        "goetyarkham:leather_coat",
                        "goetyarkham:bulletproof_vest"),
                body.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Body tag entries");
        for (String slotId : CurioSlotIds.ALL) {
            if (!CurioSlotIds.BODY.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:leather_coat");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:bulletproof_vest");
            }
        }
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:leather_coat");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:bulletproof_vest");
    }

    private static void verifyTokenItemTag() throws IOException {
        JsonObject token = readJson("/data/curios/tags/items/token.json");
        assertFalse(token.get("replace").getAsBoolean(),
                "token item tag must merge without replace");
        assertEquals(List.of(
                        "goetyarkham:aquinnahs_token",
                        "goetyarkham:beat_cops_token",
                        "goetyarkham:arcane_initiates_token",
                        "goetyarkham:leo_de_lucas_token",
                        "goetyarkham:rita_chandlers_token"),
                token.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Token tag entries");
        for (String slotId : CurioSlotIds.ALL) {
            if (!CurioSlotIds.TOKEN.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:aquinnahs_token");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:beat_cops_token");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:arcane_initiates_token");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:leo_de_lucas_token");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:rita_chandlers_token");
            }
        }
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:aquinnahs_token");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:beat_cops_token");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:arcane_initiates_token");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:leo_de_lucas_token");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:rita_chandlers_token");
    }

    private static void verifyHeirloomItemTags() throws IOException {
        JsonObject necklace = readJson(
                "/data/curios/tags/items/necklace.json");
        assertFalse(necklace.get("replace").getAsBoolean(),
                "necklace item tag must merge without replace");
        assertEquals(List.of(
                        "goetyarkham:heirloom_of_hyperborea",
                        "goetyarkham:rabbit_foot",
                        "goetyarkham:elder_sign_amulet"),
                necklace.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Necklace tag entries");

        JsonObject weakness = readJson(
                "/data/curios/tags/items/weakness.json");
        assertFalse(weakness.get("replace").getAsBoolean(),
                "weakness item tag must merge without replace");
        assertEquals(List.of(
                        "goetyarkham:dark_memory",
                        "goetyarkham:abandoned_and_alone"),
                weakness.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Weakness tag entries");

        for (String slotId : CurioSlotIds.ALL) {
            if (!CurioSlotIds.NECKLACE.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:heirloom_of_hyperborea");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:rabbit_foot");
            }
            if (!CurioSlotIds.WEAKNESS.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:dark_memory");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:abandoned_and_alone");
            }
        }
    }

    /**
     * The Curios {@code focus} slot must only accept items tagged as a
     * focus, and the addon's own focus tag collects every Goety: Arkham item
     * that implements {@code IFocus}. No such item exists yet, so the
     * addon-owned tag is currently empty; when one is added, both this set
     * and {@code data/goetyarkham/tags/items/focuses.json} must be updated
     * together.
     */
    private static void verifyFocusItemTag() throws IOException {
        JsonObject focus = readJson("/data/curios/tags/items/focus.json");
        assertFalse(focus.get("replace").getAsBoolean(),
                "focus item tag must merge without replace");
        Set<String> entries = new HashSet<>();
        focus.getAsJsonArray("values").forEach(element -> entries.add(element.getAsString()));
        assertEquals(Set.of("#goety:focuses", "#goetyarkham:focuses"),
                entries, "focus item tag entries");

        JsonObject arkhamFocuses = readJson("/data/goetyarkham/tags/items/focuses.json");
        assertFalse(arkhamFocuses.get("replace").getAsBoolean(),
                "goetyarkham:focuses tag must merge without replace");
        Set<String> arkhamEntries = new HashSet<>();
        arkhamFocuses.getAsJsonArray("values")
                .forEach(element -> arkhamEntries.add(element.getAsString()));
        Set<String> expectedFocusItems = Set.of();
        assertEquals(expectedFocusItems, arkhamEntries,
                "goetyarkham:focuses currently has no IFocus implementers");
    }

    /**
     * The Curios {@code book} slot has base size 0 and only appears once
     * another item's dynamic slot modifier grants it. Medical Texts, the
     * Book of Shadows, and the Old Book of Lore all carry the tag, alongside
     * {@code hands} - each is equippable in either slot, never both at once.
     */
    private static void verifyBookItemTag() throws IOException {
        JsonObject book = readJson("/data/curios/tags/items/book.json");
        assertFalse(book.get("replace").getAsBoolean(),
                "book item tag must merge without replace");
        assertEquals(List.of(
                        "goetyarkham:medical_texts",
                        "goetyarkham:book_of_shadows",
                        "goetyarkham:old_book_of_lore",
                        "goetyarkham:encyclopedia"),
                book.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Book tag entries");
    }

    /**
     * The Curios {@code skill_bonus} slot has base size 0 and only appears
     * once some currently equipped {@link
     * com.casper.goetyarkham.curios.SharedBonusSlotProvider} grants it
     * capacity (see {@link
     * com.casper.goetyarkham.curios.SharedBonusSlotService}). Exactly four
     * items may occupy it once granted: three vanilla items and Goety's
     * Ectoplasm (its real registry ID, not a guess from its display name),
     * each capped at a stack of 1 by server-side logic rather than the
     * data-pack tag alone.
     */
    private static void verifySkillBonusItemTag() throws IOException {
        JsonObject tag = readJson("/data/curios/tags/items/skill_bonus.json");
        assertFalse(tag.get("replace").getAsBoolean(),
                "skill_bonus item tag must merge without replace");
        Set<String> entries = new HashSet<>();
        tag.getAsJsonArray("values").forEach(element -> entries.add(element.getAsString()));
        assertEquals(Set.of(
                        "minecraft:iron_ingot",
                        "minecraft:rabbit_foot",
                        "minecraft:book",
                        "goety:ectoplasm"),
                entries,
                "skill_bonus item tag entries");
        assertResourceExists(
                "/assets/goetyarkham/textures/slot/skill_bonus.png");
        assertResourceAbsent(
                "/assets/goetyarkham/textures/slot/empty_encyclopedia_slot.png");
        assertResourceAbsent("/data/curios/tags/items/encyclopedia_skill.json");
        assertResourceAbsent("/data/goetyarkham/curios/slots/encyclopedia_skill.json");
    }

    private static void verifyBossOrEliteTag() throws IOException {
        JsonObject tag = readJson(
                "/data/goetyarkham/tags/entity_types/boss_or_elite.json");
        assertFalse(tag.get("replace").getAsBoolean(),
                "boss_or_elite must permit data-pack additions");
        Set<String> entries = new HashSet<>();
        tag.getAsJsonArray("values")
                .forEach(element -> entries.add(element.getAsString()));
        assertEquals(Set.of(
                        "#forge:bosses",
                        "#goety:mini_bosses",
                        "graveyard:lich"
                ),
                entries,
                "boss_or_elite entries");
    }

    private static void verifyItemResources() throws IOException {
        JsonObject discModel = readJson(
                "/assets/goetyarkham/models/item/disc_of_itzamna.json");
        assertEquals("goetyarkham:item/disc_of_itzamna",
                discModel.getAsJsonObject("textures").get("layer0").getAsString(),
                "Disc of Itzamna model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/disc_of_itzamna.png");
        JsonObject rosaryModel = readJson(
                "/assets/goetyarkham/models/item/holy_rosary.json");
        assertEquals("minecraft:item/generated",
                rosaryModel.get("parent").getAsString(),
                "Holy Rosary model parent");
        assertEquals("goetyarkham:item/holy_rosary",
                rosaryModel.getAsJsonObject("textures").get("layer0").getAsString(),
                "Holy Rosary model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/holy_rosary.png");

        JsonObject heirloomModel = readJson(
                "/assets/goetyarkham/models/item/heirloom_of_hyperborea.json");
        assertEquals("minecraft:item/generated",
                heirloomModel.get("parent").getAsString(),
                "Heirloom model parent");
        assertEquals("goetyarkham:item/heirloom_of_hyperborea",
                heirloomModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Heirloom model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/heirloom_of_hyperborea.png");

        JsonObject memoryModel = readJson(
                "/assets/goetyarkham/models/item/dark_memory.json");
        assertEquals("minecraft:item/generated",
                memoryModel.get("parent").getAsString(),
                "Dark Memory model parent");
        assertEquals("goetyarkham:item/dark_memory",
                memoryModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Dark Memory model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/dark_memory.png");

        JsonObject rabbitFootModel = readJson(
                "/assets/goetyarkham/models/item/rabbit_foot.json");
        assertEquals("minecraft:item/generated",
                rabbitFootModel.get("parent").getAsString(),
                "Rabbit's Foot model parent");
        assertEquals("goetyarkham:item/rabbit_food",
                rabbitFootModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Rabbit's Foot model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/rabbit_food.png");

        JsonObject amuletModel = readJson(
                "/assets/goetyarkham/models/item/wendys_amulet.json");
        assertEquals("minecraft:item/generated",
                amuletModel.get("parent").getAsString(),
                "Wendy's Amulet model parent");
        assertEquals("goetyarkham:item/wendy_amulet",
                amuletModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Wendy's Amulet model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/wendy_amulet.png");

        JsonObject abandonedModel = readJson(
                "/assets/goetyarkham/models/item/abandoned_and_alone.json");
        assertEquals("minecraft:item/generated",
                abandonedModel.get("parent").getAsString(),
                "Abandoned and Alone model parent");
        assertEquals("goetyarkham:item/abandoned_and_alone",
                abandonedModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Abandoned and Alone model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/abandoned_and_alone.png");

        JsonObject leatherCoatModel = readJson(
                "/assets/goetyarkham/models/item/leather_coat.json");
        assertEquals("minecraft:item/generated",
                leatherCoatModel.get("parent").getAsString(),
                "Leather Coat model parent");
        assertEquals("goetyarkham:item/leather_coat",
                leatherCoatModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Leather Coat model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/leather_coat.png");

        JsonObject bulletproofVestModel = readJson(
                "/assets/goetyarkham/models/item/bulletproof_vest.json");
        assertEquals("minecraft:item/generated",
                bulletproofVestModel.get("parent").getAsString(),
                "Bulletproof Vest model parent");
        assertEquals("goetyarkham:item/bulletproof_vest",
                bulletproofVestModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Bulletproof Vest model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/bulletproof_vest.png");

        JsonObject aquinnahsTokenModel = readJson(
                "/assets/goetyarkham/models/item/aquinnahs_token.json");
        assertEquals("minecraft:item/generated",
                aquinnahsTokenModel.get("parent").getAsString(),
                "Aquinnah's Token model parent");
        assertEquals("goetyarkham:item/aquinnahs_token",
                aquinnahsTokenModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Aquinnah's Token model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/aquinnahs_token.png");

        JsonObject beatCopsTokenModel = readJson(
                "/assets/goetyarkham/models/item/beat_cops_token.json");
        assertEquals("minecraft:item/generated",
                beatCopsTokenModel.get("parent").getAsString(),
                "Beat Cop's Token model parent");
        assertEquals("goetyarkham:item/beat_cops_token",
                beatCopsTokenModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Beat Cop's Token model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/beat_cops_token.png");

        JsonObject arcaneInitiatesTokenModel = readJson(
                "/assets/goetyarkham/models/item/arcane_initiates_token.json");
        assertEquals("minecraft:item/generated",
                arcaneInitiatesTokenModel.get("parent").getAsString(),
                "Arcane Initiate's Token model parent");
        assertEquals("goetyarkham:item/arcane_initiates_token",
                arcaneInitiatesTokenModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Arcane Initiate's Token model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/arcane_initiates_token.png");

        JsonObject leoDeLucasTokenModel = readJson(
                "/assets/goetyarkham/models/item/leo_de_lucas_token.json");
        assertEquals("minecraft:item/generated",
                leoDeLucasTokenModel.get("parent").getAsString(),
                "Leo De Luca's Token model parent");
        assertEquals("goetyarkham:item/leo_de_lucas_token",
                leoDeLucasTokenModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Leo De Luca's Token model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/leo_de_lucas_token.png");

        JsonObject ritaChandlersTokenModel = readJson(
                "/assets/goetyarkham/models/item/rita_chandlers_token.json");
        assertEquals("minecraft:item/generated",
                ritaChandlersTokenModel.get("parent").getAsString(),
                "Rita Chandler's Token model parent");
        assertEquals("goetyarkham:item/rita_chandlers_token",
                ritaChandlersTokenModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Rita Chandler's Token model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/rita_chandlers_token.png");

        JsonObject medicalTextsModel = readJson(
                "/assets/goetyarkham/models/item/medical_texts.json");
        assertEquals("minecraft:item/generated",
                medicalTextsModel.get("parent").getAsString(),
                "Medical Texts model parent");
        assertEquals("goetyarkham:item/medical_texts",
                medicalTextsModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Medical Texts model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/medical_texts.png");

        JsonObject bookOfShadowsModel = readJson(
                "/assets/goetyarkham/models/item/book_of_shadows.json");
        assertEquals("minecraft:item/generated",
                bookOfShadowsModel.get("parent").getAsString(),
                "Book of Shadows model parent");
        assertEquals("goetyarkham:item/book_of_shadows",
                bookOfShadowsModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Book of Shadows model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/book_of_shadows.png");

        JsonObject oldBookOfLoreModel = readJson(
                "/assets/goetyarkham/models/item/old_book_of_lore.json");
        assertEquals("minecraft:item/generated",
                oldBookOfLoreModel.get("parent").getAsString(),
                "Old Book of Lore model parent");
        assertEquals("goetyarkham:item/old_book_of_lore",
                oldBookOfLoreModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Old Book of Lore model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/old_book_of_lore.png");

        JsonObject lockpicksModel = readJson(
                "/assets/goetyarkham/models/item/lockpicks.json");
        assertEquals("minecraft:item/generated",
                lockpicksModel.get("parent").getAsString(),
                "Lockpicks model parent");
        assertEquals("goetyarkham:item/lockpicks",
                lockpicksModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Lockpicks model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/lockpicks.png");

        JsonObject magnifyingGlassModel = readJson(
                "/assets/goetyarkham/models/item/magnifying_glass.json");
        assertEquals("minecraft:item/generated",
                magnifyingGlassModel.get("parent").getAsString(),
                "Magnifying Glass model parent");
        assertEquals("goetyarkham:item/magnifying_glass",
                magnifyingGlassModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Magnifying Glass model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/magnifying_glass.png");

        JsonObject encyclopediaModel = readJson(
                "/assets/goetyarkham/models/item/encyclopedia.json");
        assertEquals("minecraft:item/generated",
                encyclopediaModel.get("parent").getAsString(),
                "Encyclopedia model parent");
        assertEquals("goetyarkham:item/encyclopedia",
                encyclopediaModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Encyclopedia model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/encyclopedia.png");
    }

    private static void verifySharedTooltipFormatting() {
        List<Component> tooltip = new java.util.ArrayList<>();
        CurioTooltipHelper.appendWhenWorn(tooltip, "test.effect");
        assertEquals(2, tooltip.size(), "shared Curio tooltip line count");
        assertEquals(
                TextColor.fromLegacyFormat(ChatFormatting.YELLOW),
                tooltip.get(0).getStyle().getColor(),
                "shared when-worn heading color");
        assertEquals(
                TextColor.fromLegacyFormat(ChatFormatting.GRAY),
                tooltip.get(1).getStyle().getColor(),
                "shared Curio effect body color");

        tooltip.clear();
        CurioTooltipHelper.appendWhenWorn(
                tooltip,
                CurioTooltipHelper.attributeBonus(
                        2, "attribute.name.goetyarkham.max_sanity"),
                CurioTooltipHelper.attributeBonus(
                        1, "attribute.name.goetyarkham.willpower"));
        assertEquals(3, tooltip.size(), "multi-effect Curio tooltip line count");
        assertEquals(
                TextColor.fromLegacyFormat(ChatFormatting.YELLOW),
                tooltip.get(0).getStyle().getColor(),
                "multi-effect when-worn heading color");
        assertEquals(
                TextColor.fromLegacyFormat(ChatFormatting.GRAY),
                tooltip.get(2).getStyle().getColor(),
                "multi-effect Curio body color");
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
            assertEquals("诡厄巫法：阿卡姆",
                    translations.get("creativetab.goetyarkham.goety_arkham")
                            .getAsString(),
                    "Chinese creative tab name");
            assertEquals("佩戴时：",
                    translations.get(CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY)
                            .getAsString(),
                    "Chinese shared when-worn heading");
            assertEquals("最大理智",
                    translations.get("attribute.name.goetyarkham.max_sanity")
                            .getAsString(),
                    "Chinese Max Sanity attribute name");
            assertEquals("意志",
                    translations.get("attribute.name.goetyarkham.willpower")
                            .getAsString(),
                    "Chinese Will attribute name");
            assertEquals("+%1$s %2$s",
                    translations.get(CurioTooltipHelper.ATTRIBUTE_BONUS_TRANSLATION_KEY)
                            .getAsString(),
                    "Chinese shared attribute bonus format");
            assertEquals("+%1$s%% %2$s",
                    translations.get(
                            CurioTooltipHelper.ATTRIBUTE_BONUS_PERCENT_TRANSLATION_KEY)
                            .getAsString(),
                    "Chinese shared percent attribute bonus format");
            assertEquals("圣玫瑰珠",
                    translations.get("item.goetyarkham.holy_rosary").getAsString(),
                    "Chinese Holy Rosary name");
            assertEquals("+2 最大理智",
                    attributeBonusText(
                            translations, 2,
                            "attribute.name.goetyarkham.max_sanity"),
                    "Chinese Holy Rosary maximum-sanity tooltip");
            assertEquals("+1 意志",
                    attributeBonusText(
                            translations, 1,
                            "attribute.name.goetyarkham.willpower"),
                    "Chinese Holy Rosary Will tooltip");
            assertEquals("伊察姆纳圆盘",
                    translations.get("item.goetyarkham.disc_of_itzamna")
                            .getAsString(),
                    "Chinese Disc of Itzamna name");
            assertEquals("半径10格内的所有非BOSS敌人会主动远离你",
                    translations.get("tooltip.goetyarkham.disc_of_itzamna.effect")
                            .getAsString(),
                    "Chinese Disc of Itzamna tooltip");
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
            assertEquals("希波利尔传家宝",
                    translations.get("item.goetyarkham.heirloom_of_hyperborea")
                            .getAsString(),
                    "Chinese Heirloom name");
            assertEquals(
                    "你使用聚晶施法时，每实际消耗1点灵魂能量，对身边的所有敌人造成1点伤害",
                    translations.get(
                            "tooltip.goetyarkham.heirloom_of_hyperborea.damage")
                            .getAsString(),
                    "Chinese Heirloom damage tooltip");
            assertEquals("+1弱点栏位",
                    translations.get(
                            "tooltip.goetyarkham.heirloom_of_hyperborea.weakness_slot")
                            .getAsString(),
                    "Chinese Heirloom slot tooltip");
            assertEquals("黑暗回忆",
                    translations.get("item.goetyarkham.dark_memory").getAsString(),
                    "Chinese Dark Memory name");
            assertEquals("按住 Shift 查看弱点效果",
                    translations.get("tooltip.goetyarkham.dark_memory.hold_shift")
                            .getAsString(),
                    "Chinese Dark Memory Shift hint");
            assertEquals("所有人立即触发一次灾厄诡计。",
                    translations.get(
                            "tooltip.goetyarkham.dark_memory.effect.treachery")
                            .getAsString(),
                    "Chinese Dark Memory treachery text");
            assertEquals("失去2点理智",
                    translations.get(
                            "tooltip.goetyarkham.dark_memory.effect.sanity")
                            .getAsString(),
                    "Chinese Dark Memory sanity text");
            assertEquals("幸运兔脚",
                    translations.get("item.goetyarkham.rabbit_foot")
                            .getAsString(),
                    "Chinese Rabbit's Foot name");
            assertEquals("温蒂的护身符",
                    translations.get("item.goetyarkham.wendys_amulet")
                            .getAsString(),
                    "Chinese Wendy's Amulet name");
            assertEquals("被抛弃的孤单",
                    translations.get("item.goetyarkham.abandoned_and_alone")
                            .getAsString(),
                    "Chinese Abandoned and Alone name");
            assertEquals("栏位：护符",
                    translations.get("tooltip.goetyarkham.slot.charm")
                            .getAsString(),
                    "Chinese charm slot label");
            assertEquals("栏位：弱点",
                    translations.get("tooltip.goetyarkham.slot.weakness")
                            .getAsString(),
                    "Chinese weakness slot label");
            assertEquals("栏位：胸饰",
                    translations.get("tooltip.goetyarkham.slot.body")
                            .getAsString(),
                    "Chinese body slot label");
            assertEquals("皮大衣",
                    translations.get("item.goetyarkham.leather_coat")
                            .getAsString(),
                    "Chinese Leather Coat name");
            assertEquals("防弹衣",
                    translations.get("item.goetyarkham.bulletproof_vest")
                            .getAsString(),
                    "Chinese Bulletproof Vest name");
            assertEquals("安奎娜的信物",
                    translations.get("item.goetyarkham.aquinnahs_token")
                            .getAsString(),
                    "Chinese Aquinnah's Token name");
            assertEquals("栏位：信物",
                    translations.get("tooltip.goetyarkham.slot.token")
                            .getAsString(),
                    "Chinese token slot label");
            assertEquals("按住 Shift 查看详细效果",
                    translations.get("tooltip.goetyarkham.aquinnahs_token.hold_shift")
                            .getAsString(),
                    "Chinese Aquinnah's Token Shift hint");
            assertEquals(
                    "受到生物的直接攻击时，信物失去1点耐久，取消本次伤害，并将伤害转移给攻击者",
                    translations.get("tooltip.goetyarkham.aquinnahs_token.effect")
                            .getAsString(),
                    "Chinese Aquinnah's Token effect tooltip");
            assertEquals("耐久已耗尽",
                    translations.get("tooltip.goetyarkham.aquinnahs_token.depleted")
                            .getAsString(),
                    "Chinese Aquinnah's Token depleted tooltip");
            assertEquals("巡警的信物",
                    translations.get("item.goetyarkham.beat_cops_token")
                            .getAsString(),
                    "Chinese Beat Cop's Token name");
            assertEquals("按住 Shift 查看详细效果",
                    translations.get("tooltip.goetyarkham.beat_cops_token.hold_shift")
                            .getAsString(),
                    "Chinese Beat Cop's Token Shift hint");
            assertEquals("每受到1点伤害，获得1层“巡警反击”，最多3层",
                    translations.get(
                            "tooltip.goetyarkham.beat_cops_token.stack_gain")
                            .getAsString(),
                    "Chinese Beat Cop's Token stack-gain tooltip");
            assertEquals("下一次成功的近战攻击每层额外造成2点伤害，并消耗全部层数",
                    translations.get(
                            "tooltip.goetyarkham.beat_cops_token.stack_consume")
                            .getAsString(),
                    "Chinese Beat Cop's Token stack-consume tooltip");
            assertEquals("当前巡警反击：%1$s/%2$s层",
                    translations.get(
                            "tooltip.goetyarkham.beat_cops_token.current_stacks")
                            .getAsString(),
                    "Chinese Beat Cop's Token current-stacks tooltip");
            assertEquals("新晋术士的信物",
                    translations.get("item.goetyarkham.arcane_initiates_token")
                            .getAsString(),
                    "Chinese Arcane Initiate's Token name");
            assertEquals("获得额外1个聚晶栏位",
                    translations.get(
                            "tooltip.goetyarkham.arcane_initiates_token.focus_slot")
                            .getAsString(),
                    "Chinese Arcane Initiate's Token focus-slot tooltip");
            assertEquals("+2 最大理智",
                    attributeBonusText(
                            translations, 2,
                            "attribute.name.goetyarkham.max_sanity"),
                    "Chinese Arcane Initiate's Token maximum-sanity tooltip");
            assertEquals("+2 意志",
                    attributeBonusText(
                            translations, 2,
                            "attribute.name.goetyarkham.willpower"),
                    "Chinese Arcane Initiate's Token Will tooltip");
            assertEquals("里奥·德·卢卡的信物",
                    translations.get("item.goetyarkham.leo_de_lucas_token")
                            .getAsString(),
                    "Chinese Leo De Luca's Token name");
            assertEquals("+2 最大理智",
                    attributeBonusText(
                            translations, 2,
                            "attribute.name.goetyarkham.max_sanity"),
                    "Chinese Leo De Luca's Token maximum-sanity tooltip");
            assertEquals("丽塔·钱德勒的信物",
                    translations.get("item.goetyarkham.rita_chandlers_token")
                            .getAsString(),
                    "Chinese Rita Chandler's Token name");
            assertEquals("+1 力量",
                    attributeBonusText(
                            translations, 1,
                            "attribute.name.goetyarkham.strength"),
                    "Chinese Rita Chandler's Token Strength tooltip");
            assertEquals("按住 Shift 查看详细效果",
                    translations.get(
                            "tooltip.goetyarkham.rita_chandlers_token.hold_shift")
                            .getAsString(),
                    "Chinese Rita Chandler's Token Shift hint");
            assertEquals("你半径10格内的玩家获得+1力量，并使其造成的伤害+2。",
                    translations.get(
                            "tooltip.goetyarkham.rita_chandlers_token.aura_effect")
                            .getAsString(),
                    "Chinese Rita Chandler's Token aura tooltip");
            assertEquals("该范围包括你自己。",
                    translations.get(
                            "tooltip.goetyarkham.rita_chandlers_token.aura_self")
                            .getAsString(),
                    "Chinese Rita Chandler's Token aura self-inclusion tooltip");
            assertEquals("丽塔·钱德勒的祝福",
                    translations.get("effect.goetyarkham.rita_chandlers_blessing")
                            .getAsString(),
                    "Chinese Rita Chandler's Blessing effect name");
            assertEquals("诡厄巫法：阿卡姆",
                    translations.get("key.categories.goetyarkham")
                            .getAsString(),
                    "Chinese key category name");
            assertEquals("使用医学教科书",
                    translations.get("key.goetyarkham.medical_texts_ability")
                            .getAsString(),
                    "Chinese Medical Texts key name");
            assertEquals("医学教科书",
                    translations.get("item.goetyarkham.medical_texts")
                            .getAsString(),
                    "Chinese Medical Texts name");
            assertEquals("栏位：手饰、书籍",
                    translations.get("tooltip.goetyarkham.medical_texts.slot")
                            .getAsString(),
                    "Chinese Medical Texts slot label");
            assertEquals(
                    "按下%s键进行一次智慧检定（2）。如果成功，则获得瞬间治疗 I；如果失败，则受到1点伤害。",
                    translations.get("tooltip.goetyarkham.medical_texts.effect")
                            .getAsString(),
                    "Chinese Medical Texts effect tooltip");
            assertEquals("影之书",
                    translations.get("item.goetyarkham.book_of_shadows")
                            .getAsString(),
                    "Chinese Book of Shadows name");
            assertEquals("栏位：手饰、书籍",
                    translations.get("tooltip.goetyarkham.book_of_shadows.slot")
                            .getAsString(),
                    "Chinese Book of Shadows slot label");
            assertEquals("获得额外1个聚晶栏位",
                    translations.get(
                            "tooltip.goetyarkham.book_of_shadows.focus_slot")
                            .getAsString(),
                    "Chinese Book of Shadows focus-slot tooltip");
            assertEquals("百科全书",
                    translations.get("item.goetyarkham.encyclopedia")
                            .getAsString(),
                    "Chinese Encyclopedia name");
            assertEquals("栏位：手饰、书籍",
                    translations.get("tooltip.goetyarkham.encyclopedia.slot")
                            .getAsString(),
                    "Chinese Encyclopedia slot label");
            assertEquals("获得额外1个加成栏位",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.skill_slot")
                            .getAsString(),
                    "Chinese Encyclopedia skill-slot tooltip");
            assertEquals("每个栏位中的技能物品使其对应技能+2",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.skill_bonus")
                            .getAsString(),
                    "Chinese Encyclopedia skill-bonus tooltip");
            assertEquals("按住 Shift：",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.accepted_heading")
                            .getAsString(),
                    "Chinese Encyclopedia accepted-items heading");
            assertEquals("可放入铁锭、兔子腿、书、灵质",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.accepted_items")
                            .getAsString(),
                    "Chinese Encyclopedia accepted-items tooltip");
            assertEquals("按住 Shift 查看详细效果",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.hold_shift")
                            .getAsString(),
                    "Chinese Encyclopedia hold-shift tooltip");
            assertEquals("智慧古书",
                    translations.get("item.goetyarkham.old_book_of_lore")
                            .getAsString(),
                    "Chinese Old Book of Lore name");
            assertEquals("栏位：手饰、书籍",
                    translations.get("tooltip.goetyarkham.old_book_of_lore.slot")
                            .getAsString(),
                    "Chinese Old Book of Lore slot label");
            assertEquals(
                    "施法后，聚晶不会进入冷却，改为使智慧古书进入相同时间的冷却",
                    translations.get("tooltip.goetyarkham.old_book_of_lore.redirect")
                            .getAsString(),
                    "Chinese Old Book of Lore redirect tooltip");
            assertEquals("智慧古书冷却期间，该效果不会触发",
                    translations.get("tooltip.goetyarkham.old_book_of_lore.gate")
                            .getAsString(),
                    "Chinese Old Book of Lore gate tooltip");
            assertEquals("撬锁工具",
                    translations.get("item.goetyarkham.lockpicks")
                            .getAsString(),
                    "Chinese Lockpicks name");
            assertEquals("栏位：手饰",
                    translations.get("tooltip.goetyarkham.lockpicks.slot")
                            .getAsString(),
                    "Chinese Lockpicks slot label");
            assertEquals("将你的敏捷加入你的智慧",
                    translations.get("tooltip.goetyarkham.lockpicks.effect")
                            .getAsString(),
                    "Chinese Lockpicks effect tooltip");
            assertEquals("当你进行智慧检定时，如果成功且未超过难度2点，失去1点耐久",
                    translations.get(
                            "tooltip.goetyarkham.lockpicks.durability_effect")
                            .getAsString(),
                    "Chinese Lockpicks durability-consumption tooltip");
            assertEquals("耐久：%s",
                    translations.get("tooltip.goetyarkham.lockpicks.durability")
                            .getAsString(),
                    "Chinese Lockpicks durability tooltip format");
            assertEquals("按住 Shift 查看详细效果",
                    translations.get("tooltip.goetyarkham.lockpicks.hold_shift")
                            .getAsString(),
                    "Chinese Lockpicks Shift hint");
            assertEquals("放大镜",
                    translations.get("item.goetyarkham.magnifying_glass")
                            .getAsString(),
                    "Chinese Magnifying Glass name");
            assertEquals("栏位：手饰",
                    translations.get("tooltip.goetyarkham.magnifying_glass.slot")
                            .getAsString(),
                    "Chinese Magnifying Glass slot label");
            assertEquals("+1 智慧",
                    attributeBonusText(
                            translations, 1,
                            "attribute.name.goetyarkham.intellect"),
                    "Chinese Magnifying Glass Intellect tooltip");
            assertEquals("专属弱点：",
                    translations.get(
                            SignatureWeaknessTooltipHelper.HEADING_TRANSLATION_KEY)
                            .getAsString(),
                    "Chinese signature-weakness heading");
            assertEquals("按住 Shift 查看",
                    translations.get(
                            SignatureWeaknessTooltipHelper.HOLD_SHIFT_TRANSLATION_KEY)
                            .getAsString(),
                    "Chinese shared hold-shift hint");
            assertEquals(
                    "当你将要获得一个负面状态效果时，免疫该效果，并获得1层\"孤单\"",
                    translations.get("tooltip.goetyarkham.wendys_amulet.immunity")
                            .getAsString(),
                    "Chinese Wendy's Amulet immunity tooltip");
            assertEquals("+1弱点栏位",
                    translations.get(
                            "tooltip.goetyarkham.wendys_amulet.weakness_slot")
                            .getAsString(),
                    "Chinese Wendy's Amulet slot tooltip");
            assertEquals(
                    "%s/5，当\"孤单\"达到5层时，移除所有\"孤单\"。失去2点理智",
                    translations.get("tooltip.goetyarkham.abandoned_and_alone.effect")
                            .getAsString(),
                    "Chinese Abandoned and Alone effect tooltip");
        } else if ("en_us".equals(language)) {
            assertEquals("Goety: Arkham",
                    translations.get("creativetab.goetyarkham.goety_arkham")
                            .getAsString(),
                    "English creative tab name");
            assertEquals("When worn:",
                    translations.get(CurioTooltipHelper.WHEN_WORN_TRANSLATION_KEY)
                            .getAsString(),
                    "English shared when-worn heading");
            assertEquals("Max Sanity",
                    translations.get("attribute.name.goetyarkham.max_sanity")
                            .getAsString(),
                    "English Max Sanity attribute name");
            assertEquals("Will",
                    translations.get("attribute.name.goetyarkham.willpower")
                            .getAsString(),
                    "English Will attribute name");
            assertEquals("+%1$s %2$s",
                    translations.get(CurioTooltipHelper.ATTRIBUTE_BONUS_TRANSLATION_KEY)
                            .getAsString(),
                    "English shared attribute bonus format");
            assertEquals("+%1$s%% %2$s",
                    translations.get(
                            CurioTooltipHelper.ATTRIBUTE_BONUS_PERCENT_TRANSLATION_KEY)
                            .getAsString(),
                    "English shared percent attribute bonus format");
            assertEquals("Holy Rosary",
                    translations.get("item.goetyarkham.holy_rosary").getAsString(),
                    "English Holy Rosary name");
            assertEquals("+2 Max Sanity",
                    attributeBonusText(
                            translations, 2,
                            "attribute.name.goetyarkham.max_sanity"),
                    "English Holy Rosary maximum-sanity tooltip");
            assertEquals("+1 Will",
                    attributeBonusText(
                            translations, 1,
                            "attribute.name.goetyarkham.willpower"),
                    "English Holy Rosary Will tooltip");
            assertEquals("Disc of Itzamna",
                    translations.get("item.goetyarkham.disc_of_itzamna")
                            .getAsString(),
                    "English Disc of Itzamna name");
            assertEquals(
                    "All non-boss enemies within a 10-block radius will actively avoid you.",
                    translations.get("tooltip.goetyarkham.disc_of_itzamna.effect")
                            .getAsString(),
                    "English Disc of Itzamna tooltip");
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
            assertEquals("Heirloom of Hyperborea",
                    translations.get("item.goetyarkham.heirloom_of_hyperborea")
                            .getAsString(),
                    "English Heirloom name");
            assertEquals(
                    "For every 1 Soul Energy actually spent casting a focus spell, deal 1 damage to all nearby enemies.",
                    translations.get(
                            "tooltip.goetyarkham.heirloom_of_hyperborea.damage")
                            .getAsString(),
                    "English Heirloom damage tooltip");
            assertEquals("+1 Weakness Slot",
                    translations.get(
                            "tooltip.goetyarkham.heirloom_of_hyperborea.weakness_slot")
                            .getAsString(),
                    "English Heirloom slot tooltip");
            assertEquals("Dark Memory",
                    translations.get("item.goetyarkham.dark_memory").getAsString(),
                    "English Dark Memory name");
            assertEquals("Hold Shift to view the weakness effect",
                    translations.get("tooltip.goetyarkham.dark_memory.hold_shift")
                            .getAsString(),
                    "English Dark Memory Shift hint");
            assertEquals("All players immediately trigger one Illager Treachery.",
                    translations.get(
                            "tooltip.goetyarkham.dark_memory.effect.treachery")
                            .getAsString(),
                    "English Dark Memory treachery text");
            assertEquals("Lose 2 Sanity.",
                    translations.get(
                            "tooltip.goetyarkham.dark_memory.effect.sanity")
                            .getAsString(),
                    "English Dark Memory sanity text");
            assertEquals("Rabbit’s Foot",
                    translations.get("item.goetyarkham.rabbit_foot")
                            .getAsString(),
                    "English Rabbit's Foot name");
            assertEquals("Wendy's Amulet",
                    translations.get("item.goetyarkham.wendys_amulet")
                            .getAsString(),
                    "English Wendy's Amulet name");
            assertEquals("Abandoned and Alone",
                    translations.get("item.goetyarkham.abandoned_and_alone")
                            .getAsString(),
                    "English Abandoned and Alone name");
            assertEquals("Slot: Charm",
                    translations.get("tooltip.goetyarkham.slot.charm")
                            .getAsString(),
                    "English charm slot label");
            assertEquals("Slot: Weakness",
                    translations.get("tooltip.goetyarkham.slot.weakness")
                            .getAsString(),
                    "English weakness slot label");
            assertEquals("Slot: Body",
                    translations.get("tooltip.goetyarkham.slot.body")
                            .getAsString(),
                    "English body slot label");
            assertEquals("Leather Coat",
                    translations.get("item.goetyarkham.leather_coat")
                            .getAsString(),
                    "English Leather Coat name");
            assertEquals("Bulletproof Vest",
                    translations.get("item.goetyarkham.bulletproof_vest")
                            .getAsString(),
                    "English Bulletproof Vest name");
            assertEquals("Aquinnah's Token",
                    translations.get("item.goetyarkham.aquinnahs_token")
                            .getAsString(),
                    "English Aquinnah's Token name");
            assertEquals("Slot: Token",
                    translations.get("tooltip.goetyarkham.slot.token")
                            .getAsString(),
                    "English token slot label");
            assertEquals("Hold Shift for details",
                    translations.get("tooltip.goetyarkham.aquinnahs_token.hold_shift")
                            .getAsString(),
                    "English Aquinnah's Token Shift hint");
            assertEquals(
                    "When directly attacked by a creature, loses 1"
                            + " durability, negates the attack, and redirects"
                            + " the damage to the attacker",
                    translations.get("tooltip.goetyarkham.aquinnahs_token.effect")
                            .getAsString(),
                    "English Aquinnah's Token effect tooltip");
            assertEquals("Durability depleted",
                    translations.get("tooltip.goetyarkham.aquinnahs_token.depleted")
                            .getAsString(),
                    "English Aquinnah's Token depleted tooltip");
            assertEquals("Beat Cop’s Token",
                    translations.get("item.goetyarkham.beat_cops_token")
                            .getAsString(),
                    "English Beat Cop's Token name");
            assertEquals("Hold Shift for details",
                    translations.get("tooltip.goetyarkham.beat_cops_token.hold_shift")
                            .getAsString(),
                    "English Beat Cop's Token Shift hint");
            assertEquals(
                    "Gain 1 stack of Beat Cop's Retaliation for each point"
                            + " of damage taken, up to 3 stacks",
                    translations.get(
                            "tooltip.goetyarkham.beat_cops_token.stack_gain")
                            .getAsString(),
                    "English Beat Cop's Token stack-gain tooltip");
            assertEquals(
                    "Your next successful melee attack deals 2 bonus damage"
                            + " per stack and consumes all stacks",
                    translations.get(
                            "tooltip.goetyarkham.beat_cops_token.stack_consume")
                            .getAsString(),
                    "English Beat Cop's Token stack-consume tooltip");
            assertEquals("Current retaliation: %1$s/%2$s",
                    translations.get(
                            "tooltip.goetyarkham.beat_cops_token.current_stacks")
                            .getAsString(),
                    "English Beat Cop's Token current-stacks tooltip");
            assertEquals("Arcane Initiate’s Token",
                    translations.get("item.goetyarkham.arcane_initiates_token")
                            .getAsString(),
                    "English Arcane Initiate's Token name");
            assertEquals("Gain 1 additional Focus slot",
                    translations.get(
                            "tooltip.goetyarkham.arcane_initiates_token.focus_slot")
                            .getAsString(),
                    "English Arcane Initiate's Token focus-slot tooltip");
            assertEquals("+2 Max Sanity",
                    attributeBonusText(
                            translations, 2,
                            "attribute.name.goetyarkham.max_sanity"),
                    "English Arcane Initiate's Token maximum-sanity tooltip");
            assertEquals("+2 Will",
                    attributeBonusText(
                            translations, 2,
                            "attribute.name.goetyarkham.willpower"),
                    "English Arcane Initiate's Token Will tooltip");
            assertEquals("Leo De Luca’s Token",
                    translations.get("item.goetyarkham.leo_de_lucas_token")
                            .getAsString(),
                    "English Leo De Luca's Token name");
            assertEquals("+2 Max Sanity",
                    attributeBonusText(
                            translations, 2,
                            "attribute.name.goetyarkham.max_sanity"),
                    "English Leo De Luca's Token maximum-sanity tooltip");
            assertEquals("Rita Chandler’s Token",
                    translations.get("item.goetyarkham.rita_chandlers_token")
                            .getAsString(),
                    "English Rita Chandler's Token name");
            assertEquals("+1 Strength",
                    attributeBonusText(
                            translations, 1,
                            "attribute.name.goetyarkham.strength"),
                    "English Rita Chandler's Token Strength tooltip");
            assertEquals("Hold Shift for details",
                    translations.get(
                            "tooltip.goetyarkham.rita_chandlers_token.hold_shift")
                            .getAsString(),
                    "English Rita Chandler's Token Shift hint");
            assertEquals(
                    "Players within 10 blocks of you gain +1 Strength and deal +2 damage.",
                    translations.get(
                            "tooltip.goetyarkham.rita_chandlers_token.aura_effect")
                            .getAsString(),
                    "English Rita Chandler's Token aura tooltip");
            assertEquals("This includes you.",
                    translations.get(
                            "tooltip.goetyarkham.rita_chandlers_token.aura_self")
                            .getAsString(),
                    "English Rita Chandler's Token aura self-inclusion tooltip");
            assertEquals("Rita Chandler’s Blessing",
                    translations.get("effect.goetyarkham.rita_chandlers_blessing")
                            .getAsString(),
                    "English Rita Chandler's Blessing effect name");
            assertEquals("Goety: Arkham",
                    translations.get("key.categories.goetyarkham")
                            .getAsString(),
                    "English key category name");
            assertEquals("Use Medical Texts",
                    translations.get("key.goetyarkham.medical_texts_ability")
                            .getAsString(),
                    "English Medical Texts key name");
            assertEquals("Medical Texts",
                    translations.get("item.goetyarkham.medical_texts")
                            .getAsString(),
                    "English Medical Texts name");
            assertEquals("Slot: Hands, Book",
                    translations.get("tooltip.goetyarkham.medical_texts.slot")
                            .getAsString(),
                    "English Medical Texts slot label");
            assertEquals(
                    "Press %s to perform an Intellect test (2). If successful,"
                            + " gain Instant Health I; if failed, take 1 damage.",
                    translations.get("tooltip.goetyarkham.medical_texts.effect")
                            .getAsString(),
                    "English Medical Texts effect tooltip");
            assertEquals("Book of Shadows",
                    translations.get("item.goetyarkham.book_of_shadows")
                            .getAsString(),
                    "English Book of Shadows name");
            assertEquals("Slot: Hands, Book",
                    translations.get("tooltip.goetyarkham.book_of_shadows.slot")
                            .getAsString(),
                    "English Book of Shadows slot label");
            assertEquals("Gain 1 additional Focus slot",
                    translations.get(
                            "tooltip.goetyarkham.book_of_shadows.focus_slot")
                            .getAsString(),
                    "English Book of Shadows focus-slot tooltip");
            assertEquals("Encyclopedia",
                    translations.get("item.goetyarkham.encyclopedia")
                            .getAsString(),
                    "English Encyclopedia name");
            assertEquals("Slot: Hands, Book",
                    translations.get("tooltip.goetyarkham.encyclopedia.slot")
                            .getAsString(),
                    "English Encyclopedia slot label");
            assertEquals("Gain 1 additional Bonus Slot",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.skill_slot")
                            .getAsString(),
                    "English Encyclopedia skill-slot tooltip");
            assertEquals(
                    "Each skill item in these slots grants +2 to its corresponding skill",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.skill_bonus")
                            .getAsString(),
                    "English Encyclopedia skill-bonus tooltip");
            assertEquals("Hold Shift:",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.accepted_heading")
                            .getAsString(),
                    "English Encyclopedia accepted-items heading");
            assertEquals(
                    "Accepts Iron Ingots, Rabbit's Feet, Books, and Ectoplasm.",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.accepted_items")
                            .getAsString(),
                    "English Encyclopedia accepted-items tooltip");
            assertEquals("Hold Shift for details",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.hold_shift")
                            .getAsString(),
                    "English Encyclopedia hold-shift tooltip");
            assertEquals("Old Book of Lore",
                    translations.get("item.goetyarkham.old_book_of_lore")
                            .getAsString(),
                    "English Old Book of Lore name");
            assertEquals("Slots: Hands, Book",
                    translations.get("tooltip.goetyarkham.old_book_of_lore.slot")
                            .getAsString(),
                    "English Old Book of Lore slot label");
            assertEquals(
                    "After casting, the focus does not enter cooldown. Old Book"
                            + " of Lore enters cooldown for the same duration instead",
                    translations.get("tooltip.goetyarkham.old_book_of_lore.redirect")
                            .getAsString(),
                    "English Old Book of Lore redirect tooltip");
            assertEquals(
                    "This effect does not activate while Old Book of Lore is on cooldown",
                    translations.get("tooltip.goetyarkham.old_book_of_lore.gate")
                            .getAsString(),
                    "English Old Book of Lore gate tooltip");
            assertEquals("Lockpicks",
                    translations.get("item.goetyarkham.lockpicks")
                            .getAsString(),
                    "English Lockpicks name");
            assertEquals("Slot: Hands",
                    translations.get("tooltip.goetyarkham.lockpicks.slot")
                            .getAsString(),
                    "English Lockpicks slot label");
            assertEquals("Add your Agility to your Intellect.",
                    translations.get("tooltip.goetyarkham.lockpicks.effect")
                            .getAsString(),
                    "English Lockpicks effect tooltip");
            assertEquals(
                    "When making an Intellect test, if you succeed by no more"
                            + " than 2, lose 1 durability.",
                    translations.get(
                            "tooltip.goetyarkham.lockpicks.durability_effect")
                            .getAsString(),
                    "English Lockpicks durability-consumption tooltip");
            assertEquals("Durability: %s",
                    translations.get("tooltip.goetyarkham.lockpicks.durability")
                            .getAsString(),
                    "English Lockpicks durability tooltip format");
            assertEquals("Hold Shift for details",
                    translations.get("tooltip.goetyarkham.lockpicks.hold_shift")
                            .getAsString(),
                    "English Lockpicks Shift hint");
            assertEquals("Magnifying Glass",
                    translations.get("item.goetyarkham.magnifying_glass")
                            .getAsString(),
                    "English Magnifying Glass name");
            assertEquals("Slot: Hands",
                    translations.get("tooltip.goetyarkham.magnifying_glass.slot")
                            .getAsString(),
                    "English Magnifying Glass slot label");
            assertEquals("+1 Intellect",
                    attributeBonusText(
                            translations, 1,
                            "attribute.name.goetyarkham.intellect"),
                    "English Magnifying Glass Intellect tooltip");
            assertEquals("Signature Weakness:",
                    translations.get(
                            SignatureWeaknessTooltipHelper.HEADING_TRANSLATION_KEY)
                            .getAsString(),
                    "English signature-weakness heading");
            assertEquals("Hold Shift for details",
                    translations.get(
                            SignatureWeaknessTooltipHelper.HOLD_SHIFT_TRANSLATION_KEY)
                            .getAsString(),
                    "English shared hold-shift hint");
            assertEquals(
                    "When you would gain a negative status effect, prevent it and gain 1 Loneliness.",
                    translations.get("tooltip.goetyarkham.wendys_amulet.immunity")
                            .getAsString(),
                    "English Wendy's Amulet immunity tooltip");
            assertEquals("+1 Weakness Slot",
                    translations.get(
                            "tooltip.goetyarkham.wendys_amulet.weakness_slot")
                            .getAsString(),
                    "English Wendy's Amulet slot tooltip");
            assertEquals(
                    "%s/5. When Loneliness reaches 5, remove all Loneliness and lose 2 Sanity.",
                    translations.get("tooltip.goetyarkham.abandoned_and_alone.effect")
                            .getAsString(),
                    "English Abandoned and Alone effect tooltip");
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

    private static String attributeBonusText(
            JsonObject translations, int amount, String attributeKey) {
        return translations.get(CurioTooltipHelper.ATTRIBUTE_BONUS_TRANSLATION_KEY)
                .getAsString()
                .replace("%1$s", Integer.toString(amount))
                .replace("%2$s", translations.get(attributeKey).getAsString());
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

    private static void assertResourceExists(String resourcePath)
            throws IOException {
        try (InputStream stream = CurioSlotDefinitionsSelfTest.class
                .getResourceAsStream(resourcePath)) {
            if (stream == null || stream.read() < 0) {
                throw new AssertionError("Missing or empty resource: " + resourcePath);
            }
        }
    }

    private static void assertResourceAbsent(String resourcePath) throws IOException {
        try (InputStream stream = CurioSlotDefinitionsSelfTest.class
                .getResourceAsStream(resourcePath)) {
            if (stream != null) {
                throw new AssertionError("Resource must not exist: " + resourcePath);
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
