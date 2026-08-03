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
        verifyCharmItemTag();
        verifyHandsItemTag();
        verifyHeirloomItemTags();
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
        JsonArray values = hands.getAsJsonArray("values");
        assertEquals(1, values.size(), "Holy Rosary hands tag entry count");
        assertEquals("goetyarkham:holy_rosary", values.get(0).getAsString(),
                "Holy Rosary is tagged for hands");
        for (String slotId : CurioSlotIds.ALL) {
            if (!CurioSlotIds.HANDS.equals(slotId)) {
                assertResourceValueAbsent(
                        "data/curios/tags/items/" + slotId + ".json",
                        "goetyarkham:holy_rosary");
            }
        }
        assertResourceValueAbsent(
                "data/curios/tags/items/charm.json",
                "goetyarkham:holy_rosary");
    }

    private static void verifyHeirloomItemTags() throws IOException {
        JsonObject necklace = readJson(
                "/data/curios/tags/items/necklace.json");
        assertFalse(necklace.get("replace").getAsBoolean(),
                "necklace item tag must merge without replace");
        assertEquals(List.of(
                        "goetyarkham:heirloom_of_hyperborea",
                        "goetyarkham:rabbit_foot"),
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
