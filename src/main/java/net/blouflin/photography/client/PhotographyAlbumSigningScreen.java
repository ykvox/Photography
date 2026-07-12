package net.blouflin.photography.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class PhotographyAlbumSigningScreen extends Screen {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("photography", "textures/gui/album.png");
    private static final int IMAGE_WIDTH = 149;
    private static final int IMAGE_HEIGHT = 188;
    private static final int TEXTURE_SIZE = 512;

    private final PhotographyAlbumScreen parent;
    private int leftPos;
    private int topPos;
    private String titleText;
    private PhotographyAlbumTextBox titleBox;
    private ImageButton signButton;

    public PhotographyAlbumSigningScreen(PhotographyAlbumScreen parent, String initialTitle) {
        super(Component.empty());
        this.parent = parent;
        this.titleText = initialTitle == null ? "" : initialTitle;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        leftPos = (width - IMAGE_WIDTH) / 2;
        topPos = (height - IMAGE_HEIGHT) / 2;

        titleBox = addRenderableWidget(new PhotographyAlbumTextBox(font, leftPos + 21, topPos + 73, 108, 9,
                () -> titleText, text -> {
                    titleText = text;
                    parent.rememberTitleDraft(text);
                }, false)
                .setTextColor(0xffb59774)
                .setSelectionColor(0xff8888ff, 0xffbbbbff));
        titleBox.setText(titleText);

        signButton = addRenderableWidget(new ImageButton(leftPos + 46, topPos + 110, 22, 22,
                PhotographyAlbumScreen.SIGN_BUTTON_SPRITES, button -> signAlbum(),
                Component.translatableWithFallback("gui.photography.album.sign", "Sign Album")));
        addRenderableWidget(new ImageButton(leftPos + 83, topPos + 111, 22, 22,
                PhotographyAlbumScreen.CANCEL_BUTTON_SPRITES, button -> cancelSigning(),
                Component.translatableWithFallback("gui.photography.album.cancel_signing", "Cancel")));
        setFocused(titleBox);
    }

    @Override
    public void tick() {
        if (titleBox != null) {
            titleBox.tick();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        if (signButton != null) {
            signButton.active = !titleText.isBlank();
        }
        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 298.0f, 0.0f,
                IMAGE_WIDTH, IMAGE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        drawCenteredText(context, Component.translatableWithFallback("gui.photography.album.enter_title", "Enter Album Title:"),
                leftPos + IMAGE_WIDTH / 2, topPos + 50, 0xfff5ebd0);
        String name = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "";
        drawCenteredText(context, Component.translatableWithFallback("gui.photography.album.by_author", "by %s".formatted(name)),
                leftPos + IMAGE_WIDTH / 2, topPos + 84, 0xffc7b496);
        super.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            cancelSigning();
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            signAlbum();
            return true;
        }
        return super.keyPressed(event);
    }

    private void signAlbum() {
        if (!titleText.isBlank()) {
            parent.commitTitleFromSigning(titleText);
        }
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private void cancelSigning() {
        parent.rememberTitleDraft(titleText);
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private void drawCenteredText(GuiGraphicsExtractor context, Component component, int centerX, int y, int color) {
        context.text(font, component, centerX - font.width(component) / 2, y, color, false);
    }
}
