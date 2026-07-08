package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyCamera;
import net.blouflin.photography.networking.SetUsingPhotographyCameraPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
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

    private static CompletableFuture<Void> screenshotFuture;
    public static void setScreenshotFuture(CompletableFuture<Void> future) {
        screenshotFuture = future;
    }

    public static void openViewfinder(InteractionHand hand) {
        zoomAmount = 1.0f;
        handUsingPhotographyCamera = hand.toString();
        defaultMouseSensitivity = client.options.sensitivity().get();
        isHUDhidden = client.gui.hud.isHidden();
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
        cameraControlsOpen = !cameraControlsOpen;
        debugViewfinder("camera controls {}", cameraControlsOpen ? "open" : "closed");
    }

    public static void cycleCompositionGuide() {
        SETTINGS.cycleCompositionGuide();
        debugViewfinder("composition guide set to {}", SETTINGS.compositionGuide().label());
    }

    public static void cycleSelfTimer() {
        SETTINGS.cycleSelfTimer();
        debugViewfinder("self timer set to {}", SETTINGS.selfTimer().label());
    }

    public static void cycleShutterSpeed() {
        SETTINGS.cycleShutterSpeed();
        debugViewfinder("shutter speed set to {}", SETTINGS.shutterSpeed().label());
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

        if (client.options.getCameraType().isFirstPerson() && client.gui.screen() == null) {
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
        spyglassFlashOpacity = 0.0f;
        viewfinderScale = 0.5f;
        canTakePhoto = false;
        zoomAmount = 1.0f;
        PhotographyHud.isUsingPhotographyCamera = false;
        renderViewfinderMask = true;
        suppressViewfinderOverlayForCapture = false;
        cameraControlsOpen = false;
        loggedOverlayRender = false;
        debugViewfinder("viewfinder close");
        if (client.player != null) {
            client.player.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0f, 1.0f);
        }
        SetUsingPhotographyCameraPayload payload = new SetUsingPhotographyCameraPayload(isUsingPhotographyCamera, handUsingPhotographyCamera);
        ClientPlayNetworking.send(payload);
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
            renderCameraControls(context, k, l, i, j);
        }

        context.blitSprite(RenderPipelines.GUI_TEXTURED, CAMERA_SCOPE_FLASH, k, l, i, j, spyglassFlashOpacity);

        context.fill(RenderPipelines.GUI, 0, n, context.guiWidth(), context.guiHeight(), CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, 0, 0, context.guiWidth(), l, CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, 0, l, k, n, CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, m, l, context.guiWidth(), n, CommonColors.BLACK);

        //context.drawText(MinecraftClient.getInstance().textRenderer, "Hello, world!", k, l, 0xFFFFFFFF, false);

    }

    private static void renderCompositionGuide(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        Identifier texture = SETTINGS.compositionGuide().overlayTexture();
        if (texture != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height);
        }
    }

    private static void renderCameraControls(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        if (!cameraControlsOpen) {
            return;
        }

        int panelWidth = 176;
        int panelHeight = 42;
        int panelX = x + (width - panelWidth) / 2;
        int panelY = y + height - panelHeight - 18;
        int background = 0xaa000000;
        int foreground = 0xffffffff;

        context.fill(RenderPipelines.GUI, panelX, panelY, panelX + panelWidth, panelY + panelHeight, background);
        renderControlSlot(context, panelX + 8, panelY + 7, SETTINGS.compositionGuide().controlSprite(), "C " + SETTINGS.compositionGuide().label(), foreground);
        renderControlSlot(context, panelX + 64, panelY + 7, SETTINGS.selfTimer().controlSprite(), "T " + SETTINGS.selfTimer().label(), foreground);
        renderControlSlot(context, panelX + 120, panelY + 7, SETTINGS.shutterSpeed().controlSprite(), "S " + SETTINGS.shutterSpeed().label(), foreground);
    }

    private static void renderControlSlot(GuiGraphicsExtractor context, int x, int y, Identifier sprite, String label, int color) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16);
        context.text(client.font, Component.literal(label), x - 2, y + 20, color, false);
    }

    public static void debugViewfinder(String message, Object... args) {
        if (DEBUG_VIEWFINDER) {
            Photography.LOGGER.info("[viewfinder] " + message, args);
        }
    }
}
