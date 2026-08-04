package com.casper.goetyarkham.mixin.client;

import com.Polarice3.Goety.client.gui.radial.GenericRadialMenu;
import com.Polarice3.Goety.client.gui.screen.inventory.FocusRadialMenuScreen;
import com.Polarice3.Goety.utils.WandUtil;
import com.casper.goetyarkham.client.focus.CurioFocusSlotMenuItem;
import com.casper.goetyarkham.client.focus.StoreFocusInCurioSlotMenuItem;
import com.casper.goetyarkham.curios.FocusCurioService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Minimal-surface extension of Goety's wand focus radial menu so a Curios
 * {@code focus} slot behaves as a third focus source alongside the focus bag
 * and the player's inventory, plus a new "Slot" text entry. This class is
 * loaded client-only (see the {@code client} list in goetyarkham.mixins.json)
 * since {@code FocusRadialMenuScreen} itself only ever loads client-side.
 *
 * <p>Every injection point below was confirmed against the decompiled
 * bytecode of {@code goety-2.5.55.4.jar} (methods keep their obfuscated
 * runtime names, e.g. {@code m_86600_} = {@code Screen.tick()},
 * {@code m_88315_} = {@code Screen.render(GuiGraphics, int, int, float)});
 * {@code remap = false} matches this project's existing convention for
 * mixing into third-party mod classes (see {@code DarkWandFocusSpendMixin}).</p>
 */
@Mixin(value = FocusRadialMenuScreen.class, remap = false)
public abstract class FocusRadialMenuScreenMixin {
    @Shadow
    @Final
    private GenericRadialMenu menu;

    @Shadow
    private boolean needsRecheckStacks;

    @Unique
    private ItemStack[] goetyarkham$lastFocusSlotStacks = new ItemStack[0];

    @Unique
    private StoreFocusInCurioSlotMenuItem goetyarkham$slotMenuItem;

    // Tail of the no-arg constructor: this.menu is already assigned by then,
    // matching where insertMenuItem/extractMenuItem are built in vanilla code.
    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void goetyarkham$createSlotMenuItem(CallbackInfo callback) {
        this.goetyarkham$slotMenuItem = new StoreFocusInCurioSlotMenuItem(
                this.menu, Component.translatable("tooltip.goetyarkham.focus_slot.store"));
    }

    // Screen.tick(): detect Curios focus-slot content changes (e.g. after our
    // own swap/store packets round-trip) and force the existing rebuild path
    // to run, the same way vanilla forces it when the bag ItemStack changes.
    @Inject(method = "m_86600_", at = @At("HEAD"), require = 1)
    private void goetyarkham$detectCurioFocusChange(CallbackInfo callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int slotCount = FocusCurioService.getSlotCount(player);
        ItemStack[] current = new ItemStack[slotCount];
        for (int i = 0; i < slotCount; i++) {
            current[i] = FocusCurioService.getFocusAt(player, i);
        }
        if (!Arrays.equals(this.goetyarkham$lastFocusSlotStacks, current)) {
            this.goetyarkham$lastFocusSlotStacks = current;
            this.needsRecheckStacks = true;
        }
    }

    // Screen.tick(): both of vanilla's "nothing left to show, close" checks
    // call WandUtil.findFocus(player).isEmpty(). Redirecting every occurrence
    // in this method (no ordinal) folds the Curios focus slot into "is there
    // still a focus somewhere" so the screen no longer auto-closes when only
    // a Curios slot holds a focus.
    @Redirect(
            method = "m_86600_",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41619_()Z"),
            require = 2)
    private boolean goetyarkham$treatCurioFocusAsPresentOnTick(ItemStack wandFocus) {
        return goetyarkham$isEmptyConsideringCurioSlots(wandFocus);
    }

    // Screen.render(...): the very first WandUtil.findFocus(player).isEmpty()
    // call is the "hide everything, nothing to show" early return; the three
    // later occurrences (insert/extract button visibility, central item) must
    // keep describing the wand's actual contents, so only ordinal 0 is redirected.
    @Redirect(
            method = "m_88315_",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41619_()Z", ordinal = 0),
            require = 1)
    private boolean goetyarkham$treatCurioFocusAsPresentOnRender(ItemStack wandFocus) {
        return goetyarkham$isEmptyConsideringCurioSlots(wandFocus);
    }

    @Unique
    private boolean goetyarkham$isEmptyConsideringCurioSlots(ItemStack wandFocus) {
        if (!wandFocus.isEmpty()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || !FocusCurioService.hasAnyFocus(player);
    }

    // Screen.render(...): right after vanilla adds its "insert"/"extract" text
    // items during a rebuild pass (this code path only runs while
    // needsRecheckStacks was true), append one ItemStackRadialMenuItem per
    // non-empty Curios focus slot and the "Slot" text item.
    @Inject(
            method = "m_88315_",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/Polarice3/Goety/client/gui/radial/GenericRadialMenu;add(Lcom/Polarice3/Goety/client/gui/radial/RadialMenuItem;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER),
            require = 1)
    private void goetyarkham$rebuildCurioFocusMenuItems(
            GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int slotCount = FocusCurioService.getSlotCount(player);
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = FocusCurioService.getFocusAt(player, i);
            if (stack.isEmpty()) {
                continue;
            }
            CurioFocusSlotMenuItem item = new CurioFocusSlotMenuItem(this.menu, i, stack);
            item.setVisible(true);
            this.menu.add(item);
        }
        if (this.goetyarkham$slotMenuItem != null) {
            this.menu.add(this.goetyarkham$slotMenuItem);
        }
    }

    // Screen.render(...): mirrors vanilla's per-frame
    // insertMenuItem/extractMenuItem.setVisible(...) calls, which run
    // unconditionally right before menu.draw(...), not only on rebuild passes.
    @Inject(
            method = "m_88315_",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/Polarice3/Goety/client/gui/radial/GenericRadialMenu;draw(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                    shift = At.Shift.BEFORE),
            require = 1)
    private void goetyarkham$updateSlotMenuItemVisibility(
            GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo callback) {
        if (this.goetyarkham$slotMenuItem == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            this.goetyarkham$slotMenuItem.setVisible(false);
            return;
        }
        boolean wandHasFocus = !WandUtil.findFocus(player).isEmpty();
        this.goetyarkham$slotMenuItem.setVisible(wandHasFocus && FocusCurioService.hasEmptySlot(player));
    }
}
