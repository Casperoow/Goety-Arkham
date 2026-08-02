package com.casper.goetyarkham.client;

import com.casper.goetyarkham.GoetyArkham;
import com.casper.goetyarkham.sanity.SanityMath;
import com.casper.goetyarkham.sanity.SanitySnapshot;
import com.casper.goetyarkham.sanity.config.SanityClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

@OnlyIn(Dist.CLIENT)
public final class SanityHud {
    private static final ResourceLocation ICONS = new ResourceLocation(
            GoetyArkham.MOD_ID, "textures/gui/sanity_icons.png");
    private static final int ICON_SIZE = 9;
    private static final int SOURCE_ICON_SIZE = 18;
    private static final int ICON_STEP = 8;
    private static final int ICONS_PER_ROW = 10;
    private static final int TEXTURE_WIDTH = 54;
    private static final int TEXTURE_HEIGHT = 18;
    private static final int FULL_U = 0;
    private static final int EMPTY_U = 18;
    private static final int DAMAGED_U = 36;

    private SanityHud() {
    }

    public static void render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui
                || minecraft.player.isSpectator()
                || !gui.shouldDrawSurvivalElements()) {
            return;
        }

        SanitySnapshot snapshot = ClientSanity.snapshot();
        int maximum = snapshot.maximumSanity();
        if (maximum <= 0) {
            return;
        }
        int permanentLoss = SanityMath.clampPermanentLoss(
                snapshot.permanentMaxLoss());
        int displayedSlots = maximum + permanentLoss;
        int current = Math.max(0, Math.min(maximum, snapshot.currentSanity()));
        int rows = (displayedSlots + ICONS_PER_ROW - 1) / ICONS_PER_ROW;
        int originX = screenWidth / 2 - 91 + SanityClientConfig.hudOffsetX();
        int bottomY = screenHeight - gui.leftHeight - ICON_SIZE
                + SanityClientConfig.hudOffsetY();

        // Usable sanity slots are drawn empty first, then current sanity is
        // overlaid as full. Permanently lost slots remain visible after them.
        for (int index = 0; index < maximum; index++) {
            blitIcon(graphics, originX, bottomY, index, EMPTY_U);
        }
        for (int index = 0; index < current; index++) {
            blitIcon(graphics, originX, bottomY, index, FULL_U);
        }
        for (int index = maximum; index < displayedSlots; index++) {
            blitIcon(graphics, originX, bottomY, index, DAMAGED_U);
        }

        // Reserve the occupied left-HUD height for overlays rendered later.
        gui.leftHeight += rows * ICON_SIZE;
    }

    private static void blitIcon(
            GuiGraphics graphics, int originX, int bottomY, int index, int u) {
        int column = index % ICONS_PER_ROW;
        int row = index / ICONS_PER_ROW;

        graphics.blit(
                ICONS,
                originX + column * ICON_STEP,
                bottomY - row * ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                (float) u,
                0.0F,
                SOURCE_ICON_SIZE,
                SOURCE_ICON_SIZE,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
    }
}
