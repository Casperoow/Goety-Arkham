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
            CurioSlotIds.RESOURCE, "Resource",
            CurioSlotIds.TALENT, "Talent",
            CurioSlotIds.WEAKNESS, "Weakness"
    );

    private static final Map<String, String> CHINESE_NAMES = Map.of(
            CurioSlotIds.TOKEN, "信物",
            CurioSlotIds.BOOK, "书籍",
            CurioSlotIds.FOCUS, "聚晶",
            CurioSlotIds.TAROT, "塔罗牌",
            CurioSlotIds.ASSET, "资产",
            CurioSlotIds.RESOURCE, "资源",
            CurioSlotIds.TALENT, "天赋",
            CurioSlotIds.WEAKNESS, "弱点"
    );

    private CurioSlotDefinitionsSelfTest() {
    }

    public static void main(String[] args) throws IOException {
        verifyCatalog();
        verifySlotDefinitions();
        verifyPlayerBinding();
        verifyResourceSlot();
        verifyCharmItemTag();
        verifyHandsItemTag();
        verifyBodyItemTag();
        verifyTokenItemTag();
        verifyHeirloomItemTags();
        verifyFocusItemTag();
        verifyBookItemTag();
        verifyResourceItemTag();
        verifyAssetItemTag();
        verifyBeltItemTag();
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

    /**
     * The {@code resource} slot has base size 0 and only appears once
     * another item's dynamic slot modifier grants it (see {@link
     * DynamicCurioSlotContributionService}), and must sort immediately after
     * {@code asset} in the Curios inventory screen.
     */
    private static void verifyResourceSlot() throws IOException {
        JsonObject resource = readJson("/data/goetyarkham/curios/slots/resource.json");
        assertEquals(0, resource.get("size").getAsInt(), "resource base size");
        assertEquals("goetyarkham:slot/resource",
                resource.get("icon").getAsString(),
                "resource slot icon");

        JsonObject asset = readJson("/data/goetyarkham/curios/slots/asset.json");
        assertFalse(resource.get("order").getAsInt() <= asset.get("order").getAsInt(),
                "resource slot must sort after the asset slot");

        JsonObject talent = readJson("/data/goetyarkham/curios/slots/talent.json");
        assertFalse(resource.get("order").getAsInt() >= talent.get("order").getAsInt(),
                "resource slot must sort before the talent slot");

        JsonObject binding = readJson("/data/goetyarkham/curios/entities/player.json");
        List<String> slots = binding.getAsJsonArray("slots").asList().stream()
                .map(element -> element.getAsString()).toList();
        int assetIndex = slots.indexOf(CurioSlotIds.ASSET);
        int resourceIndex = slots.indexOf(CurioSlotIds.RESOURCE);
        assertFalse(assetIndex < 0, "player binding is missing the asset slot");
        assertEquals(assetIndex + 1, resourceIndex,
                "resource slot must immediately follow asset in the player binding");

        assertResourceExists("/assets/goetyarkham/textures/slot/resource.png");
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
                        "goetyarkham:encyclopedia",
                        "goetyarkham:lockpicks",
                        "goetyarkham:magnifying_glass",
                        "goetyarkham:rolands_38_special"),
                hands.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Hands tag entries");
        for (String slotId : CurioSlotIds.ALL) {
            if (!CurioSlotIds.HANDS.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:rolands_38_special");
            }
        }
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
                        "goetyarkham:abandoned_and_alone",
                        "goetyarkham:cover_up",
                        "goetyarkham:the_necronomicon_john_dee"),
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
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:cover_up");
            }
            // The Necronomicon (John Dee) is deliberately dual-tagged: it
            // must appear in weakness and book, and nowhere else - it is
            // still always equipped in the weakness slot only (see {@link
            // com.casper.goetyarkham.item.TheNecronomiconJohnDeeItem#canEquip}).
            if (!CurioSlotIds.WEAKNESS.equals(slotId) && !CurioSlotIds.BOOK.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:the_necronomicon_john_dee");
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
                        "goetyarkham:encyclopedia",
                        "goetyarkham:the_necronomicon_john_dee"),
                book.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Book tag entries");
        assertResourceAbsent("/data/curios/tags/items/skill_bonus.json");
        assertResourceAbsent("/data/goetyarkham/curios/slots/skill_bonus.json");
        assertResourceAbsent("/assets/goetyarkham/textures/slot/skill_bonus.png");
    }

    /**
     * The Curios {@code resource} slot has base size 0 and only appears
     * once some currently equipped {@link ResourceSlotProvider} grants it
     * capacity (see {@link ResourceSlotService}). Exactly four items may
     * occupy it once granted: three vanilla items and Goety's Ectoplasm
     * (its real registry ID, not a guess from its display name), each
     * capped at a stack of 1 by server-side logic rather than the data-pack
     * tag alone.
     */
    private static void verifyResourceItemTag() throws IOException {
        JsonObject tag = readJson("/data/curios/tags/items/resource.json");
        assertFalse(tag.get("replace").getAsBoolean(),
                "resource item tag must merge without replace");
        Set<String> entries = new HashSet<>();
        tag.getAsJsonArray("values").forEach(element -> entries.add(element.getAsString()));
        assertEquals(Set.of(
                        "minecraft:iron_ingot",
                        "minecraft:rabbit_foot",
                        "minecraft:book",
                        "goety:ectoplasm"),
                entries,
                "resource item tag entries");
    }

    /**
     * The Curios {@code asset} slot has base size 4 and is bound directly
     * in the player binding (unlike {@code book}/{@code resource}). Relic
     * Hunter is the first Goety: Arkham item to occupy it; multiple copies
     * may be equipped at once (see {@link MultiEquippableCurio}), each
     * dynamically granting its own {@link CurioSlotIds#CHARM} slot rather
     * than the base charm size being edited directly - see {@link
     * com.casper.goetyarkham.item.RelicHunterService}. Charisma is the
     * second, mirroring the same mechanism but granting {@link
     * CurioSlotIds#TOKEN} instead - see {@link
     * com.casper.goetyarkham.item.CharismaService}. Physical Training is the
     * third: unlike the other two, it does not grant its own extra slot on
     * some other Curios slot - instead it participates in the shared {@link
     * CurioSlotIds#RESOURCE} pool exactly like the Encyclopedia (see {@link
     * com.casper.goetyarkham.item.PhysicalTrainingService}), just declaring
     * a minimum of 3 instead of 1.
     */
    private static void verifyAssetItemTag() throws IOException {
        JsonObject tag = readJson("/data/curios/tags/items/asset.json");
        assertFalse(tag.get("replace").getAsBoolean(),
                "asset item tag must merge without replace");
        Set<String> entries = new HashSet<>();
        tag.getAsJsonArray("values").forEach(element -> entries.add(element.getAsString()));
        assertEquals(Set.of(
                        "goetyarkham:relic_hunter",
                        "goetyarkham:charisma",
                        "goetyarkham:physical_training",
                        "goetyarkham:hard_knocks",
                        "goetyarkham:dig_deep",
                        "goetyarkham:arcane_studies",
                        "goetyarkham:hyperawareness"),
                entries,
                "asset item tag entries");
        for (String slotId : CurioSlotIds.ALL) {
            if (!CurioSlotIds.ASSET.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:relic_hunter");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:charisma");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:physical_training");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:hard_knocks");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:dig_deep");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:arcane_studies");
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:hyperawareness");
            }
        }
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:relic_hunter");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:charisma");
        assertResourceValueAbsent(
                "data/curios/tags/items/token.json",
                "goetyarkham:charisma");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:physical_training");
        assertResourceValueAbsent(
                "data/curios/tags/items/token.json",
                "goetyarkham:physical_training");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:hard_knocks");
        assertResourceValueAbsent(
                "data/curios/tags/items/token.json",
                "goetyarkham:hard_knocks");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:dig_deep");
        assertResourceValueAbsent(
                "data/curios/tags/items/token.json",
                "goetyarkham:dig_deep");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:arcane_studies");
        assertResourceValueAbsent(
                "data/curios/tags/items/token.json",
                "goetyarkham:arcane_studies");
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:hyperawareness");
        assertResourceValueAbsent(
                "data/curios/tags/items/token.json",
                "goetyarkham:hyperawareness");
    }

    /**
     * The {@code belt} Curios slot is a default slot shipped by the base
     * Curios mod itself (see {@code data/curios/curios/slots/belt.json} and
     * {@code assets/curios/textures/slot/empty_belt_slot.png} in the Curios
     * jar) and is already available to every living entity without any
     * Goety: Arkham slot definition or player-binding entry - exactly like
     * {@link CurioSlotIds#CHARM}, it is intentionally absent from {@link
     * CurioSlotIds#ALL}/{@link CurioSlotIds#BASE_SIZES}. Daisy's Tote Bag is
     * the first Goety: Arkham item to occupy it, contributed via this
     * addon-owned item tag file merging into the shared {@code curios:belt}
     * tag.
     */
    private static void verifyBeltItemTag() throws IOException {
        JsonObject belt = readJson("/data/curios/tags/items/belt.json");
        assertFalse(belt.get("replace").getAsBoolean(),
                "belt item tag must merge without replace");
        assertEquals(List.of("goetyarkham:daisys_tote_bag"),
                belt.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString()).toList(),
                "Belt tag entries");
        for (String slotId : CurioSlotIds.ALL) {
            assertResourceValueAbsent(
                    "data/curios/tags/items/" + slotId + ".json",
                    "goetyarkham:daisys_tote_bag");
        }
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:daisys_tote_bag");
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

        JsonObject rolandModel = readJson(
                "/assets/goetyarkham/models/item/rolands_38_special.json");
        assertEquals("minecraft:item/generated",
                rolandModel.get("parent").getAsString(),
                "Roland's .38 Special model parent");
        assertEquals("goetyarkham:item/rolands_38_special",
                rolandModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Roland's .38 Special model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/rolands_38_special.png");

        JsonObject coverUpModel = readJson(
                "/assets/goetyarkham/models/item/cover_up.json");
        assertEquals("minecraft:item/generated",
                coverUpModel.get("parent").getAsString(),
                "Cover Up model parent");
        assertEquals("goetyarkham:item/cover_up",
                coverUpModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Cover Up model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/cover_up.png");

        JsonObject knifeModel = readJson(
                "/assets/goetyarkham/models/item/knife.json");
        assertEquals("minecraft:item/handheld",
                knifeModel.get("parent").getAsString(),
                "Knife model parent (must render with a held-item pose,"
                        + " not minecraft:item/generated)");
        assertEquals("goetyarkham:item/knife",
                knifeModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Knife model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/knife.png");

        JsonObject relicHunterModel = readJson(
                "/assets/goetyarkham/models/item/relic_hunter.json");
        assertEquals("minecraft:item/generated",
                relicHunterModel.get("parent").getAsString(),
                "Relic Hunter model parent");
        assertEquals("goetyarkham:item/relic_hunter",
                relicHunterModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Relic Hunter model reuses the supplied texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/relic_hunter.png");

        JsonObject charismaModel = readJson(
                "/assets/goetyarkham/models/item/charisma.json");
        assertEquals("minecraft:item/generated",
                charismaModel.get("parent").getAsString(),
                "Charisma model parent");
        assertEquals("goetyarkham:item/charisma",
                charismaModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Charisma model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/charisma.png");

        JsonObject physicalTrainingModel = readJson(
                "/assets/goetyarkham/models/item/physical_training.json");
        assertEquals("minecraft:item/generated",
                physicalTrainingModel.get("parent").getAsString(),
                "Physical Training model parent");
        assertEquals("goetyarkham:item/physical_training",
                physicalTrainingModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Physical Training model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/physical_training.png");

        JsonObject hardKnocksModel = readJson(
                "/assets/goetyarkham/models/item/hard_knocks.json");
        assertEquals("minecraft:item/generated",
                hardKnocksModel.get("parent").getAsString(),
                "Hard Knocks model parent");
        assertEquals("goetyarkham:item/hard_knocks",
                hardKnocksModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Hard Knocks model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/hard_knocks.png");

        JsonObject digDeepModel = readJson(
                "/assets/goetyarkham/models/item/dig_deep.json");
        assertEquals("minecraft:item/generated",
                digDeepModel.get("parent").getAsString(),
                "Dig Deep model parent");
        assertEquals("goetyarkham:item/dig_deep",
                digDeepModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Dig Deep model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/dig_deep.png");

        JsonObject arcaneStudiesModel = readJson(
                "/assets/goetyarkham/models/item/arcane_studies.json");
        assertEquals("minecraft:item/generated",
                arcaneStudiesModel.get("parent").getAsString(),
                "Arcane Studies model parent");
        assertEquals("goetyarkham:item/arcane_studies",
                arcaneStudiesModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Arcane Studies model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/arcane_studies.png");

        JsonObject hyperawarenessModel = readJson(
                "/assets/goetyarkham/models/item/hyperawareness.json");
        assertEquals("minecraft:item/generated",
                hyperawarenessModel.get("parent").getAsString(),
                "Hyperawareness model parent");
        assertEquals("goetyarkham:item/hyperawareness",
                hyperawarenessModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Hyperawareness model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/hyperawareness.png");

        JsonObject daisysToteBagModel = readJson(
                "/assets/goetyarkham/models/item/daisys_tote_bag.json");
        assertEquals("minecraft:item/generated",
                daisysToteBagModel.get("parent").getAsString(),
                "Daisy's Tote Bag model parent");
        assertEquals("goetyarkham:item/daisys_tote_bag",
                daisysToteBagModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "Daisy's Tote Bag model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/daisys_tote_bag.png");

        JsonObject necronomiconModel = readJson(
                "/assets/goetyarkham/models/item/the_necronomicon_john_dee.json");
        assertEquals("minecraft:item/generated",
                necronomiconModel.get("parent").getAsString(),
                "The Necronomicon (John Dee) model parent");
        assertEquals("goetyarkham:item/the_necronomicon_john_dee",
                necronomiconModel.getAsJsonObject("textures")
                        .get("layer0").getAsString(),
                "The Necronomicon (John Dee) model texture");
        assertResourceExists(
                "/assets/goetyarkham/textures/item/the_necronomicon_john_dee.png");
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
            assertEquals("拥有1个资源栏位",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.resource_slot")
                            .getAsString(),
                    "Chinese Encyclopedia resource-slot tooltip");
            assertEquals("放置于该栏位的技能物品使你指定的技能+2",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.resource_bonus")
                            .getAsString(),
                    "Chinese Encyclopedia resource-bonus tooltip");
            assertEquals("按住 Shift 查看当前加成",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.hold_shift")
                            .getAsString(),
                    "Chinese Encyclopedia hold-shift tooltip");
            assertEquals("当前加成：",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.current_bonus_heading")
                            .getAsString(),
                    "Chinese Encyclopedia current-bonus heading");
            assertEquals("装备后可获得：",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.when_equipped_heading")
                            .getAsString(),
                    "Chinese Encyclopedia when-equipped heading");
            assertEquals("无",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.none")
                            .getAsString(),
                    "Chinese Encyclopedia none label");
            assertFalse(translations.has("curios.identifier.skill_bonus"),
                    "Chinese language must not retain the removed skill_bonus slot name");
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
            assertEquals("刀子",
                    translations.get("item.goetyarkham.knife").getAsString(),
                    "Chinese Knife name");
            assertEquals("宝物猎人",
                    translations.get("item.goetyarkham.relic_hunter")
                            .getAsString(),
                    "Chinese Relic Hunter name");
            assertEquals("获得额外1个护符栏位",
                    translations.get(
                            "tooltip.goetyarkham.relic_hunter.charm_slot")
                            .getAsString(),
                    "Chinese Relic Hunter charm-slot tooltip");
            assertEquals("魅力超凡",
                    translations.get("item.goetyarkham.charisma")
                            .getAsString(),
                    "Chinese Charisma name");
            assertEquals("获得额外1个信物栏位",
                    translations.get(
                            "tooltip.goetyarkham.charisma.token_slot")
                            .getAsString(),
                    "Chinese Charisma token-slot tooltip");
            assertEquals("体能训练",
                    translations.get("item.goetyarkham.physical_training")
                            .getAsString(),
                    "Chinese Physical Training name");
            assertEquals("栏位：资产",
                    translations.get("tooltip.goetyarkham.physical_training.slot")
                            .getAsString(),
                    "Chinese Physical Training slot label");
            assertEquals("拥有3个资源栏位，放置于该栏位的灵质或铁锭使你指定的技能+1",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.resource_bonus")
                            .getAsString(),
                    "Chinese Physical Training resource-bonus tooltip");
            assertEquals("按住 Shift 查看当前加成",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.hold_shift")
                            .getAsString(),
                    "Chinese Physical Training hold-shift tooltip");
            assertEquals("当前加成：",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.current_bonus_heading")
                            .getAsString(),
                    "Chinese Physical Training current-bonus heading");
            assertEquals("装备后可获得：",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.when_equipped_heading")
                            .getAsString(),
                    "Chinese Physical Training when-equipped heading");
            assertEquals("无",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.none")
                            .getAsString(),
                    "Chinese Physical Training none label");
            assertEquals("沉重打击",
                    translations.get("item.goetyarkham.hard_knocks")
                            .getAsString(),
                    "Chinese Hard Knocks name");
            assertEquals("栏位：资产",
                    translations.get("tooltip.goetyarkham.hard_knocks.slot")
                            .getAsString(),
                    "Chinese Hard Knocks slot label");
            assertEquals("拥有3个资源栏位，放置于该栏位的铁锭或兔子腿使你指定的技能+1",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.resource_bonus")
                            .getAsString(),
                    "Chinese Hard Knocks resource-bonus tooltip");
            assertEquals("按住 Shift 查看当前加成",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.hold_shift")
                            .getAsString(),
                    "Chinese Hard Knocks hold-shift tooltip");
            assertEquals("当前加成：",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.current_bonus_heading")
                            .getAsString(),
                    "Chinese Hard Knocks current-bonus heading");
            assertEquals("装备后可获得：",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.when_equipped_heading")
                            .getAsString(),
                    "Chinese Hard Knocks when-equipped heading");
            assertEquals("无",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.none")
                            .getAsString(),
                    "Chinese Hard Knocks none label");
            assertEquals("深挖",
                    translations.get("item.goetyarkham.dig_deep")
                            .getAsString(),
                    "Chinese Dig Deep name");
            assertEquals("栏位：资产",
                    translations.get("tooltip.goetyarkham.dig_deep.slot")
                            .getAsString(),
                    "Chinese Dig Deep slot label");
            assertEquals("拥有3个资源栏位，放置于该栏位的灵质或兔子腿使你指定的技能+1",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.resource_bonus")
                            .getAsString(),
                    "Chinese Dig Deep resource-bonus tooltip");
            assertEquals("按住 Shift 查看当前加成",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.hold_shift")
                            .getAsString(),
                    "Chinese Dig Deep hold-shift tooltip");
            assertEquals("当前加成：",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.current_bonus_heading")
                            .getAsString(),
                    "Chinese Dig Deep current-bonus heading");
            assertEquals("装备后可获得：",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.when_equipped_heading")
                            .getAsString(),
                    "Chinese Dig Deep when-equipped heading");
            assertEquals("无",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.none")
                            .getAsString(),
                    "Chinese Dig Deep none label");
            assertEquals("神秘研究",
                    translations.get("item.goetyarkham.arcane_studies")
                            .getAsString(),
                    "Chinese Arcane Studies name");
            assertEquals("栏位：资产",
                    translations.get("tooltip.goetyarkham.arcane_studies.slot")
                            .getAsString(),
                    "Chinese Arcane Studies slot label");
            assertEquals("拥有3个资源栏位，放置于该栏位的灵质或书使你指定的技能+1",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.resource_bonus")
                            .getAsString(),
                    "Chinese Arcane Studies resource-bonus tooltip");
            assertEquals("按住 Shift 查看当前加成",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.hold_shift")
                            .getAsString(),
                    "Chinese Arcane Studies hold-shift tooltip");
            assertEquals("当前加成：",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.current_bonus_heading")
                            .getAsString(),
                    "Chinese Arcane Studies current-bonus heading");
            assertEquals("装备后可获得：",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.when_equipped_heading")
                            .getAsString(),
                    "Chinese Arcane Studies when-equipped heading");
            assertEquals("无",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.none")
                            .getAsString(),
                    "Chinese Arcane Studies none label");
            assertEquals("超感知",
                    translations.get("item.goetyarkham.hyperawareness")
                            .getAsString(),
                    "Chinese Hyperawareness name");
            assertEquals("栏位：资产",
                    translations.get("tooltip.goetyarkham.hyperawareness.slot")
                            .getAsString(),
                    "Chinese Hyperawareness slot label");
            assertEquals("拥有3个资源栏位，放置于该栏位的书或兔子腿使你指定的技能+1",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.resource_bonus")
                            .getAsString(),
                    "Chinese Hyperawareness resource-bonus tooltip");
            assertEquals("按住 Shift 查看当前加成",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.hold_shift")
                            .getAsString(),
                    "Chinese Hyperawareness hold-shift tooltip");
            assertEquals("当前加成：",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.current_bonus_heading")
                            .getAsString(),
                    "Chinese Hyperawareness current-bonus heading");
            assertEquals("装备后可获得：",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.when_equipped_heading")
                            .getAsString(),
                    "Chinese Hyperawareness when-equipped heading");
            assertEquals("无",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.none")
                            .getAsString(),
                    "Chinese Hyperawareness none label");
            assertEquals("黛西的手提包",
                    translations.get("item.goetyarkham.daisys_tote_bag")
                            .getAsString(),
                    "Chinese Daisy's Tote Bag name");
            assertEquals("你额外获得2个书籍栏位",
                    translations.get(
                            "tooltip.goetyarkham.daisys_tote_bag.book_slot")
                            .getAsString(),
                    "Chinese Daisy's Tote Bag book-slot tooltip");
            assertEquals("你每佩戴一个书籍，你获得1点智慧",
                    translations.get(
                            "tooltip.goetyarkham.daisys_tote_bag.book_bonus")
                            .getAsString(),
                    "Chinese Daisy's Tote Bag book-bonus tooltip");
            assertEquals("死灵之书 (John Dee)",
                    translations.get(
                            "item.goetyarkham.the_necronomicon_john_dee")
                            .getAsString(),
                    "Chinese The Necronomicon (John Dee) name");
            assertEquals("栏位：弱点、书籍",
                    translations.get(
                            "tooltip.goetyarkham.the_necronomicon_john_dee.slot")
                            .getAsString(),
                    "Chinese Necronomicon slot label");
            assertEquals("你受到3点理智损伤",
                    translations.get(
                            "tooltip.goetyarkham.the_necronomicon_john_dee.effect")
                            .getAsString(),
                    "Chinese Necronomicon effect tooltip");
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
            assertEquals("Ensures you have at least 1 Resource slot",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.resource_slot")
                            .getAsString(),
                    "English Encyclopedia resource-slot tooltip");
            assertEquals(
                    "Skill items placed in Resource slots grant +2 to their corresponding skill",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.resource_bonus")
                            .getAsString(),
                    "English Encyclopedia resource-bonus tooltip");
            assertEquals("Hold Shift to view current bonuses",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.hold_shift")
                            .getAsString(),
                    "English Encyclopedia hold-shift tooltip");
            assertEquals("Current bonus:",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.current_bonus_heading")
                            .getAsString(),
                    "English Encyclopedia current-bonus heading");
            assertEquals("When equipped, you would gain:",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.when_equipped_heading")
                            .getAsString(),
                    "English Encyclopedia when-equipped heading");
            assertEquals("None",
                    translations.get(
                            "tooltip.goetyarkham.encyclopedia.none")
                            .getAsString(),
                    "English Encyclopedia none label");
            assertFalse(translations.has("curios.identifier.skill_bonus"),
                    "English language must not retain the removed skill_bonus slot name");
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
            assertEquals("Knife",
                    translations.get("item.goetyarkham.knife").getAsString(),
                    "English Knife name");
            assertEquals("Relic Hunter",
                    translations.get("item.goetyarkham.relic_hunter")
                            .getAsString(),
                    "English Relic Hunter name");
            assertEquals("Gain 1 additional Charm slot",
                    translations.get(
                            "tooltip.goetyarkham.relic_hunter.charm_slot")
                            .getAsString(),
                    "English Relic Hunter charm-slot tooltip");
            assertEquals("Charisma",
                    translations.get("item.goetyarkham.charisma")
                            .getAsString(),
                    "English Charisma name");
            assertEquals("Gain 1 additional Token slot",
                    translations.get(
                            "tooltip.goetyarkham.charisma.token_slot")
                            .getAsString(),
                    "English Charisma token-slot tooltip");
            assertEquals("Physical Training",
                    translations.get("item.goetyarkham.physical_training")
                            .getAsString(),
                    "English Physical Training name");
            assertEquals("Slot: Asset",
                    translations.get("tooltip.goetyarkham.physical_training.slot")
                            .getAsString(),
                    "English Physical Training slot label");
            assertEquals(
                    "You have 3 Resource slots. Ectoplasm or Iron Ingots placed"
                            + " in these slots grant +1 to their corresponding skill.",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.resource_bonus")
                            .getAsString(),
                    "English Physical Training resource-bonus tooltip");
            assertEquals("Hold Shift to view current bonuses",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.hold_shift")
                            .getAsString(),
                    "English Physical Training hold-shift tooltip");
            assertEquals("Current bonus:",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.current_bonus_heading")
                            .getAsString(),
                    "English Physical Training current-bonus heading");
            assertEquals("When equipped, you would gain:",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.when_equipped_heading")
                            .getAsString(),
                    "English Physical Training when-equipped heading");
            assertEquals("None",
                    translations.get(
                            "tooltip.goetyarkham.physical_training.none")
                            .getAsString(),
                    "English Physical Training none label");
            assertEquals("Hard Knocks",
                    translations.get("item.goetyarkham.hard_knocks")
                            .getAsString(),
                    "English Hard Knocks name");
            assertEquals("Slot: Asset",
                    translations.get("tooltip.goetyarkham.hard_knocks.slot")
                            .getAsString(),
                    "English Hard Knocks slot label");
            assertEquals(
                    "You have 3 Resource slots. Iron Ingots or Rabbit's Feet placed"
                            + " in these slots grant +1 to their corresponding skill.",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.resource_bonus")
                            .getAsString(),
                    "English Hard Knocks resource-bonus tooltip");
            assertEquals("Hold Shift to view current bonuses",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.hold_shift")
                            .getAsString(),
                    "English Hard Knocks hold-shift tooltip");
            assertEquals("Current bonus:",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.current_bonus_heading")
                            .getAsString(),
                    "English Hard Knocks current-bonus heading");
            assertEquals("When equipped, you would gain:",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.when_equipped_heading")
                            .getAsString(),
                    "English Hard Knocks when-equipped heading");
            assertEquals("None",
                    translations.get(
                            "tooltip.goetyarkham.hard_knocks.none")
                            .getAsString(),
                    "English Hard Knocks none label");
            assertEquals("Dig Deep",
                    translations.get("item.goetyarkham.dig_deep")
                            .getAsString(),
                    "English Dig Deep name");
            assertEquals("Slot: Asset",
                    translations.get("tooltip.goetyarkham.dig_deep.slot")
                            .getAsString(),
                    "English Dig Deep slot label");
            assertEquals(
                    "You have 3 Resource slots. Ectoplasm or Rabbit's Feet placed"
                            + " in these slots grant +1 to their corresponding skill.",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.resource_bonus")
                            .getAsString(),
                    "English Dig Deep resource-bonus tooltip");
            assertEquals("Hold Shift to view current bonuses",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.hold_shift")
                            .getAsString(),
                    "English Dig Deep hold-shift tooltip");
            assertEquals("Current bonus:",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.current_bonus_heading")
                            .getAsString(),
                    "English Dig Deep current-bonus heading");
            assertEquals("When equipped, you would gain:",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.when_equipped_heading")
                            .getAsString(),
                    "English Dig Deep when-equipped heading");
            assertEquals("None",
                    translations.get(
                            "tooltip.goetyarkham.dig_deep.none")
                            .getAsString(),
                    "English Dig Deep none label");
            assertEquals("Arcane Studies",
                    translations.get("item.goetyarkham.arcane_studies")
                            .getAsString(),
                    "English Arcane Studies name");
            assertEquals("Slot: Asset",
                    translations.get("tooltip.goetyarkham.arcane_studies.slot")
                            .getAsString(),
                    "English Arcane Studies slot label");
            assertEquals(
                    "You have 3 Resource slots. Ectoplasm or Books placed"
                            + " in these slots grant +1 to their corresponding skill.",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.resource_bonus")
                            .getAsString(),
                    "English Arcane Studies resource-bonus tooltip");
            assertEquals("Hold Shift to view current bonuses",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.hold_shift")
                            .getAsString(),
                    "English Arcane Studies hold-shift tooltip");
            assertEquals("Current bonus:",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.current_bonus_heading")
                            .getAsString(),
                    "English Arcane Studies current-bonus heading");
            assertEquals("When equipped, you would gain:",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.when_equipped_heading")
                            .getAsString(),
                    "English Arcane Studies when-equipped heading");
            assertEquals("None",
                    translations.get(
                            "tooltip.goetyarkham.arcane_studies.none")
                            .getAsString(),
                    "English Arcane Studies none label");
            assertEquals("Hyperawareness",
                    translations.get("item.goetyarkham.hyperawareness")
                            .getAsString(),
                    "English Hyperawareness name");
            assertEquals("Slot: Asset",
                    translations.get("tooltip.goetyarkham.hyperawareness.slot")
                            .getAsString(),
                    "English Hyperawareness slot label");
            assertEquals(
                    "You have 3 Resource slots. Books or Rabbit's Feet placed"
                            + " in these slots grant +1 to their corresponding skill.",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.resource_bonus")
                            .getAsString(),
                    "English Hyperawareness resource-bonus tooltip");
            assertEquals("Hold Shift to view current bonuses",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.hold_shift")
                            .getAsString(),
                    "English Hyperawareness hold-shift tooltip");
            assertEquals("Current bonus:",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.current_bonus_heading")
                            .getAsString(),
                    "English Hyperawareness current-bonus heading");
            assertEquals("When equipped, you would gain:",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.when_equipped_heading")
                            .getAsString(),
                    "English Hyperawareness when-equipped heading");
            assertEquals("None",
                    translations.get(
                            "tooltip.goetyarkham.hyperawareness.none")
                            .getAsString(),
                    "English Hyperawareness none label");
            assertEquals("Daisy's Tote Bag",
                    translations.get("item.goetyarkham.daisys_tote_bag")
                            .getAsString(),
                    "English Daisy's Tote Bag name");
            assertEquals("Gain 2 additional Book slots",
                    translations.get(
                            "tooltip.goetyarkham.daisys_tote_bag.book_slot")
                            .getAsString(),
                    "English Daisy's Tote Bag book-slot tooltip");
            assertEquals("For every Book you have equipped, gain 1 Intellect",
                    translations.get(
                            "tooltip.goetyarkham.daisys_tote_bag.book_bonus")
                            .getAsString(),
                    "English Daisy's Tote Bag book-bonus tooltip");
            assertEquals("The Necronomicon (John Dee)",
                    translations.get(
                            "item.goetyarkham.the_necronomicon_john_dee")
                            .getAsString(),
                    "English The Necronomicon (John Dee) name");
            assertEquals("Slot: Weakness, Book",
                    translations.get(
                            "tooltip.goetyarkham.the_necronomicon_john_dee.slot")
                            .getAsString(),
                    "English Necronomicon slot label");
            assertEquals("You suffer 3 sanity trauma.",
                    translations.get(
                            "tooltip.goetyarkham.the_necronomicon_john_dee.effect")
                            .getAsString(),
                    "English Necronomicon effect tooltip");
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
