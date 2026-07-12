package net.blouflin.photography.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class PhotographyCameraControlsScreen extends Screen {
    public static final WidgetSprites SHUTTER_SPEED_SPRITES = threeStateSprites("camera_controls/shutter_speed_dial");
    public static final WidgetSprites FOCAL_LENGTH_SPRITES = normalHighlightedSprites("camera_controls/focal_length");
    public static final Identifier SEPARATOR_SPRITE = Identifier.fromNamespaceAndPath("photography", "camera_controls/button_separator");

    public static final int SEPARATOR_WIDTH = 1;
    public static final int BUTTON_HEIGHT = 18;
    public static final int SIDE_BUTTONS_WIDTH = 49;
    public static final int BUTTON_WIDTH = 15;

    public static boolean suppressRenderForReadback;

    private int leftPos;
    private int topPos;
    private int shutterX;
    private int shutterY;
    private int focalX;
    private int focalY;
    private ControlsLayout layout;
    private long lastRenderDebugTick = -1000L;
    private ImageButton shutterButton;
    private ImageButton focalButton;
    private ImageButton guideButton;
    private ImageButton timerButton;
    private ImageButton flashButton;

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

        layout = computeLayout(width, height);
        leftPos = layout.left();
        topPos = layout.top();
        PhotographyHud.debugViewfinder("controls screen init gui={}x{}, left={}, top={}, openingSize={}, strip x={} y={} w={} h={}",
                width, height, leftPos, topPos, layout.openingSize(), layout.stripX(), layout.stripY(), layout.stripWidth(), BUTTON_HEIGHT);

        int yOffset = PhotographyHud.controlsStripYOffset();
        shutterX = leftPos + 94;
        shutterY = topPos + 226 + yOffset;
        shutterButton = addRenderableWidget(new CameraControlButton(
                shutterX,
                shutterY,
                69,
                12,
                SHUTTER_SPEED_SPRITES,
                SoundProfile.SILENT,
                button -> {
                    PhotographyHud.debugViewfinder("controls shutter button clicked");
                    PhotographyHud.cycleShutterSpeed();
                    PhotographyCameraSounds.playDialClick();
                    rebuildWidgets();
                },
                Component.literal("Shutter: " + PhotographyHud.SETTINGS.shutterSpeed().label())));

        int elementX = layout.stripX();
        int elementY = layout.stripY() + yOffset;

        focalX = elementX;
        focalY = elementY;
        focalButton = addRenderableWidget(new CameraControlButton(
                focalX,
                focalY,
                SIDE_BUTTONS_WIDTH,
                BUTTON_HEIGHT,
                FOCAL_LENGTH_SPRITES,
                SoundProfile.SILENT,
                button -> {
                    PhotographyHud.debugViewfinder("controls focal length button clicked");
                    PhotographyHud.cycleFocalLength();
                    PhotographyCameraSounds.playLensRingBurst();
                    rebuildWidgets();
                },
                Component.literal("Focal length: " + PhotographyHud.SETTINGS.focalLength().label())));
        elementX += SIDE_BUTTONS_WIDTH + SEPARATOR_WIDTH;

        guideButton = addRenderableWidget(createImageButton(
                elementX,
                elementY,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                threeStateSprites(PhotographyHud.SETTINGS.compositionGuide().controlSprite()),
                button -> {
                    PhotographyHud.debugViewfinder("controls composition button clicked");
                    PhotographyHud.cycleCompositionGuide();
                    rebuildWidgets();
                }));
        elementX += BUTTON_WIDTH + SEPARATOR_WIDTH;

        timerButton = addRenderableWidget(createImageButton(
                elementX,
                elementY,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                threeStateSprites(PhotographyHud.SETTINGS.selfTimer().controlSprite()),
                button -> {
                    PhotographyHud.debugViewfinder("controls timer button clicked");
                    PhotographyHud.cycleSelfTimer();
                    rebuildWidgets();
                }));
        elementX += BUTTON_WIDTH + SEPARATOR_WIDTH;

        flashButton = addRenderableWidget(createImageButton(
                elementX,
                elementY,
                SIDE_BUTTONS_WIDTH,
                BUTTON_HEIGHT,
                threeStateSprites(PhotographyHud.SETTINGS.flashMode().controlSprite()),
                button -> {
                    PhotographyHud.debugViewfinder("controls flash button clicked");
                    PhotographyHud.cycleFlashMode();
                    rebuildWidgets();
                }));
        applyAnimatedLayout();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        if (suppressRenderForReadback) {
            long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
            if (gameTime - lastRenderDebugTick >= 20L) {
                lastRenderDebugTick = gameTime;
                PhotographyHud.debugViewfinder("controls screen render suppressed for readback");
            }
            return;
        }

        applyAnimatedLayout();
        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        if (gameTime - lastRenderDebugTick >= 60L) {
            lastRenderDebugTick = gameTime;
            PhotographyHud.debugViewfinder("controls screen render called");
        }
        super.extractRenderState(context, mouseX, mouseY, partialTick);
        drawSeparator(context, focalX + SIDE_BUTTONS_WIDTH, focalY);
        drawSeparator(context, focalX + SIDE_BUTTONS_WIDTH + SEPARATOR_WIDTH + BUTTON_WIDTH, focalY);
        drawSeparator(context, focalX + SIDE_BUTTONS_WIDTH + SEPARATOR_WIDTH + BUTTON_WIDTH + SEPARATOR_WIDTH + BUTTON_WIDTH, focalY);
        drawShutterText(context);
        drawFocalLengthText(context);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        if (input.key() == InputConstants.KEY_ESCAPE) {
            PhotographyHud.stopRenderPhotographyCameraOverlay();
            return true;
        }
        if (input.key() == GLFW_KEY_C()) {
            PhotographyHud.cycleCompositionGuide();
            PhotographyCameraSounds.playButtonClick();
            rebuildWidgets();
            return true;
        }
        if (input.key() == GLFW_KEY_T()) {
            PhotographyHud.cycleSelfTimer();
            PhotographyCameraSounds.playButtonClick();
            rebuildWidgets();
            return true;
        }
        if (input.key() == GLFW_KEY_S()) {
            PhotographyHud.cycleShutterSpeed();
            PhotographyCameraSounds.playDialClick();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        applyAnimatedLayout();
        if (event.button() == 1) {
            PhotographyHud.debugViewfinder("controls right-click toggle");
            PhotographyHud.toggleCameraControls();
            return true;
        }

        if (event.button() == 0) {
            if (!PhotographyHud.controlsStripHitboxesActive()) {
                return PhotographyHud.requestCapture("left-click-settings-closed");
            }
            boolean handledByWidget = super.mouseClicked(event, doubleClick);
            if (handledByWidget) {
                return true;
            }
            PhotographyHud.debugViewfinder("controls left-click outside settings");
            return PhotographyHud.requestCapture("left-click-settings-open");
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        PhotographyHud.closeCameraControls();
    }

    private ImageButton createImageButton(int x, int y, int width, int height, WidgetSprites sprites, net.minecraft.client.gui.components.Button.OnPress onPress) {
        PhotographyHud.debugViewfinder("controls widget created at x={}, y={}, w={}, h={}", x, y, width, height);
        return new CameraControlButton(x, y, width, height, sprites, SoundProfile.GENERIC_BUTTON, onPress, Component.empty());
    }

    private void applyAnimatedLayout() {
        layout = computeLayout(width, height);
        leftPos = layout.left();
        topPos = layout.top();
        int yOffset = PhotographyHud.controlsStripYOffset();
        boolean visible = PhotographyHud.controlsStripOpenProgress() > 0.02f;
        boolean active = PhotographyHud.controlsStripHitboxesActive();

        shutterX = leftPos + 94;
        shutterY = topPos + 226 + yOffset;
        setButtonState(shutterButton, shutterX, shutterY, visible, active);

        int elementX = layout.stripX();
        int elementY = layout.stripY() + yOffset;
        focalX = elementX;
        focalY = elementY;
        setButtonState(focalButton, elementX, elementY, visible, active);
        elementX += SIDE_BUTTONS_WIDTH + SEPARATOR_WIDTH;

        setButtonState(guideButton, elementX, elementY, visible, active);
        elementX += BUTTON_WIDTH + SEPARATOR_WIDTH;

        setButtonState(timerButton, elementX, elementY, visible, active);
        elementX += BUTTON_WIDTH + SEPARATOR_WIDTH;

        setButtonState(flashButton, elementX, elementY, visible, active);
    }

    private static void setButtonState(ImageButton button, int x, int y, boolean visible, boolean active) {
        if (button == null) {
            return;
        }
        button.setX(x);
        button.setY(y);
        button.visible = visible;
        button.active = active;
    }

    private void drawSeparator(GuiGraphicsExtractor context, int x, int y) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, SEPARATOR_SPRITE, x, y, SEPARATOR_WIDTH, BUTTON_HEIGHT);
    }

    private void drawShutterText(GuiGraphicsExtractor context) {
        String text = PhotographyHud.SETTINGS.shutterSpeed().notation().replace("1/", "");
        int textWidth = font.width(text);
        int x = shutterX + 69 / 2 - textWidth / 2 + 1;
        context.text(font, text, x, shutterY + 3, 0xffffffff, false);
    }

    private void drawFocalLengthText(GuiGraphicsExtractor context) {
        Component text = Component.literal(PhotographyHud.currentFocalLengthLabel());
        int textWidth = font.width(text);
        int x = focalX + 17 + (29 - textWidth) / 2;
        context.text(font, text, x, focalY + 7, 0xffffffff, false);
    }

    public static ControlsLayout computeLayout(int guiWidth, int guiHeight) {
        int openingSize = Math.min(guiWidth, guiHeight);
        int left = (guiWidth - 256) / 2;
        int top = Math.round((guiHeight - openingSize) / 2.0f + openingSize - 256.0f);
        int stripWidth = SIDE_BUTTONS_WIDTH + SEPARATOR_WIDTH
                + BUTTON_WIDTH + SEPARATOR_WIDTH
                + BUTTON_WIDTH + SEPARATOR_WIDTH
                + SIDE_BUTTONS_WIDTH;
        int stripX = left + 128 - stripWidth / 2;
        int stripY = top + 238;
        return new ControlsLayout(guiWidth, guiHeight, openingSize, left, top, stripX, stripY, stripWidth);
    }

    private static WidgetSprites threeStateSprites(String path) {
        return threeStateSprites(Identifier.fromNamespaceAndPath("photography", path));
    }

    public static WidgetSprites threeStateSprites(Identifier base) {
        return new WidgetSprites(base,
                Identifier.fromNamespaceAndPath(base.getNamespace(), base.getPath() + "_disabled"),
                Identifier.fromNamespaceAndPath(base.getNamespace(), base.getPath() + "_highlighted"));
    }

    private static WidgetSprites normalHighlightedSprites(String path) {
        Identifier base = Identifier.fromNamespaceAndPath("photography", path);
        return new WidgetSprites(base, base,
                Identifier.fromNamespaceAndPath(base.getNamespace(), base.getPath() + "_highlighted"));
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

    public record ControlsLayout(int guiWidth, int guiHeight, int openingSize, int left, int top,
                                 int stripX, int stripY, int stripWidth) {
    }

    private static class CameraControlButton extends ImageButton {
        private final SoundProfile soundProfile;

        private CameraControlButton(int x, int y, int width, int height, WidgetSprites sprites,
                                    SoundProfile soundProfile,
                                    net.minecraft.client.gui.components.Button.OnPress onPress,
                                    Component message) {
            super(x, y, width, height, sprites, onPress, message);
            this.soundProfile = soundProfile;
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            if (soundProfile == SoundProfile.GENERIC_BUTTON) {
                PhotographyCameraSounds.playButtonClick();
            }
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            if (soundProfile == SoundProfile.GENERIC_BUTTON) {
                PhotographyCameraSounds.playButtonRelease();
            }
        }
    }

    private enum SoundProfile {
        GENERIC_BUTTON,
        SILENT
    }
}
