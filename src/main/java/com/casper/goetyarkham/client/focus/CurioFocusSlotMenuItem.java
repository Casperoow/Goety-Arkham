package com.casper.goetyarkham.client.focus;

import com.Polarice3.Goety.client.gui.radial.GenericRadialMenu;
import com.Polarice3.Goety.client.gui.radial.ItemStackRadialMenuItem;
import com.casper.goetyarkham.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Radial-menu entry backed by a Curios {@code focus} slot. Clicking it asks
 * the server to swap that exact slot index with the wand's current focus;
 * the client never sends the stack itself.
 *
 * <p>Declared as a named (not anonymous) class so it can be constructed
 * safely from inside a Mixin injector without relying on implicit outer-class
 * capture.</p>
 */
public final class CurioFocusSlotMenuItem extends ItemStackRadialMenuItem {
    private final GenericRadialMenu menu;

    public CurioFocusSlotMenuItem(GenericRadialMenu menu, int slotIndex, ItemStack stack) {
        super(menu, slotIndex, stack, Component.empty());
        this.menu = menu;
    }

    @Override
    public boolean onClick() {
        ModNetwork.sendSwapFocusWithCurioSlot(getSlot());
        this.menu.close();
        return true;
    }
}
