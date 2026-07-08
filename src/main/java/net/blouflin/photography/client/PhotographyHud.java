package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyCamera;
import net.blouflin.photography.networking.SetUsingPhotographyCameraPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PhotographyHud {

    public static boolean isUsingPhotographyCamera = false;
    public static float spyglassFlashOpacity = 0.0f;
    public static float viewfinderScale = 0.5f;
    public static boolean canTakePhoto = false;
    public static boolean isTakingPhoto = false;
    public static boolean isHUDhidden;
    public static String handUsingPhotographyCamera = InteractionHand.MAIN_HAND.name();
    public static double zoomAmount;
    public static double defaultMouseSensitivity;
    public static final Identifier VIEWFINDER_MASK = Identifier.fromNamespaceAndPath("photography","textures/gui/viewfinder/viewfinder.png");
    public static final Identifier CAMERA_SCOPE_FLASH = Identifier.fromNamespaceAndPath("photography","camera_scope_flash");
    public static boolean renderViewfinderMask = true;
    public static boolean suppressViewfinderOverlayForCapture = false;
    public static boolean cameraControlsOpen = false;
    public static final PhotographyCameraSettings SETTINGS = new PhotographyCameraSettings();
    private static final Minecraft client = Minecraft.getInstance();
    private static final KeyMapping escapeKeybinding = new KeyMapping("key.keyboard.escape", GLFW.GLFW_KEY_ESCAPE, KeyMapping.Category.MISC);
    private static final boolean DEBUG_VIEWFINDER = Boolean.getBoolean("photography.debugViewfinder");
    private static boolean loggedOverlayRender;
    private static CameraType previousCameraType;
    private static boolean selfieEnabled;
    private static long lastControlsHudDebugTick = -1000L;
    private static int controlsPanelX;
    private static int controlsPanelY;
    private static boolean controlsPanelPositionKnown;

    private static CompletableFuture<Void> screenshotFuture;
    public static void setScreenshotFuture(CompletableFuture<Void> future) {
        screenshotFuture = future;
    }

    public static void openViewfinder(InteractionHand hand) {
        zoomAmount = 1.0f;
        handUsingPhotographyCamera = hand.toString();
        defaultMouseSensitivity = client.options.sensitivity().get();
        isHUDhidden = client.gui.hud.isHidden();
        previousCameraType = client.options.getCameraType();
        selfieEnabled = false;
        debugViewfinder("previous perspective stored: {}", previousCameraType);
        applyCameraModelState(false);
        if (!client.gui.hud.isHidden()) { client.gui.hud.toggle(); }
        isUsingPhotographyCamera = true;
        renderViewfinderMask = true;
        suppressViewfinderOverlayForCapture = false;
        cameraControlsOpen = false;
        controlsPanelPositionKnown = false;
        loggedOverlayRender = false;
        debugViewfinder("viewfinder open ({})", handUsingPhotographyCamera);
        if (client.player != null) {
            client.player.playSound(SoundEvents.SPYGLASS_USE, 1.0f, 1.0f);
        }
        SetUsingPhotographyCameraPayload payload = new SetUsingPhotographyCameraPayload(isUsingPhotographyCamera, handUsingPhotographyCamera);
        ClientPlayNetworking.send(payload);
    }

    public static void toggleCameraControls() {
        if (cameraControlsOpen) {
            closeCameraControlsScreen();
        } else {
            openCameraControls();
        }
    }

    public static void openCameraControls() {
        if (client.gui.screen() instanceof PhotographyCameraControlsScreen) {
            cameraControlsOpen = true;
            return;
        }
        cameraControlsOpen = true;
        debugViewfinder("controls opened");
        client.setScreenAndShow(new PhotographyCameraControlsScreen());
        debugViewfinder("controls screen set/opened; current screen={}", client.gui.screen() == null ? "null" : client.gui.screen().getClass().getName());
    }

    public static void closeCameraControls() {
        if (!cameraControlsOpen) {
            return;
        }
        cameraControlsOpen = false;
        debugViewfinder("controls closed");
    }

    public static void closeCameraControlsScreen() {
        closeCameraControls();
        if (client.gui.screen() instanceof PhotographyCameraControlsScreen) {
            client.setScreenAndShow(null);
        }
    }

    public static void cycleCompositionGuide() {
        SETTINGS.cycleCompositionGuide();
        debugViewfinder("button clicked / composition guide set to {}", SETTINGS.compositionGuide().label());
    }

    public static void cycleSelfTimer() {
        SETTINGS.cycleSelfTimer();
        debugViewfinder("button clicked / self timer set to {}", SETTINGS.selfTimer().label());
    }

    public static void cycleShutterSpeed() {
        SETTINGS.cycleShutterSpeed();
        debugViewfinder("button clicked / shutter speed set to {}", SETTINGS.shutterSpeed().label());
    }

    public static void toggleSelfie() {
        if (!isUsingPhotographyCamera) {
            return;
        }

        selfieEnabled = !selfieEnabled;
        client.options.setCameraType(selfieEnabled ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        applyCameraModelState(selfieEnabled);
        debugViewfinder("selfie toggled {}", selfieEnabled ? "on" : "off");
    }

    public static boolean isSelfieEnabled() {
        return selfieEnabled;
    }

    public static boolean canUseViewfinderInCurrentPerspective() {
        return isViewfinderPerspective();
    }

    public static void beginCaptureOverlaySuppression() {
        suppressViewfinderOverlayForCapture = true;
        renderViewfinderMask = false;
        debugViewfinder("before suppressing overlay for capture");
    }

    public static void requestCleanCaptureFrame(CompletableFuture<Void> future) {
        debugViewfinder("capture frame requested");
        screenshotFuture = future;
    }

    public static void restoreOverlayAfterCapture() {
        suppressViewfinderOverlayForCapture = false;
        renderViewfinderMask = true;
        debugViewfinder("overlay restored");
    }

    public static void renderPhotographyCameraOverlay(GuiGraphicsExtractor context) {

        float f = client.getDeltaTracker().getGameTimeDeltaTicks();
        viewfinderScale = Mth.lerp(0.5f * f, viewfinderScale, 1.0f);

        if (isViewfinderPerspective() && (client.gui.screen() == null || client.gui.screen() instanceof PhotographyCameraControlsScreen)) {
            if (isTakingPhoto) {
                renderViewfinderMask = false;
            }

            if (!client.gui.hud.isHidden()) { client.gui.hud.toggle(); }
            checkIsPhotographyCameraOpen(client);
            if (!isHUDhidden) {
                renderViewfinderOverlay(context, viewfinderScale);
            }
            spyglassFlashOpacity = Mth.lerp(0.1f * f, spyglassFlashOpacity, 0.0125f);

            if (viewfinderScale >= 0.98f && spyglassFlashOpacity <= 0.1f && !isTakingPhoto) {
                canTakePhoto = true;
            } else {
                canTakePhoto = false;
            }

            if (escapeKeybinding.isDown()) {
                stopRenderPhotographyCameraOverlay();
            }
        } else {
            stopRenderPhotographyCameraOverlay();
        }

        if (screenshotFuture != null) {
            screenshotFuture.complete(null);
            screenshotFuture = null;
        }
    }

    public static void stopRenderPhotographyCameraOverlay() {
        client.options.sensitivity().set(defaultMouseSensitivity);
        if (client.gui.hud.isHidden() != isHUDhidden) { client.gui.hud.toggle(); }
        if (client.gui.screen() instanceof PhotographyCameraControlsScreen) {
            client.setScreenAndShow(null);
        }
        spyglassFlashOpacity = 0.0f;
        viewfinderScale = 0.5f;
        canTakePhoto = false;
        zoomAmount = 1.0f;
        PhotographyHud.isUsingPhotographyCamera = false;
        renderViewfinderMask = true;
        suppressViewfinderOverlayForCapture = false;
        cameraControlsOpen = false;
        controlsPanelPositionKnown = false;
        loggedOverlayRender = false;
        restorePreviousPerspective();
        debugViewfinder("viewfinder close");
        if (client.player != null) {
            client.player.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0f, 1.0f);
        }
        SetUsingPhotographyCameraPayload payload = new SetUsingPhotographyCameraPayload(isUsingPhotographyCamera, handUsingPhotographyCamera);
        ClientPlayNetworking.send(payload);
    }

    private static boolean isViewfinderPerspective() {
        CameraType cameraType = client.options.getCameraType();
        return cameraType == CameraType.FIRST_PERSON || cameraType == CameraType.THIRD_PERSON_FRONT;
    }

    private static void restorePreviousPerspective() {
        if (previousCameraType != null) {
            client.options.setCameraType(previousCameraType);
            debugViewfinder("perspective restored: {}", previousCameraType);
            previousCameraType = null;
        }
        selfieEnabled = false;
        applyCameraModelState(false);
    }

    private static void applyCameraModelState(boolean selfieModel) {
        Player player = client.player;
        if (player == null) {
            return;
        }

        InteractionHand hand = InteractionHand.valueOf(handUsingPhotographyCamera);
        ItemStack stack = player.getItemInHand(hand);
        if (!PhotographyCamera.isPhotographyCamera(stack)) {
            return;
        }

        float customModelData = selfieModel ? 56777F : 56774F;
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(customModelData), List.of(), List.of(), List.of()));
        debugViewfinder("camera model state set to {}", selfieModel ? "selfie" : "normal");
    }

    public static void checkIsPhotographyCameraOpen(Minecraft client) {
        Player player = client.player;
        InteractionHand hand = InteractionHand.valueOf(handUsingPhotographyCamera);
        boolean isPhotographyCamera = player != null && PhotographyCamera.isPhotographyCamera(player.getItemInHand(hand));
        if (!isPhotographyCamera) {
            if (PhotographyHud.isUsingPhotographyCamera) {
                stopRenderPhotographyCameraOverlay();
            }
        }
    }

    private static void renderViewfinderOverlay(GuiGraphicsExtractor context, float scale) {
        if (suppressViewfinderOverlayForCapture) {
            return;
        }

        if (!loggedOverlayRender) {
            debugViewfinder("overlay render active");
            loggedOverlayRender = true;
        }

        float f;
        float g = f = (float)Math.min(context.guiWidth(), context.guiHeight());
        float h = Math.min((float)context.guiWidth() / f, (float)context.guiHeight() / g) * scale;
        int i = Mth.floor(f * h);
        int j = Mth.floor(g * h);
        int k = (context.guiWidth() - i) / 2;
        int l = (context.guiHeight() - j) / 2;
        int m = k + i;
        int n = l + j;

        if (renderViewfinderMask) {
            context.blit(RenderPipelines.GUI_TEXTURED, VIEWFINDER_MASK, k, l, 0.0f, 0.0f, i, j, i, j);
            renderCompositionGuide(context, k, l, i, j);
        }

        context.blitSprite(RenderPipelines.GUI_TEXTURED, CAMERA_SCOPE_FLASH, k, l, i, j, spyglassFlashOpacity);

        context.fill(RenderPipelines.GUI, 0, n, context.guiWidth(), context.guiHeight(), CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, 0, 0, context.guiWidth(), l, CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, 0, l, k, n, CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, m, l, context.guiWidth(), n, CommonColors.BLACK);

        renderCameraControlsStrip(context, k, l, i, j);
    }

    private static void renderCompositionGuide(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        Identifier texture = SETTINGS.compositionGuide().overlayTexture();
        if (texture != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height);
        }
    }

    private static void renderCameraControlsStrip(GuiGraphicsExtractor context, int viewfinderX, int viewfinderY, int viewfinderWidth, int viewfinderHeight) {
        if (!cameraControlsOpen) {
            return;
        }

        int panelWidth = PhotographyCameraControlsScreen.PANEL_WIDTH;
        int panelHeight = PhotographyCameraControlsScreen.PANEL_HEIGHT;
        int panelX = (context.guiWidth() - panelWidth) / 2;
        int panelY = Math.min(context.guiHeight() - panelHeight - 8, viewfinderY + viewfinderHeight - panelHeight - 18);
        controlsPanelX = panelX;
        controlsPanelY = panelY;
        controlsPanelPositionKnown = true;
        int background = 0xdd000000;
        int foreground = 0xffffffff;

        long gameTime = client.level == null ? 0L : client.level.getGameTime();
        if (gameTime - lastControlsHudDebugTick >= 30L) {
            lastControlsHudDebugTick = gameTime;
            debugViewfinder("controls HUD strip render called x={}, y={}, w={}, h={}, gui={}x{}",
                    panelX, panelY, panelWidth, panelHeight, context.guiWidth(), context.guiHeight());
        }

        context.fill(RenderPipelines.GUI, panelX, panelY, panelX + panelWidth, panelY + panelHeight, background);
        context.text(client.font, "GUIDE: " + SETTINGS.compositionGuide().label(), panelX + 6, panelY + 5, foreground, false);
        context.text(client.font, "TIMER: " + SETTINGS.selfTimer().label(), panelX + 6, panelY + 18, foreground, false);
        context.text(client.font, "SHUTTER: " + SETTINGS.shutterSpeed().label(), panelX + 78, panelY + 18, foreground, false);
        renderControlVisual(context, panelX + 16, panelY + 8, SETTINGS.compositionGuide().controlSprite(),
                "C " + SETTINGS.compositionGuide().label(), foreground);
        renderControlVisual(context, panelX + 76, panelY + 8, SETTINGS.selfTimer().controlSprite(),
                "T " + SETTINGS.selfTimer().label(), foreground);
        renderControlVisual(context, panelX + 136, panelY + 8, SETTINGS.shutterSpeed().controlSprite(),
                "S " + SETTINGS.shutterSpeed().label(), foreground);
    }

    public static boolean handleControlsClick(double guiX, double guiY) {
        if (!cameraControlsOpen || !controlsPanelPositionKnown) {
            return false;
        }

        if (isInside(guiX, guiY, controlsPanelX + 16, controlsPanelY + 8, PhotographyCameraControlsScreen.BUTTON_SIZE, PhotographyCameraControlsScreen.BUTTON_SIZE)) {
            debugViewfinder("controls HUD composition hit at {}, {}", guiX, guiY);
            cycleCompositionGuide();
            return true;
        }
        if (isInside(guiX, guiY, controlsPanelX + 76, controlsPanelY + 8, PhotographyCameraControlsScreen.BUTTON_SIZE, PhotographyCameraControlsScreen.BUTTON_SIZE)) {
            debugViewfinder("controls HUD timer hit at {}, {}", guiX, guiY);
            cycleSelfTimer();
            return true;
        }
        if (isInside(guiX, guiY, controlsPanelX + 136, controlsPanelY + 8, PhotographyCameraControlsScreen.BUTTON_SIZE, PhotographyCameraControlsScreen.BUTTON_SIZE)) {
            debugViewfinder("controls HUD shutter hit at {}, {}", guiX, guiY);
            cycleShutterSpeed();
            return true;
        }

        return false;
    }

    private static boolean isInside(double guiX, double guiY, int x, int y, int width, int height) {
        return guiX >= x && guiX < x + width && guiY >= y && guiY < y + height;
    }

    private static void renderControlVisual(GuiGraphicsExtractor context, int x, int y, Identifier sprite, String label, int color) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, PhotographyCameraControlsScreen.BUTTON_SIZE, PhotographyCameraControlsScreen.BUTTON_SIZE);
        context.text(client.font, label, x - 10, y + 22, color, false);
    }

    public static void debugViewfinder(String message, Object... args) {
        if (DEBUG_VIEWFINDER) {
            Photography.LOGGER.info("[viewfinder] " + message, args);
        }
    }

    public static boolean isDebugViewfinderEnabled() {
        return DEBUG_VIEWFINDER;
    }
}
