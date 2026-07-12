package net.blouflin.photography.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.blouflin.photography.Photography;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class PhotographyAlbumPhotoScreen extends Screen {
    private final Screen parent;
    private final ItemStack photo;
    private final String handName;
    private final int spreadIndex;

    public PhotographyAlbumPhotoScreen(Screen parent, ItemStack photo, String handName, int spreadIndex) {
        super(Component.translatableWithFallback("gui.photography.album.photo", "Photograph"));
        this.parent = parent;
        this.photo = photo;
        this.handName = handName;
        this.spreadIndex = spreadIndex;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        int size = Math.min(260, Math.min(width, height) - 32);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        PhotographyPhotoRenderCache.drawAlbumPhoto(context, photo, x, y, size);
        super.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            closeToAlbum();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            closeToAlbum();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeToAlbum();
    }

    private void closeToAlbum() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Photography.PHOTOGRAPH_PLACE, 1.0f, 1.0f));
        Minecraft.getInstance().setScreenAndShow(parent != null ? parent : new PhotographyAlbumScreen(handName));
    }
}
