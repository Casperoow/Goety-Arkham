package com.casper.goetyarkham.item;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.curios.CurioSlotIds;
import com.casper.goetyarkham.sanity.SanityChangeCause;
import com.casper.goetyarkham.sanity.SanityService;
import com.casper.goetyarkham.stats.PlayerStatsService;
import com.casper.goetyarkham.stats.StatType;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.UUID;

@GameTestHolder(GoetyArkham.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DaisysToteBagGameTests {
    private static final String BATCH = "goetyarkham:daisys_tote_bag";
    private static final UUID OTHER_BOOK_SOURCE_ID = UUID.fromString(
            "11111111-2222-4333-8444-555555555555");

    private DaisysToteBagGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void bookSlotGrantsExactlyTwoAndStacksWithOtherSources(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "daisy-book-slot", 1.5D);
        ICuriosItemHandler inventory = inventory(player, helper);
        try {
            ICurioStacksHandler book = handler(player, CurioSlotIds.BOOK, helper);
            int baseSlots = book.getStacks().getSlots();
            helper.assertTrue(baseSlots == 0,
                    "Book slot did not start at its base size of 0");

            // Simulate an unrelated item independently granting +1 book slot.
            inventory.addPermanentSlotModifier(
                    CurioSlotIds.BOOK, OTHER_BOOK_SOURCE_ID, "test:other_book_source",
                    1.0D, AttributeModifier.Operation.ADDITION);
            book.getSlots();
            helper.assertTrue(book.getStacks().getSlots() == baseSlots + 1,
                    "Synthetic other book-slot source did not grant exactly +1");

            equipDaisy(player, helper);
            book = handler(player, CurioSlotIds.BOOK, helper);
            helper.assertTrue(book.getStacks().getSlots() == baseSlots + 1 + 2,
                    "Daisy's Tote Bag did not stack its own +2 on top of the"
                            + " other book-slot source's +1");

            unequipDaisy(player, helper);
            book = handler(player, CurioSlotIds.BOOK, helper);
            helper.assertTrue(book.getStacks().getSlots() == baseSlots + 1,
                    "Unequipping Daisy's Tote Bag removed more than its own"
                            + " +2 book-slot contribution");
            helper.succeed();
        } finally {
            unequipDaisy(player, helper);
            inventory.removeSlotModifier(CurioSlotIds.BOOK, OTHER_BOOK_SOURCE_ID);
            handler(player, CurioSlotIds.BOOK, helper).getSlots();
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void bookBonusTracksLiveEquippedBookCountIncludingNecronomicon(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer player = testPlayer(level, "daisy-book-bonus", 1.5D);
        try {
            helper.assertTrue(DaisysToteBagService.countEquippedBooks(player) == 0,
                    "A player with nothing equipped counted a nonzero number of books");
            int baseIntellect = PlayerStatsService.getFinalValue(player, StatType.INTELLECT);

            equipDaisy(player, helper);
            // Wearing Daisy's Tote Bag auto-equips its own signature weakness,
            // The Necronomicon (John Dee), which itself carries the book tag
            // and sits in the weakness slot - that alone counts as 1 book.
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT)
                            == baseIntellect + 1,
                    "Daisy's Tote Bag's own auto-equipped Necronomicon did not"
                            + " contribute exactly +1 Intellect as an equipped book");

            equipBookOfShadows(player, helper);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT)
                            == baseIntellect + 2,
                    "Equipping a second book did not raise the bonus to +2");

            equipMedicalTexts(player, helper);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT)
                            == baseIntellect + 3,
                    "Equipping a third book did not raise the bonus to +3");

            unequipBookOfShadows(player, helper);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT)
                            == baseIntellect + 2,
                    "Unequipping one book did not immediately lower the bonus to +2");

            equipBookOfShadows(player, helper);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT)
                            == baseIntellect + 3,
                    "Re-equipping the book did not immediately restore the bonus to +3");

            unequipDaisy(player, helper);
            helper.assertTrue(
                    PlayerStatsService.getFinalValue(player, StatType.INTELLECT)
                            == baseIntellect,
                    "Unequipping Daisy's Tote Bag did not remove every bit of"
                            + " its book-count Intellect bonus");
            helper.succeed();
        } finally {
            unequipDaisy(player, helper);
            player.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void signatureWeaknessEquipsOnlyInWeaknessSlotAndPreservesOtherWeaknesses(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer first = testPlayer(level, "daisy-weakness-first", 1.5D);
        TestPlayer second = testPlayer(level, "daisy-weakness-second", 4.5D);
        try {
            ICurioStacksHandler firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            ICurioStacksHandler secondWeakness = handler(second, CurioSlotIds.WEAKNESS, helper);
            int firstBaseSlots = firstWeakness.getStacks().getSlots();
            int secondBaseSlots = secondWeakness.getStacks().getSlots();

            // A pre-existing, unrelated weakness in the player's base slot
            // must never be touched by Daisy's Tote Bag.
            firstWeakness.getStacks().setStackInSlot(
                    0, new ItemStack(ModItems.COVER_UP.get()));

            equipDaisy(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots() == firstBaseSlots + 1,
                    "Daisy's Tote Bag did not add exactly one weakness slot");
            helper.assertTrue(countBoundNecronomicons(firstWeakness, first) == 1,
                    "Daisy's Tote Bag did not equip exactly one bound Necronomicon");
            helper.assertTrue(firstWeakness.getStacks().getStackInSlot(0)
                            .is(ModItems.COVER_UP.get()),
                    "Daisy's Tote Bag overwrote the player's pre-existing weakness");
            helper.assertTrue(handler(second, CurioSlotIds.WEAKNESS, helper)
                            .getStacks().getSlots() == secondBaseSlots,
                    "First player's Tote Bag changed the second player's slots");

            ICurioStacksHandler book = handler(first, CurioSlotIds.BOOK, helper);
            for (int slot = 0; slot < book.getStacks().getSlots(); slot++) {
                helper.assertTrue(
                        !book.getStacks().getStackInSlot(slot)
                                .is(ModItems.THE_NECRONOMICON_JOHN_DEE.get()),
                        "The auto-equipped Necronomicon was placed in the book"
                                + " slot instead of the weakness slot");
            }

            assertManualRemovalLocked(helper, first, firstWeakness);

            equipDaisy(second, helper);
            secondWeakness = handler(second, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(secondWeakness.getStacks().getSlots() == secondBaseSlots + 1
                            && countBoundNecronomicons(secondWeakness, second) == 1,
                    "Second wearer did not receive its own bound Necronomicon");

            unequipDaisy(first, helper);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(firstWeakness.getStacks().getSlots() == firstBaseSlots,
                    "Removing Daisy's Tote Bag did not remove its weakness slot");
            helper.assertTrue(countBoundNecronomicons(firstWeakness, first) == 0,
                    "Removing Daisy's Tote Bag did not delete its bound Necronomicon");
            helper.assertTrue(firstWeakness.getStacks().getStackInSlot(0)
                            .is(ModItems.COVER_UP.get()),
                    "Removing Daisy's Tote Bag disturbed the player's other weakness");
            helper.assertTrue(countBoundNecronomicons(
                            handler(second, CurioSlotIds.WEAKNESS, helper), second) == 1,
                    "Removing first player's Tote Bag deleted second player's Necronomicon");
            helper.succeed();
        } finally {
            unequipDaisy(first, helper);
            unequipDaisy(second, helper);
            first.discard();
            second.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void permanentSanityLossTracksActualContributionAndNeverOverRestores(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer zeroBaseline = testPlayer(level, "daisy-sanity-zero", 1.5D);
        TestPlayer sevenBaseline = testPlayer(level, "daisy-sanity-seven", 4.5D);
        TestPlayer eightBaseline = testPlayer(level, "daisy-sanity-eight", 7.5D);
        TestPlayer nineBaseline = testPlayer(level, "daisy-sanity-nine", 10.5D);
        try {
            // Case A: 0 -> 3 -> 0.
            equipDaisy(zeroBaseline, helper);
            helper.assertTrue(SanityService.getPermanentMaxLoss(zeroBaseline) == 3,
                    "Fresh permanent loss of 0 did not become exactly 3 after equipping");
            unequipDaisy(zeroBaseline, helper);
            helper.assertTrue(SanityService.getPermanentMaxLoss(zeroBaseline) == 0,
                    "Unequipping did not restore permanent loss back to exactly 0");

            // Case B: 7 -> 9 (contributes 2) -> 7.
            SanityService.addPermanentMaxLoss(sevenBaseline, 7, SanityChangeCause.OTHER);
            equipDaisy(sevenBaseline, helper);
            helper.assertTrue(SanityService.getPermanentMaxLoss(sevenBaseline) == 9,
                    "Permanent loss of 7 did not cap at exactly 9 after equipping");
            unequipDaisy(sevenBaseline, helper);
            helper.assertTrue(SanityService.getPermanentMaxLoss(sevenBaseline) == 7,
                    "Unequipping restored more than the 2 points this Necronomicon"
                            + " actually contributed, corrupting the other-source 7");

            // Case C: 8 -> 9 (contributes 1) -> 8.
            SanityService.addPermanentMaxLoss(eightBaseline, 8, SanityChangeCause.OTHER);
            equipDaisy(eightBaseline, helper);
            helper.assertTrue(SanityService.getPermanentMaxLoss(eightBaseline) == 9,
                    "Permanent loss of 8 did not cap at exactly 9 after equipping");
            unequipDaisy(eightBaseline, helper);
            helper.assertTrue(SanityService.getPermanentMaxLoss(eightBaseline) == 8,
                    "Unequipping restored more than the 1 point this Necronomicon"
                            + " actually contributed");

            // Case D: 9 -> 9 (contributes 0) -> 9, and an unrelated restoration
            // in between must never be topped up by a later unequip.
            SanityService.addPermanentMaxLoss(nineBaseline, 9, SanityChangeCause.OTHER);
            equipDaisy(nineBaseline, helper);
            helper.assertTrue(SanityService.getPermanentMaxLoss(nineBaseline) == 9,
                    "Permanent loss of 9 changed after equipping an already-full pool");
            SanityService.restorePermanentMaxLoss(nineBaseline, 5, SanityChangeCause.OTHER);
            int afterUnrelatedRestore = SanityService.getPermanentMaxLoss(nineBaseline);
            helper.assertTrue(afterUnrelatedRestore == 4,
                    "Test setup did not produce the expected post-restore baseline of 4");
            unequipDaisy(nineBaseline, helper);
            helper.assertTrue(SanityService.getPermanentMaxLoss(nineBaseline)
                            == afterUnrelatedRestore,
                    "Unequipping a Necronomicon that contributed 0 granted a free"
                            + " restoration on top of an unrelated source's recovery");
            helper.succeed();
        } finally {
            unequipDaisy(zeroBaseline, helper);
            unequipDaisy(sevenBaseline, helper);
            unequipDaisy(eightBaseline, helper);
            unequipDaisy(nineBaseline, helper);
            zeroBaseline.discard();
            sevenBaseline.discard();
            eightBaseline.discard();
            nineBaseline.discard();
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void reconcileLoginCloneDimensionNeverDuplicatesNecronomiconOrLoss(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer first = testPlayer(level, "daisy-reconcile-first", 1.5D);
        TestPlayer second = testPlayer(level, "daisy-reconcile-second", 4.5D);
        try {
            equipDaisy(first, helper);
            int settledLoss = SanityService.getPermanentMaxLoss(first);
            ICurioStacksHandler firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            int baseSlots = firstWeakness.getStacks().getSlots() - 1;

            // Repeated login/dimension-change/Curios-sync reconciliation.
            DaisysToteBagService.reconcile(first);
            DaisysToteBagService.reconcile(first);
            DaisysToteBagService.reconcile(first);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(countBoundNecronomicons(firstWeakness, first) == 1,
                    "Repeated reconciliation duplicated the Necronomicon");
            helper.assertTrue(firstWeakness.getStacks().getSlots() == baseSlots + 1,
                    "Repeated reconciliation duplicated the weakness slot");
            helper.assertTrue(SanityService.getPermanentMaxLoss(first) == settledLoss,
                    "Repeated reconciliation re-applied permanent Sanity loss");
            helper.assertTrue(handler(first, CurioSlotIds.BOOK, helper)
                            .getStacks().getSlots() == 2,
                    "Repeated reconciliation changed the granted book-slot count");

            // Simulate the Necronomicon's ItemStack representation vanishing
            // (e.g. an edited save or a dimension round trip that lost it)
            // while the service's own state remains active.
            int necronomiconSlot = boundNecronomiconSlot(firstWeakness, first);
            helper.assertTrue(necronomiconSlot >= 0, "No bound Necronomicon to clear");
            firstWeakness.getStacks().setStackInSlot(necronomiconSlot, ItemStack.EMPTY);
            DaisysToteBagService.reconcile(first);
            firstWeakness = handler(first, CurioSlotIds.WEAKNESS, helper);
            helper.assertTrue(countBoundNecronomicons(firstWeakness, first) == 1,
                    "Reconcile did not restore a missing bound Necronomicon");
            helper.assertTrue(SanityService.getPermanentMaxLoss(first) == settledLoss,
                    "Restoring a missing Necronomicon inflicted a second round of"
                            + " permanent Sanity loss");

            // Dimension change: an NBT round trip of the bound stack itself
            // (standing in for a full save/load across dimensions) preserves
            // its identity and applied-loss bookkeeping.
            int slot = boundNecronomiconSlot(firstWeakness, first);
            ItemStack original = firstWeakness.getStacks().getStackInSlot(slot);
            ItemStack roundTripped = ItemStack.of(original.save(new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(
                    TheNecronomiconJohnDeeItem.isToteBagBound(roundTripped, first.getUUID())
                            && TheNecronomiconJohnDeeItem.getAppliedPermanentLoss(roundTripped)
                            == TheNecronomiconJohnDeeItem.getAppliedPermanentLoss(original),
                    "An NBT serialize/deserialize round trip lost the Necronomicon's"
                            + " binding or its recorded applied-loss contribution");

            // Simulated relog/clone restoration onto a fresh player instance,
            // with the separately-cloned Sanity capability emulated exactly
            // as the real clone path would already have applied it.
            TestPlayer restored = testPlayer(level, "daisy-reconcile-restored", 7.5D);
            try {
                int appliedLoss = TheNecronomiconJohnDeeItem.getAppliedPermanentLoss(original);
                SanityService.addPermanentMaxLoss(
                        restored, appliedLoss, SanityChangeCause.OTHER);
                DaisysToteBagService.copyPersistentState(first, restored);
                equipDaisy(restored, helper);
                helper.assertTrue(countBoundNecronomicons(
                                handler(restored, CurioSlotIds.WEAKNESS, helper),
                                restored) == 1,
                        "Restored player did not retain exactly one bound Necronomicon");
                helper.assertTrue(SanityService.getPermanentMaxLoss(restored) == appliedLoss,
                        "Restoring cloned state re-applied permanent Sanity loss"
                                + " on top of the already-cloned amount");
            } finally {
                unequipDaisy(restored, helper);
                restored.discard();
            }

            // Multi-player isolation: none of the above touched the second player.
            helper.assertTrue(DaisysToteBagService.isActive(second) == false
                            && SanityService.getPermanentMaxLoss(second) == 0,
                    "First player's reconciliation affected an unrelated second player");
            helper.succeed();
        } finally {
            unequipDaisy(first, helper);
            unequipDaisy(second, helper);
            first.discard();
            second.discard();
        }
    }

    private static void assertManualRemovalLocked(
            GameTestHelper helper,
            TestPlayer player,
            ICurioStacksHandler weaknesses) {
        int slot = boundNecronomiconSlot(weaknesses, player);
        helper.assertTrue(slot >= 0, "No bound Necronomicon to lock-test");
        ItemStack stack = weaknesses.getStacks().getStackInSlot(slot);
        SlotContext context = new SlotContext(
                CurioSlotIds.WEAKNESS, player, slot, false, true);
        ICurio curio = CuriosApi.getCurio(stack).resolve().orElse(null);
        helper.assertTrue(curio != null && !curio.canUnequip(context),
                "The Necronomicon did not reject manual unequip");
        helper.assertTrue(curio.getDropRule(
                        context, player.damageSources().generic(), 0, false)
                        == ICurio.DropRule.ALWAYS_KEEP,
                "The Necronomicon is not protected from death drops");
        helper.assertTrue(weaknesses.getStacks()
                        .extractItem(slot, 1, false).isEmpty(),
                "Survival extraction removed the locked Necronomicon");
        player.getAbilities().instabuild = true;
        helper.assertTrue(weaknesses.getStacks()
                        .extractItem(slot, 1, false).isEmpty(),
                "Creative extraction removed the locked Necronomicon");
        player.getAbilities().instabuild = false;
        helper.assertTrue(countBoundNecronomicons(weaknesses, player) == 1,
                "Lock test changed the bound Necronomicon count");
    }

    private static int countBoundNecronomicons(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        int count = 0;
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (TheNecronomiconJohnDeeItem.isToteBagBound(
                    weaknesses.getStacks().getStackInSlot(slot), owner.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static int boundNecronomiconSlot(
            ICurioStacksHandler weaknesses, ServerPlayer owner) {
        for (int slot = 0; slot < weaknesses.getStacks().getSlots(); slot++) {
            if (TheNecronomiconJohnDeeItem.isToteBagBound(
                    weaknesses.getStacks().getStackInSlot(slot), owner.getUUID())) {
                return slot;
            }
        }
        return -1;
    }

    private static TestPlayer testPlayer(ServerLevel level, String name, double x) {
        TestPlayer player = new TestPlayer(level, name);
        player.setPos(x, 1.0D, 1.5D);
        return player;
    }

    private static ICuriosItemHandler inventory(ServerPlayer player, GameTestHelper helper) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        helper.assertTrue(inventory != null, "Missing Curios inventory");
        return inventory;
    }

    private static ICurioStacksHandler handler(
            ServerPlayer player, String slot, GameTestHelper helper) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inv -> inv.getStacksHandler(slot))
                .orElse(null);
        helper.assertTrue(handler != null, "Missing Curios handler: " + slot);
        return handler;
    }

    private static void equipDaisy(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler belt = handler(player, CurioSlotIds.BELT, helper);
        boolean alreadyWorn = false;
        for (int slot = 0; slot < belt.getStacks().getSlots(); slot++) {
            if (belt.getStacks().getStackInSlot(slot).is(ModItems.DAISYS_TOTE_BAG.get())) {
                alreadyWorn = true;
            }
        }
        if (!alreadyWorn) {
            belt.getStacks().setStackInSlot(0, new ItemStack(ModItems.DAISYS_TOTE_BAG.get()));
            settleCurioChange(player);
        }
    }

    private static void unequipDaisy(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler belt = handler(player, CurioSlotIds.BELT, helper);
        for (int slot = 0; slot < belt.getStacks().getSlots(); slot++) {
            if (belt.getStacks().getStackInSlot(slot).is(ModItems.DAISYS_TOTE_BAG.get())) {
                belt.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        settleCurioChange(player);
    }

    private static void equipBookOfShadows(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler book = handler(player, CurioSlotIds.BOOK, helper);
        int slot = firstEmptySlot(book, helper);
        book.getStacks().setStackInSlot(slot, new ItemStack(ModItems.BOOK_OF_SHADOWS.get()));
        settleCurioChange(player);
    }

    private static void unequipBookOfShadows(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler book = handler(player, CurioSlotIds.BOOK, helper);
        for (int slot = 0; slot < book.getStacks().getSlots(); slot++) {
            if (book.getStacks().getStackInSlot(slot).is(ModItems.BOOK_OF_SHADOWS.get())) {
                book.getStacks().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        settleCurioChange(player);
    }

    private static void equipMedicalTexts(ServerPlayer player, GameTestHelper helper) {
        ICurioStacksHandler book = handler(player, CurioSlotIds.BOOK, helper);
        int slot = firstEmptySlot(book, helper);
        book.getStacks().setStackInSlot(slot, new ItemStack(ModItems.MEDICAL_TEXTS.get()));
        settleCurioChange(player);
    }

    private static int firstEmptySlot(ICurioStacksHandler handler, GameTestHelper helper) {
        for (int slot = 0; slot < handler.getStacks().getSlots(); slot++) {
            if (handler.getStacks().getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        helper.assertTrue(false, "No empty slot available");
        return -1;
    }

    private static void settleCurioChange(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static final class TestPlayer extends ServerPlayer {
        private TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public void sendSystemMessage(Component message) {
        }

        @Override
        protected void onEffectAdded(
                net.minecraft.world.effect.MobEffectInstance effectInstance,
                net.minecraft.world.entity.Entity source) {
        }

        @Override
        protected void onEffectUpdated(
                net.minecraft.world.effect.MobEffectInstance effectInstance,
                boolean forced,
                net.minecraft.world.entity.Entity source) {
        }

        @Override
        protected void onEffectRemoved(
                net.minecraft.world.effect.MobEffectInstance effectInstance) {
        }
    }
}
