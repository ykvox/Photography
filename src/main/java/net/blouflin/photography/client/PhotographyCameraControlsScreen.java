package net.blouflin.photography.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class PhotographyCameraControlsScreen extends Screen {
    public static final int BUTTON_SIZE = 18;
    public static final int PANEL_WIDTH = 170;
    public static final int PANEL_HEIGHT = 46;

    private int leftPos;
    private int topPos;

    public PhotographyCameraControlsScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void tick() {
        refreshMovementKeys();
        if (!PhotographyHud.isUsingPhotographyCamera) {
            onClose();
        }
    }

    @Override
    protected void init() {
        super.init();
        refreshMovementKeys();

        leftPos = (width - PANEL_WIDTH) / 2;
        topPos = Math.max(12, height - 82);
        PhotographyHud.debugViewfinder("controls screen init gui={}x{}, panel x={}, y={}, w={}, h={}",
                width, height, leftPos, topPos, PANEL_WIDTH, PANEL_HEIGHT);

        addRenderableWidget(createImageButton(
                leftPos + 16,
                topPos + 8,
                PhotographyHud.SETTINGS.compositionGuide().controlSprite(),
                Component.literal("Composition: " + PhotographyHud.SETTINGS.compositionGuide().label()),
                button -> {
                    PhotographyHud.debugViewfinder("controls composition button clicked");
                    PhotographyHud.cycleCompositionGuide();
                    rebuildWidgets();
                }));

        addRenderableWidget(createImageButton(
                leftPos + 76,
                topPos + 8,
                PhotographyHud.SETTINGS.selfTimer().controlSprite(),
                Component.literal("Timer: " + PhotographyHud.SETTINGS.selfTimer().label()),
                button -> {
                    PhotographyHud.debugViewfinder("controls timer button clicked");
                    PhotographyHud.cycleSelfTimer();
                    rebuildWidgets();
                }));

        addRenderableWidget(createImageButton(
                leftPos + 136,
                topPos + 8,
                PhotographyHud.SETTINGS.shutterSpeed().controlSprite(),
                Component.literal("Shutter: " + PhotographyHud.SETTINGS.shutterSpeed().label()),
                button -> {
                    PhotographyHud.debugViewfinder("controls shutter button clicked");
                    PhotographyHud.cycleShutterSpeed();
                    rebuildWidgets();
                }));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        PhotographyHud.debugViewfinder("controls screen render called");
        int background = 0xaa000000;
        int foreground = 0xffffffff;

        context.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, background);
        context.text(font, Component.literal("C " + PhotographyHud.SETTINGS.compositionGuide().label()), leftPos + 6, topPos + 31, foreground, false);
        context.text(font, Component.literal("T " + PhotographyHud.SETTINGS.selfTimer().label()), leftPos + 68, topPos + 31, foreground, false);
        context.text(font, Component.literal("S " + PhotographyHud.SETTINGS.shutterSpeed().label()), leftPos + 126, topPos + 31, foreground, false);

        super.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        if (input.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (input.key() == GLFW_KEY_C()) {
            PhotographyHud.cycleCompositionGuide();
            rebuildWidgets();
            return true;
        }
        if (input.key() == GLFW_KEY_T()) {
            PhotographyHud.cycleSelfTimer();
            rebuildWidgets();
            return true;
        }
        if (input.key() == GLFW_KEY_S()) {
            PhotographyHud.cycleShutterSpeed();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        PhotographyHud.closeCameraControls();
        Minecraft.getInstance().setScreenAndShow(null);
    }

    private ImageButton createImageButton(int x, int y, Identifier sprite, Component tooltip, net.minecraft.client.gui.components.Button.OnPress onPress) {
        PhotographyHud.debugViewfinder("controls widget created at x={}, y={}, w={}, h={}, sprite={}", x, y, BUTTON_SIZE, BUTTON_SIZE, sprite);
        ImageButton button = new ImageButton(x, y, BUTTON_SIZE, BUTTON_SIZE, new WidgetSprites(sprite), onPress, tooltip);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    private void refreshMovementKeys() {
        KeyMapping.setAll();
    }

    private static int GLFW_KEY_C() {
        return 67;
    }

    private static int GLFW_KEY_T() {
        return 84;
    }

    private static int GLFW_KEY_S() {
        return 83;
    }
}
