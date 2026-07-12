package net.blouflin.photography.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public class PhotographyPhotoClientTooltip implements ClientTooltipComponent {
    private static final int IMAGE_SIZE = 80;
    private static final int PADDING = 2;
    private static final int BOTTOM_MARGIN = 7;
    private static final int WIDTH = IMAGE_SIZE + PADDING * 2;
    private static final int HEIGHT = IMAGE_SIZE + PADDING * 2 + BOTTOM_MARGIN;

    private final PhotographyPhotoTooltip tooltip;

    public PhotographyPhotoClientTooltip(PhotographyPhotoTooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public int getHeight(Font font) {
        return HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return WIDTH;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor context) {
        if (PhotographyCaptureDebug.DEBUG_CAPTURE_IMAGES) {
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            net.blouflin.photography.Photography.LOGGER.info(
                    "[photo-preview-layout] component={}x{} frame={},{},{},{} image={},{},{},{} texture={}x{} guiScale={}",
                    WIDTH, HEIGHT,
                    x + PADDING, y + PADDING, IMAGE_SIZE, IMAGE_SIZE,
                    x + PADDING, y + PADDING, IMAGE_SIZE, IMAGE_SIZE,
                    IMAGE_SIZE, IMAGE_SIZE,
                    String.format("%.2f", guiScale));
        }
        PhotographyPhotoRenderCache.drawTooltipPreview(context, tooltip.stack(), x + PADDING, y + PADDING, IMAGE_SIZE);
    }
}
