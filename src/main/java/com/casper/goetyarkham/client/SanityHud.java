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
    private static final int ROW_STEP = 5;
    private static final int ICONS_PER_ROW = 10;
    private static final int TEXTURE_WIDTH = 54;
    private static final int TEXTURE_HEIGHT = 18;
    private static final int FULL_U = 0;
    private static final int EMPTY_U = 18;
    private static final int PERMANENT_DAMAGE_U = 36;

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
        int rightEdgeX = screenWidth / 2 + 91
                + SanityClientConfig.hudOffsetX();
        int bottomY = screenHeight - gui.rightHeight
                + SanityClientConfig.hudOffsetY();

        for (int row = 0; row < rows; row++) {
            int rowStart = row * ICONS_PER_ROW;
            int rowIconCount = Math.min(
                    ICONS_PER_ROW, displayedSlots - rowStart);
            int rowWidth = ICON_SIZE + (rowIconCount - 1) * ICON_STEP;
            int rowOriginX = rightEdgeX - rowWidth;
            int iconY = bottomY - row * ROW_STEP;

            if (row > 0) {
                graphics.enableScissor(
                        rowOriginX, iconY, rightEdgeX, iconY + ROW_STEP);
            }
            try {
                for (int column = 0; column < rowIconCount; column++) {
                    int index = rowStart + column;
                    int u = index < current
                            ? FULL_U
                            : index < maximum
                                    ? EMPTY_U
                                    : PERMANENT_DAMAGE_U;
                    blitIcon(
                            graphics,
                            rowOriginX + column * ICON_STEP,
                            iconY,
                            u);
                }
            } finally {
                if (row > 0) {
                    graphics.disableScissor();
                }
            }
        }

        int occupiedHeight = ICON_SIZE + (rows - 1) * ROW_STEP;
        gui.rightHeight += occupiedHeight;
    }

    private static void blitIcon(GuiGraphics graphics, int x, int y, int u) {
        graphics.blit(
                ICONS,
                x,
                y,
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
