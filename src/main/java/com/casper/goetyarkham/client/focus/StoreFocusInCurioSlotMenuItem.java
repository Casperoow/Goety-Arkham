package com.casper.goetyarkham.client.focus;

import com.Polarice3.Goety.client.gui.radial.GenericRadialMenu;
import com.Polarice3.Goety.client.gui.radial.TextRadialMenuItem;
import com.casper.goetyarkham.network.ModNetwork;
import net.minecraft.network.chat.Component;

/**
 * Radial-menu "Slot" text entry. Clicking it asks the server to store the
 * wand's current focus into the lowest-numbered empty Curios focus slot.
 *
 * <p>Declared as a named (not anonymous) class so it can be constructed
 * safely from inside a Mixin injector without relying on implicit outer-class
 * capture.</p>
 */
public final class StoreFocusInCurioSlotMenuItem extends TextRadialMenuItem {
    private final GenericRadialMenu menu;

    public StoreFocusInCurioSlotMenuItem(GenericRadialMenu menu, Component text) {
        super(menu, text);
        this.menu = menu;
    }

    @Override
    public boolean onClick() {
        ModNetwork.sendStoreFocusInCurioSlot();
        this.menu.close();
        return true;
    }
}
