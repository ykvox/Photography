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
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

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
        if (!client.gui.hud.isHidden()) { client.gui.hud.toggle(); }
        isUsingPhotographyCamera = true;
        renderViewfinderMask = true;
        suppressViewfinderOverlayForCapture = false;
        cameraControlsOpen = false;
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
        int background = 0xdd000000;
        int foreground = 0xffffffff;

        debugViewfinder("controls HUD render at x={}, y={}, w={}, h={}, gui={}x{}",
                panelX, panelY, panelWidth, panelHeight, context.guiWidth(), context.guiHeight());

        context.fill(RenderPipelines.GUI, panelX, panelY, panelX + panelWidth, panelY + panelHeight, background);
        renderControlVisual(context, panelX + 16, panelY + 8, SETTINGS.compositionGuide().controlSprite(),
                "C " + SETTINGS.compositionGuide().label(), foreground);
        renderControlVisual(context, panelX + 76, panelY + 8, SETTINGS.selfTimer().controlSprite(),
                "T " + SETTINGS.selfTimer().label(), foreground);
        renderControlVisual(context, panelX + 136, panelY + 8, SETTINGS.shutterSpeed().controlSprite(),
                "S " + SETTINGS.shutterSpeed().label(), foreground);
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
}
