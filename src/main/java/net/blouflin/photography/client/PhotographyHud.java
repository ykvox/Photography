package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyCamera;
import net.blouflin.photography.PhotographyPaper;
import net.blouflin.photography.networking.CreateMapStatePayload;
import net.blouflin.photography.networking.CameraPhysicalSoundPayload;
import net.blouflin.photography.networking.SetUsingPhotographyCameraPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicLong;

public class PhotographyHud {

    public static boolean isUsingPhotographyCamera = false;
    public static float spyglassFlashOpacity = 0.0f;
    public static float viewfinderScale = 1.0f;
    public static boolean canTakePhoto = false;
    public static boolean isTakingPhoto = false;
    public static boolean isHUDhidden;
    public static String handUsingPhotographyCamera = InteractionHand.MAIN_HAND.name();
    public static double zoomAmount;
    private static double focalLengthMillimeters = 35.0d;
    public static double defaultMouseSensitivity;
    public static final Identifier VIEWFINDER_MASK = Identifier.fromNamespaceAndPath("photography","textures/gui/viewfinder/viewfinder.png");
    public static final Identifier CAMERA_SCOPE_FLASH = Identifier.fromNamespaceAndPath("photography","camera_scope_flash");
    public static boolean renderViewfinderMask = true;
    public static boolean cameraControlsOpen = false;
    public static final PhotographyCameraSettings SETTINGS = new PhotographyCameraSettings();
    public static CameraState cameraState = CameraState.CLOSED;
    private static final long VIEWFINDER_ANIMATION_MS = 300L;
    private static final long CONTROLS_STRIP_ANIMATION_MS = 180L;
    private static final int CONTROLS_STRIP_SLIDE_PIXELS = 34;
    private static final int MIN_FOCAL_MM = 12;
    private static final int MAX_FOCAL_MM = 350;
    private static final int CAPTURE_COOLDOWN_TICKS = 10;
    private static final float FLASH_CAPTURE_BRIGHTNESS_MULTIPLIER = 2.8f;
    private static final float VIEWFINDER_INITIAL_SCALE = 0.5f;
    private static final Minecraft client = Minecraft.getInstance();
    private static final KeyMapping escapeKeybinding = new KeyMapping("key.keyboard.escape", GLFW.GLFW_KEY_ESCAPE, KeyMapping.Category.MISC);
    private static final boolean DEBUG_VIEWFINDER = Boolean.getBoolean("photography.debugViewfinder");
    private static boolean loggedOverlayRender;
    private static CameraType previousCameraType;
    private static boolean selfieEnabled;
    private static final AtomicLong SESSION_SEQUENCE = new AtomicLong();
    private static final AtomicLong CAPTURE_SEQUENCE = new AtomicLong();
    private static long viewfinderSessionId;
    private static String activeCaptureId = "capture-unset";
    private static int visualShutterTicks;
    private static int visualShutterColor = CommonColors.BLACK;
    private static PhotographyCameraSettings.ShutterSpeed activeShutterSpeed = PhotographyCameraSettings.ShutterSpeed.AUTO;
    private static boolean activeResolvedFlash;
    private static int timerTicksRemaining;
    private static int captureCooldownTicks;
    private static int lastLoggedTimerSecond = -1;
    private static String pendingTimerSource = "";
    private static int renderedFramesSinceOpen;
    private static int layoutDebugFramesRemaining;
    private static int layoutStableFrames;
    private static long viewfinderAnimationStartedMs;
    private static ControlsStripState controlsStripState = ControlsStripState.CLOSED;
    private static long controlsStripAnimationStartedMs;
    private static boolean controlsScreenCreatedThisSession;
    private static boolean loggedControlsFirstOpenThisSession;
    private static ControlHit pressedControl = ControlHit.NONE;
    private static double selfieCameraXRot;
    private static double selfieCameraYRot;

    public static void openViewfinder(InteractionHand hand) {
        if (isUsingPhotographyCamera && cameraState != CameraState.CLOSING && cameraState != CameraState.CLOSED) {
            return;
        }
        zoomAmount = 1.0f;
        focalLengthMillimeters = SETTINGS.focalLength().millimeters();
        handUsingPhotographyCamera = hand.toString();
        defaultMouseSensitivity = client.options.sensitivity().get();
        isHUDhidden = client.gui.hud.isHidden();
        previousCameraType = client.options.getCameraType();
        selfieEnabled = false;
        resetSelfieCameraRotation();
        debugViewfinder("previous perspective stored: {}", previousCameraType);
        if (!client.gui.hud.isHidden()) { client.gui.hud.toggle(); }
        isUsingPhotographyCamera = true;
        applyCameraModelState(false);
        viewfinderSessionId = SESSION_SEQUENCE.incrementAndGet();
        startViewfinderAnimation(CameraState.OPENING, "open-viewfinder");
        playViewfinderSound(true);
        renderViewfinderMask = true;
        cameraControlsOpen = false;
        setControlsStripState(ControlsStripState.CLOSED, "open-viewfinder");
        PhotographyCameraControlsScreen.suppressRenderForReadback = false;
        visualShutterTicks = 0;
        timerTicksRemaining = 0;
        captureCooldownTicks = 0;
        lastLoggedTimerSecond = -1;
        renderedFramesSinceOpen = 0;
        layoutStableFrames = 0;
        layoutDebugFramesRemaining = 5;
        loggedOverlayRender = false;
        controlsScreenCreatedThisSession = false;
        loggedControlsFirstOpenThisSession = false;
        logViewfinderInput("open-viewfinder");
        debugViewfinder("viewfinder open session={} ({})", viewfinderSessionId, handUsingPhotographyCamera);
        SetUsingPhotographyCameraPayload payload = new SetUsingPhotographyCameraPayload(isUsingPhotographyCamera, handUsingPhotographyCamera, selfieEnabled);
        ClientPlayNetworking.send(payload);
    }

    public static void toggleCameraControls() {
        ControlsStripState oldState = controlsStripState;
        boolean screenCreatedBeforeToggle = client.gui.screen() instanceof PhotographyCameraControlsScreen;
        if (cameraControlsOpen) {
            closeCameraControlsScreen();
        } else {
            openCameraControls();
        }
        if (!loggedControlsFirstOpenThisSession && oldState == ControlsStripState.CLOSED && controlsStripState == ControlsStripState.OPENING) {
            loggedControlsFirstOpenThisSession = true;
            Photography.LOGGER.info("[controls-strip-first-open] screenCreated={} renderBackground=false state={}",
                    screenCreatedBeforeToggle,
                    controlsStripState);
        }
        Photography.LOGGER.info("controls toggle: old={} new={} captureState={} shutterFeedback={} currentScreen={}",
                oldState,
                controlsStripState,
                cameraState,
                visualShutterTicks > 0,
                currentScreenClassName());
    }

    public static void handleRightClickWhileActive() {
        if (!isUsingPhotographyCamera) {
            return;
        }
        toggleCameraControls();
    }

    public static void openCameraControls() {
        if (controlsStripState == ControlsStripState.OPEN || controlsStripState == ControlsStripState.OPENING) {
            cameraControlsOpen = true;
            return;
        }
        boolean firstOpen = !loggedControlsFirstOpenThisSession;
        cameraControlsOpen = true;
        startControlsStripAnimation(ControlsStripState.OPENING, "toggle-open");
        layoutDebugFramesRemaining = 5;
        layoutStableFrames = 0;
        debugViewfinder("controls opened");
        client.mouseHandler.releaseMouse();
        PhotographyCameraSounds.playPrint();
        Photography.LOGGER.info("[controls-flicker] firstOpen={} screen={} background=false stripState={} viewfinderState={}",
                firstOpen,
                currentScreenClassName(),
                controlsStripState,
                cameraState);
        logViewfinderInput("controls-open");
    }

    public static void closeCameraControls() {
        if (!cameraControlsOpen || controlsStripState == ControlsStripState.CLOSING || controlsStripState == ControlsStripState.CLOSED) {
            return;
        }
        startControlsStripAnimation(ControlsStripState.CLOSING, "toggle-close");
        PhotographyCameraSounds.playPrint();
        pressedControl = ControlHit.NONE;
        logViewfinderInput("controls-close-start");
        layoutDebugFramesRemaining = 5;
        layoutStableFrames = 0;
        debugViewfinder("controls closed");
    }

    public static void closeCameraControlsScreen() {
        closeCameraControls();
    }

    public static void cycleCompositionGuide() {
        String oldValue = SETTINGS.compositionGuide().label();
        SETTINGS.cycleCompositionGuide();
        logSettingChange("composition_guide", oldValue, SETTINGS.compositionGuide().label());
    }

    public static void cycleSelfTimer() {
        String oldValue = SETTINGS.selfTimer().label();
        SETTINGS.cycleSelfTimer();
        logSettingChange("self_timer", oldValue, SETTINGS.selfTimer().label());
    }

    public static void cycleShutterSpeed() {
        String oldValue = SETTINGS.shutterSpeed().label();
        SETTINGS.cycleShutterSpeed();
        logSettingChange("shutter_speed", oldValue, SETTINGS.shutterSpeed().label());
    }

    public static void cycleFocalLength() {
        String oldValue = SETTINGS.focalLength().label();
        SETTINGS.cycleFocalLength();
        focalLengthMillimeters = SETTINGS.focalLength().millimeters();
        zoomAmount = 1.0d;
        logSettingChange("focal_length", oldValue, SETTINGS.focalLength().label());
    }

    public static void cycleFlashMode() {
        String oldValue = SETTINGS.flashMode().label();
        SETTINGS.cycleFlashMode();
        logSettingChange("flash", oldValue, SETTINGS.flashMode().label());
    }

    private static void logSettingChange(String setting, String oldValue, String newValue) {
        Photography.LOGGER.info("[PhotographyControls] session={} setting={} old={} new={}",
                viewfinderSessionId, setting, oldValue, newValue);
    }

    public static void toggleSelfie() {
        if (!isUsingPhotographyCamera) {
            return;
        }

        selfieEnabled = !selfieEnabled;
        client.options.setCameraType(selfieEnabled ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        resetSelfieCameraRotation();
        applyCameraModelState(selfieEnabled);
        ClientPlayNetworking.send(new SetUsingPhotographyCameraPayload(isUsingPhotographyCamera, handUsingPhotographyCamera, selfieEnabled));
        debugViewfinder("selfie toggled {}", selfieEnabled ? "on" : "off");
    }

    public static boolean isSelfieEnabled() {
        return selfieEnabled;
    }

    public static double selfieCameraXRot() {
        return selfieCameraXRot;
    }

    public static double selfieCameraYRot() {
        return selfieCameraYRot;
    }

    private static void resetSelfieCameraRotation() {
        selfieCameraXRot = 0.0d;
        selfieCameraYRot = 0.0d;
    }

    public static boolean canUseViewfinderInCurrentPerspective() {
        return true;
    }

    public static boolean requestCapture(String source) {
        if (isTakingPhoto) {
            logCaptureState("request ignored", "in_progress");
            logCaptureCooldown(false);
            return false;
        }
        if (!isUsingPhotographyCamera || !canTakePhoto || timerTicksRemaining > 0) {
            String reason = !isUsingPhotographyCamera ? "viewfinder_closed" : timerTicksRemaining > 0 ? "timer_pending" : "cannot_take_photo";
            logCaptureState("request ignored", reason);
            logCaptureCooldown(false);
            debugViewfinder("capture ignored source={} canTakePhoto={} isTakingPhoto={} state={}", source, canTakePhoto, isTakingPhoto, cameraState);
            return false;
        }
        if (blockCaptureWithoutPhotographicPaper()) {
            logCaptureCooldown(false);
            return false;
        }
        if (captureCooldownTicks > 0) {
            logCaptureState("request ignored", "cooldown");
            logCaptureCooldown(false);
            return false;
        }

        if (!SETTINGS.selfTimer().isOff()) {
            timerTicksRemaining = SETTINGS.selfTimer().ticks();
            pendingTimerSource = source;
            canTakePhoto = false;
            lastLoggedTimerSecond = timerDisplayedSeconds();
            PhotographyCameraSounds.playTimerTick();
            logCaptureState("request accepted", "timer_started");
            logCaptureCooldown(true);
            debugViewfinder("timer started source={} seconds={} ticks={}", source, SETTINGS.selfTimer().seconds(), timerTicksRemaining);
            return true;
        }

        startCaptureNow(source);
        return true;
    }

    private static void startCaptureNow(String source) {
        if (blockCaptureWithoutPhotographicPaper()) {
            return;
        }
        activeCaptureId = "capture-" + CAPTURE_SEQUENCE.incrementAndGet();
        activeResolvedFlash = resolveFlashForNextCapture();
        activeShutterSpeed = resolveShutterSpeedForNextCapture();
        visualShutterColor = activeResolvedFlash ? 0xeeffffff : 0xee000000;
        boolean lastFrameAdvance = CAPTURE_SEQUENCE.get() % 12L == 0L;
        Photography.LOGGER.info("[PhotographyCaptureDebug] capture accepted id={} source={} session={} state={} configuredShutter={} resolvedShutter={} shutterDurationMs={} visualShutterTicks={} flashMode={} resolvedFlash={} shutterFeedbackColor={}",
                activeCaptureId, source, viewfinderSessionId, cameraState, SETTINGS.shutterSpeed().label(), activeShutterSpeed.label(), activeShutterSpeed.durationMilliseconds(), activeShutterSpeed.visualTicks(), SETTINGS.flashMode().label(), activeResolvedFlash, String.format("0x%08x", visualShutterColor));
        logCaptureState("request accepted", "immediate");
        debugViewfinder("{} capture accepted id={} session={} state={} flashMode={} resolvedFlash={} shutterFeedbackColor={}",
                source, activeCaptureId, viewfinderSessionId, cameraState, SETTINGS.flashMode().label(), activeResolvedFlash, String.format("0x%08x", visualShutterColor));
        canTakePhoto = false;
        isTakingPhoto = true;
        captureCooldownTicks = CAPTURE_COOLDOWN_TICKS;
        visualShutterTicks = activeShutterSpeed.visualTicks();
        if (activeResolvedFlash) {
            PhotographyFlashDynamicLight.startFlashLight();
        }
        long visualStartTick = client.level == null ? -1L : client.level.getGameTime();
        long visualEndTick = visualStartTick < 0L ? -1L : visualStartTick + visualShutterTicks;
        Photography.LOGGER.info("[shutter-timing] speed={} visualStart={} visualEnd={} soundOpen={} soundClose={}",
                activeShutterSpeed.notation(),
                visualStartTick,
                visualEndTick,
                visualStartTick,
                visualEndTick);
        ClientPlayNetworking.send(new CreateMapStatePayload(activeResolvedFlash, visualShutterTicks, lastFrameAdvance));
        logCaptureCooldown(true);
    }

    private static boolean blockCaptureWithoutPhotographicPaper() {
        Player player = client.player;
        if (player == null || player.isCreative() || hasPhotographicPaper(player)) {
            return false;
        }

        PhotographyCameraSounds.playButtonRelease();
        canTakePhoto = isUsingPhotographyCamera;
        isTakingPhoto = false;
        timerTicksRemaining = 0;
        pendingTimerSource = "";
        Photography.LOGGER.info("[camera-shot] blocked=no_photographic_paper mode=survival_adventure");
        logCaptureState("request ignored", "no_photographic_paper");
        return true;
    }

    private static boolean hasPhotographicPaper(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (PhotographyPaper.isPhotographicPaper(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    public static String activeCaptureId() {
        return activeCaptureId;
    }

    public static long viewfinderSessionId() {
        return viewfinderSessionId;
    }

    public static String cameraStateName() {
        return cameraState.name().toLowerCase();
    }

    public static double currentFovMultiplier() {
        return 35.0d / focalLengthMillimeters;
    }

    public static int currentFocalLengthMm() {
        return Math.max(MIN_FOCAL_MM, Math.min(MAX_FOCAL_MM, Math.round((float) focalLengthMillimeters)));
    }

    public static String currentFocalLengthLabel() {
        return currentFocalLengthMm() + "mm";
    }

    public static void adjustZoomFromScroll(double vertical) {
        int oldFocalLength = currentFocalLengthMm();
        if (vertical > 0) {
            focalLengthMillimeters /= 1.08d;
        } else if (vertical < 0) {
            focalLengthMillimeters *= 1.08d;
        }
        focalLengthMillimeters = Mth.clamp(focalLengthMillimeters, MIN_FOCAL_MM, MAX_FOCAL_MM);
        zoomAmount = 35.0d / focalLengthMillimeters;
        if (client.options != null) {
            client.options.sensitivity().set(defaultMouseSensitivity * Mth.clamp(zoomAmount / 2.0d, 0.15d, 1.0d));
        }
        int newFocalLength = currentFocalLengthMm();
        if (newFocalLength != oldFocalLength) {
            PhotographyCameraSounds.playRateLimitedScrollLensRing();
            debugViewfinder("zoom scroll focalLength {} -> {} zoom={} fovMultiplier={}",
                    oldFocalLength, newFocalLength, zoomAmount, currentFovMultiplier());
        }
    }

    public static boolean visualShutterActive() {
        return visualShutterTicks > 0;
    }

    public static int visualShutterColor() {
        return visualShutterColor;
    }

    public static boolean activeResolvedFlash() {
        return activeResolvedFlash;
    }

    public static float shutterSpeedBrightnessMultiplier() {
        return activeShutterSpeed.brightnessMultiplier();
    }

    public static float flashBrightnessMultiplier() {
        return activeResolvedFlash ? FLASH_CAPTURE_BRIGHTNESS_MULTIPLIER : 1.0f;
    }

    public static float totalCaptureBrightnessMultiplier() {
        return Mth.clamp(shutterSpeedBrightnessMultiplier() * flashBrightnessMultiplier(), 0.45f, 3.4f);
    }

    public static String activeShutterFeedbackColor() {
        return String.format("0x%08x", visualShutterColor);
    }

    public static String activeResolvedShutterSpeedNotation() {
        return activeShutterSpeed.notation();
    }

    public static boolean controlsScreenRenderSuppressedForReadback() {
        return PhotographyCameraControlsScreen.suppressRenderForReadback;
    }

    public static String currentScreenClassName() {
        return client.gui.screen() == null ? "null" : client.gui.screen().getClass().getName();
    }

    public static String timerCountdownLabel() {
        if (timerTicksRemaining <= 0) {
            return "";
        }
        int seconds = Math.max(1, (int) Math.ceil(timerTicksRemaining / 20.0d));
        return Integer.toString(seconds);
    }

    public static void renderPhotographyCameraOverlay(GuiGraphicsExtractor context) {

        updateViewfinderAnimation();
        updateControlsStripAnimation();

        if (isUsingPhotographyCamera
                && isViewfinderPerspective()
                && (client.gui.screen() == null || client.gui.screen() instanceof PhotographyCameraControlsScreen)) {
            if (!client.gui.hud.isHidden()) { client.gui.hud.toggle(); }
            checkIsPhotographyCameraOpen(client);
        if (!isHUDhidden) {
                renderViewfinderOverlay(context, viewfinderScale);
                renderControlsStrip(context);
            }
            spyglassFlashOpacity = 0.0f;

            if (!isTakingPhoto && timerTicksRemaining <= 0 && layoutStableFrames > 0 && cameraState == CameraState.OPEN) {
                canTakePhoto = true;
            } else {
                canTakePhoto = false;
            }

            if (escapeKeybinding.isDown()) {
                stopRenderPhotographyCameraOverlay();
            }
        } else {
            requestCloseViewfinder("invalid-render-context");
        }

    }

    public static void tickClient() {
        updateControlsStripAnimation();
        if (captureCooldownTicks > 0) {
            captureCooldownTicks--;
        }
        if (!isUsingPhotographyCamera) {
            return;
        }

        tickVisuals();
        if (timerTicksRemaining <= 0) {
            return;
        }

        timerTicksRemaining--;
        int displayedSecond = timerDisplayedSeconds();
        if (displayedSecond != lastLoggedTimerSecond) {
            lastLoggedTimerSecond = displayedSecond;
            if (displayedSecond > 0) {
                PhotographyCameraSounds.playTimerTick();
            }
            debugViewfinder("timer tick source={} remainingTicks={} displayedSeconds={}", pendingTimerSource, timerTicksRemaining, displayedSecond);
        }
        if (timerTicksRemaining <= 0) {
            String source = pendingTimerSource;
            pendingTimerSource = "";
            debugViewfinder("timer fired source={}", source);
            startCaptureNow(source + "-timer");
        }
    }

    private static void tickVisuals() {
        if (visualShutterTicks > 0) {
            visualShutterTicks--;
        }
    }

    public static void finishCapture() {
        isTakingPhoto = false;
        PhotographyCameraControlsScreen.suppressRenderForReadback = false;
        canTakePhoto = isUsingPhotographyCamera && cameraState == CameraState.OPEN && timerTicksRemaining <= 0;
        logCaptureState("reset", "pipeline_handoff_complete");
        Photography.LOGGER.info("[PhotographyCaptureDebug] capture completed id={} stateReset canTakePhoto={}", activeCaptureId, canTakePhoto);
        debugViewfinder("capture completed id={} stateReset canTakePhoto={}", activeCaptureId, canTakePhoto);
    }

    public static void stopRenderPhotographyCameraOverlay() {
        requestCloseViewfinder("close-request");
    }

    private static void requestCloseViewfinder(String reason) {
        if (!isUsingPhotographyCamera || cameraState == CameraState.CLOSING || cameraState == CameraState.CLOSED) {
            return;
        }
        startViewfinderAnimation(CameraState.CLOSING, reason);
        playViewfinderSound(false);
        canTakePhoto = false;
        captureCooldownTicks = 0;
        if (cameraControlsOpen) {
            closeCameraControls();
        }
        if (timerTicksRemaining > 0) {
            debugViewfinder("timer cancelled source={} remainingTicks={}", pendingTimerSource, timerTicksRemaining);
        }
        timerTicksRemaining = 0;
        pendingTimerSource = "";
        lastLoggedTimerSecond = -1;
    }

    private static void finishCloseViewfinder() {
        client.options.sensitivity().set(defaultMouseSensitivity);
        if (client.gui.hud.isHidden() != isHUDhidden) { client.gui.hud.toggle(); }
        if (client.gui.screen() instanceof PhotographyCameraControlsScreen) {
            client.setScreenAndShow(null);
            client.mouseHandler.grabMouse();
        }
        spyglassFlashOpacity = 0.0f;
        viewfinderScale = 1.0f;
        canTakePhoto = false;
        zoomAmount = 1.0f;
        focalLengthMillimeters = SETTINGS.focalLength().millimeters();
        PhotographyHud.isUsingPhotographyCamera = false;
        setCameraState(CameraState.CLOSED, "close-viewfinder-complete");
        renderViewfinderMask = true;
        cameraControlsOpen = false;
        setControlsStripState(ControlsStripState.CLOSED, "close-viewfinder-complete");
        PhotographyCameraControlsScreen.suppressRenderForReadback = false;
        visualShutterTicks = 0;
        timerTicksRemaining = 0;
        pendingTimerSource = "";
        lastLoggedTimerSecond = -1;
        renderedFramesSinceOpen = 0;
        layoutStableFrames = 0;
        layoutDebugFramesRemaining = 0;
        loggedOverlayRender = false;
        restorePreviousPerspective();
        debugViewfinder("viewfinder close");
        SetUsingPhotographyCameraPayload payload = new SetUsingPhotographyCameraPayload(isUsingPhotographyCamera, handUsingPhotographyCamera, selfieEnabled);
        ClientPlayNetworking.send(payload);
    }

    private static boolean isViewfinderPerspective() {
        CameraType cameraType = client.options.getCameraType();
        return cameraType == CameraType.FIRST_PERSON
                || cameraType == CameraType.THIRD_PERSON_BACK
                || cameraType == CameraType.THIRD_PERSON_FRONT;
    }

    private static boolean resolveFlashForNextCapture() {
        if (SETTINGS.flashMode().isOn()) {
            return true;
        }
        if (!SETTINGS.flashMode().isAuto()) {
            return false;
        }
        if (client.level == null || client.player == null) {
            return false;
        }

        BlockPos pos = client.player.blockPosition();
        int brightness = client.level.getMaxLocalRawBrightness(pos);
        boolean resolved = brightness < 8;
        debugViewfinder("flash auto resolved={} brightness={} pos={}", resolved, brightness, pos);
        return resolved;
    }

    private static PhotographyCameraSettings.ShutterSpeed resolveShutterSpeedForNextCapture() {
        if (SETTINGS.shutterSpeed() != PhotographyCameraSettings.ShutterSpeed.AUTO) {
            return SETTINGS.shutterSpeed();
        }
        if (activeResolvedFlash) {
            return PhotographyCameraSettings.ShutterSpeed.SPEED_1_125;
        }
        return PhotographyCameraSettings.ShutterSpeed.SPEED_1_60;
    }

    private static void restorePreviousPerspective() {
        if (previousCameraType != null) {
            client.options.setCameraType(previousCameraType);
            debugViewfinder("perspective restored: {}", previousCameraType);
            previousCameraType = null;
        }
        selfieEnabled = false;
        applyCameraModelState(false, false);
    }

    private static void applyCameraModelState(boolean selfieModel) {
        applyCameraModelState(isUsingPhotographyCamera, selfieModel);
    }

    private static void applyCameraModelState(boolean usingCamera, boolean selfieModel) {
        Player player = client.player;
        if (player == null) {
            return;
        }

        InteractionHand hand = InteractionHand.valueOf(handUsingPhotographyCamera);
        ItemStack stack = player.getItemInHand(hand);
        if (!PhotographyCamera.isPhotographyCamera(stack)) {
            return;
        }

        PhotographyCamera.setCameraModelState(stack, usingCamera, selfieModel);
        debugViewfinder("camera model state set to {}", !usingCamera ? "closed" : selfieModel ? "selfie" : "open");
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
        renderedFramesSinceOpen++;
        layoutStableFrames++;
        logOpeningLayoutIfNeeded(context, k, l, i, j, m, n);

        if (renderViewfinderMask) {
            context.blit(RenderPipelines.GUI_TEXTURED, VIEWFINDER_MASK, k, l, 0.0f, 0.0f, i, j, i, j);
            renderCompositionGuide(context, k, l, i, j);
        }

        context.blitSprite(RenderPipelines.GUI_TEXTURED, CAMERA_SCOPE_FLASH, k, l, i, j, spyglassFlashOpacity);

        context.fill(RenderPipelines.GUI, 0, n, context.guiWidth(), context.guiHeight(), CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, 0, 0, context.guiWidth(), l, CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, 0, l, k, n, CommonColors.BLACK);
        context.fill(RenderPipelines.GUI, m, l, context.guiWidth(), n, CommonColors.BLACK);

        if (visualShutterTicks > 0) {
            context.fill(RenderPipelines.GUI, k, l, m, n, visualShutterColor);
        }
    }

    private static void renderCompositionGuide(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        Identifier texture = SETTINGS.compositionGuide().overlayTexture();
        if (texture != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, width, height);
        }
    }

    private static int timerDisplayedSeconds() {
        if (timerTicksRemaining <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(timerTicksRemaining / 20.0d));
    }

    private static void logOpeningLayoutIfNeeded(GuiGraphicsExtractor context, int x, int y, int width, int height, int right, int bottom) {
        if (!DEBUG_VIEWFINDER || layoutDebugFramesRemaining <= 0) {
            return;
        }

        layoutDebugFramesRemaining--;
        PhotographyCameraControlsScreen.ControlsLayout controlsLayout =
                PhotographyCameraControlsScreen.computeLayout(context.guiWidth(), context.guiHeight());
        Photography.LOGGER.info("[viewfinder-layout] session={} frameSinceOpen={} stableFrames={} framebuffer={}x{} window={}x{} gui={}x{} mask x={} y={} w={} h={} bottom={} blackBars top=0,0,{}x{} bottom=0,{},{}x{} left=0,{},{}x{} right={},{},{}x{} controls x={} y={} w={} h={}",
                viewfinderSessionId,
                renderedFramesSinceOpen,
                layoutStableFrames,
                client.gameRenderer.mainRenderTarget().width,
                client.gameRenderer.mainRenderTarget().height,
                client.getWindow().getWidth(),
                client.getWindow().getHeight(),
                context.guiWidth(),
                context.guiHeight(),
                x,
                y,
                width,
                height,
                bottom,
                context.guiWidth(),
                y,
                bottom,
                context.guiWidth(),
                context.guiHeight() - bottom,
                y,
                x,
                height,
                right,
                y,
                context.guiWidth() - right,
                height,
                controlsLayout.stripX(),
                controlsLayout.stripY(),
                controlsLayout.stripWidth(),
                PhotographyCameraControlsScreen.BUTTON_HEIGHT);
    }

    private static void setCameraState(CameraState newState, String reason) {
        CameraState oldState = cameraState;
        cameraState = newState;
        if (oldState != newState) {
            Photography.LOGGER.info("[viewfinder-state] old={} new={} reason={}", oldState, newState, reason);
        }
    }

    private static void startViewfinderAnimation(CameraState newState, String reason) {
        viewfinderAnimationStartedMs = System.currentTimeMillis();
        setCameraState(newState, reason);
        updateViewfinderAnimation();
    }

    private static void startControlsStripAnimation(ControlsStripState newState, String reason) {
        controlsStripAnimationStartedMs = System.currentTimeMillis();
        setControlsStripState(newState, reason);
        updateControlsStripAnimation();
    }

    private static void updateViewfinderAnimation() {
        if (cameraState == CameraState.OPENING || cameraState == CameraState.CLOSING) {
            float progress = Mth.clamp((System.currentTimeMillis() - viewfinderAnimationStartedMs) / (float) VIEWFINDER_ANIMATION_MS, 0.0f, 1.0f);
            float eased = easeOutExpo(progress);
            if (cameraState == CameraState.OPENING) {
                viewfinderScale = Mth.lerp(eased, VIEWFINDER_INITIAL_SCALE, 1.0f);
                if (progress >= 1.0f) {
                    setCameraState(CameraState.OPEN, "open-animation-complete");
                    viewfinderScale = 1.0f;
                }
            } else {
                viewfinderScale = Mth.lerp(eased, 1.0f, VIEWFINDER_INITIAL_SCALE);
                if (progress >= 1.0f) {
                    viewfinderScale = 1.0f;
                    finishCloseViewfinder();
                }
            }
        } else {
            viewfinderScale = 1.0f;
        }
    }

    private static float easeOutExpo(float progress) {
        if (progress >= 1.0f) {
            return 1.0f;
        }
        return (float) (1.0d - Math.pow(2.0d, -10.0d * progress));
    }

    private static void updateControlsStripAnimation() {
        if (controlsStripState == ControlsStripState.OPENING || controlsStripState == ControlsStripState.CLOSING) {
            float progress = Mth.clamp((System.currentTimeMillis() - controlsStripAnimationStartedMs) / (float) CONTROLS_STRIP_ANIMATION_MS, 0.0f, 1.0f);
            if (controlsStripState == ControlsStripState.OPENING && progress >= 1.0f) {
                setControlsStripState(ControlsStripState.OPEN, "open-animation-complete");
            } else if (controlsStripState == ControlsStripState.CLOSING && progress >= 1.0f) {
                finishCloseCameraControls();
            }
        }
    }

    private static void finishCloseCameraControls() {
        cameraControlsOpen = false;
        setControlsStripState(ControlsStripState.CLOSED, "close-animation-complete");
        if (client.gui.screen() instanceof PhotographyCameraControlsScreen) {
            client.setScreenAndShow(null);
        }
        client.mouseHandler.grabMouse();
        logViewfinderInput("controls-close-complete");
    }

    public static int controlsStripYOffset() {
        return Math.round((1.0f - controlsStripOpenProgress()) * CONTROLS_STRIP_SLIDE_PIXELS);
    }

    public static boolean controlsStripHitboxesActive() {
        return controlsStripOpenProgress() >= 0.85f;
    }

    public static float controlsStripOpenProgress() {
        return switch (controlsStripState) {
            case CLOSED -> 0.0f;
            case OPEN -> 1.0f;
            case OPENING -> easeOutCubic(Mth.clamp((System.currentTimeMillis() - controlsStripAnimationStartedMs) / (float) CONTROLS_STRIP_ANIMATION_MS, 0.0f, 1.0f));
            case CLOSING -> 1.0f - easeInCubic(Mth.clamp((System.currentTimeMillis() - controlsStripAnimationStartedMs) / (float) CONTROLS_STRIP_ANIMATION_MS, 0.0f, 1.0f));
        };
    }

    private static float easeOutCubic(float progress) {
        float inverse = 1.0f - progress;
        return 1.0f - inverse * inverse * inverse;
    }

    private static float easeInCubic(float progress) {
        return progress * progress * progress;
    }

    private static void setControlsStripState(ControlsStripState newState, String reason) {
        ControlsStripState oldState = controlsStripState;
        controlsStripState = newState;
        if (oldState != newState) {
            Photography.LOGGER.info("[controls-strip-state] old={} new={} reason={}", oldState, newState, reason);
        }
    }

    private static void playViewfinderSound(boolean open) {
        ClientPlayNetworking.send(new CameraPhysicalSoundPayload(open ? "viewfinder_open" : "viewfinder_close"));
        Photography.LOGGER.info("[viewfinder-sound] event={} session={} state={}",
                open ? "item.camera.viewfinder_open" : "item.camera.viewfinder_close",
                viewfinderSessionId,
                cameraState);
    }

    private static void logViewfinderInput(String reason) {
        Photography.LOGGER.info("[viewfinder-input] state={} screen={} mouseCaptured={} reason={}",
                controlsStripState,
                currentScreenClassName(),
                client.mouseHandler.isMouseGrabbed(),
                reason);
    }

    private static void logCaptureState(String event, String reason) {
        Photography.LOGGER.info("[PhotographyCaptureState] {} reason={} canTakePhoto={} captureInProgress={} pending={} cameraState={}",
                event,
                reason,
                canTakePhoto,
                isTakingPhoto,
                PhotographyCaptureTask.hasPendingCapture(),
                cameraState);
    }

    private static void logCaptureCooldown(boolean accepted) {
        Photography.LOGGER.info("[camera-cooldown] captureId={} accepted={} remainingTicks={} captureInProgress={}",
                activeCaptureId,
                accepted,
                captureCooldownTicks,
                isTakingPhoto || PhotographyCaptureTask.hasPendingCapture());
    }

    public static boolean shouldConsumeAttackInput() {
        return isUsingPhotographyCamera;
    }

    public static void debugViewfinder(String message, Object... args) {
        if (DEBUG_VIEWFINDER) {
            Photography.LOGGER.info("[viewfinder] " + message, args);
        }
    }

    public static boolean isDebugViewfinderEnabled() {
        return DEBUG_VIEWFINDER;
    }

    public enum CameraState {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING
    }

    public enum ControlsStripState {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING
    }

    public static boolean handleCameraMouseButton(int button, int action) {
        if (!isUsingPhotographyCamera) {
            return false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS) {
            handleRightClickWhileActive();
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (!cameraControlsOpen && controlsStripState == ControlsStripState.CLOSED) {
            if (action == GLFW.GLFW_PRESS) {
                boolean captureRequested = requestCapture("left-click");
                logConsumedAttack("viewfinder", "unknown", captureRequested);
            }
            return true;
        }

        if (action == GLFW.GLFW_RELEASE) {
            if (pressedControl.genericReleaseSound) {
                PhotographyCameraSounds.playButtonRelease();
            }
            pressedControl = ControlHit.NONE;
            return true;
        }

        if (action != GLFW.GLFW_PRESS) {
            return true;
        }

        if (!controlsStripHitboxesActive()) {
            return true;
        }

        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
        ControlsGeometry geometry = computeControlsGeometry(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        ControlHit hit = geometry.hit(mouseX, mouseY);
        pressedControl = hit;
        switch (hit) {
            case SHUTTER -> {
                cycleShutterSpeed();
                PhotographyCameraSounds.playDialClick();
                return true;
            }
            case FOCAL -> {
                cycleFocalLength();
                PhotographyCameraSounds.playLensRingBurst();
                return true;
            }
            case GUIDE -> {
                PhotographyCameraSounds.playButtonClick();
                cycleCompositionGuide();
                return true;
            }
            case TIMER -> {
                PhotographyCameraSounds.playButtonClick();
                cycleSelfTimer();
                return true;
            }
            case FLASH -> {
                PhotographyCameraSounds.playButtonClick();
                cycleFlashMode();
                return true;
            }
            case NONE -> {
                boolean captureRequested = requestCapture("left-click-settings-open");
                logConsumedAttack("settings", "miss", captureRequested);
                return true;
            }
        }
        return true;
    }

    private static void logConsumedAttack(String state, String target, boolean captureRequested) {
        if (DEBUG_VIEWFINDER) {
            Photography.LOGGER.info("[camera-input] consumedAttack=true state={} target={} captureRequested={}",
                    state, target, captureRequested);
        }
    }

    private static void renderControlsStrip(GuiGraphicsExtractor context) {
        if (controlsStripOpenProgress() <= 0.02f) {
            return;
        }

        ControlsGeometry geometry = computeControlsGeometry(context.guiWidth(), context.guiHeight());
        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
        ControlHit hoveredControl = controlsStripHitboxesActive() ? geometry.hit(mouseX, mouseY) : ControlHit.NONE;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, controlSprite(PhotographyCameraControlsScreen.SHUTTER_SPEED_SPRITES, ControlHit.SHUTTER, hoveredControl),
                geometry.shutterX(), geometry.shutterY(), 69, 12);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, controlSprite(PhotographyCameraControlsScreen.FOCAL_LENGTH_SPRITES, ControlHit.FOCAL, hoveredControl),
                geometry.focalX(), geometry.focalY(), PhotographyCameraControlsScreen.SIDE_BUTTONS_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT);

        int elementX = geometry.focalX() + PhotographyCameraControlsScreen.SIDE_BUTTONS_WIDTH;
        drawControlsSeparator(context, elementX, geometry.focalY());
        elementX += PhotographyCameraControlsScreen.SEPARATOR_WIDTH;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, controlSprite(PhotographyCameraControlsScreen.threeStateSprites(SETTINGS.compositionGuide().controlSprite()), ControlHit.GUIDE, hoveredControl),
                elementX, geometry.focalY(), PhotographyCameraControlsScreen.BUTTON_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT);
        elementX += PhotographyCameraControlsScreen.BUTTON_WIDTH;
        drawControlsSeparator(context, elementX, geometry.focalY());
        elementX += PhotographyCameraControlsScreen.SEPARATOR_WIDTH;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, controlSprite(PhotographyCameraControlsScreen.threeStateSprites(SETTINGS.selfTimer().controlSprite()), ControlHit.TIMER, hoveredControl),
                elementX, geometry.focalY(), PhotographyCameraControlsScreen.BUTTON_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT);
        elementX += PhotographyCameraControlsScreen.BUTTON_WIDTH;
        drawControlsSeparator(context, elementX, geometry.focalY());
        elementX += PhotographyCameraControlsScreen.SEPARATOR_WIDTH;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, controlSprite(PhotographyCameraControlsScreen.threeStateSprites(SETTINGS.flashMode().controlSprite()), ControlHit.FLASH, hoveredControl),
                elementX, geometry.focalY(), PhotographyCameraControlsScreen.SIDE_BUTTONS_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT);

        Font font = client.font;
        String shutterText = SETTINGS.shutterSpeed().notation().replace("1/", "");
        int shutterTextWidth = font.width(shutterText);
        context.text(font, shutterText, geometry.shutterX() + 69 / 2 - shutterTextWidth / 2 + 1, geometry.shutterY() + 3, 0xffffffff, false);

        Component focalText = Component.literal(currentFocalLengthLabel());
        int focalTextWidth = font.width(focalText);
        context.text(font, focalText, geometry.focalX() + 17 + (29 - focalTextWidth) / 2, geometry.focalY() + 7, 0xffffffff, false);
    }

    private static void drawControlsSeparator(GuiGraphicsExtractor context, int x, int y) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, PhotographyCameraControlsScreen.SEPARATOR_SPRITE, x, y,
                PhotographyCameraControlsScreen.SEPARATOR_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT);
    }

    private static Identifier controlSprite(net.minecraft.client.gui.components.WidgetSprites sprites, ControlHit control, ControlHit hoveredControl) {
        boolean pressed = pressedControl == control;
        boolean hovered = hoveredControl == control;
        Identifier sprite = sprites.get(true, hovered || pressed);
        if (DEBUG_VIEWFINDER && (hovered || pressed)) {
            Photography.LOGGER.info("[controls-render] button={} state={} sprite={}",
                    control.name().toLowerCase(), pressed ? "pressed" : "hovered", sprite);
        }
        return sprite;
    }

    private static ControlsGeometry computeControlsGeometry(int guiWidth, int guiHeight) {
        PhotographyCameraControlsScreen.ControlsLayout layout = PhotographyCameraControlsScreen.computeLayout(guiWidth, guiHeight);
        int yOffset = controlsStripYOffset();
        return new ControlsGeometry(
                layout.left() + 94,
                layout.top() + 226 + yOffset,
                layout.stripX(),
                layout.stripY() + yOffset);
    }

    private record ControlsGeometry(int shutterX, int shutterY, int focalX, int focalY) {
        private ControlHit hit(double mouseX, double mouseY) {
            if (inside(mouseX, mouseY, shutterX, shutterY, 69, 12)) {
                return ControlHit.SHUTTER;
            }
            int elementX = focalX;
            if (inside(mouseX, mouseY, elementX, focalY, PhotographyCameraControlsScreen.SIDE_BUTTONS_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT)) {
                return ControlHit.FOCAL;
            }
            elementX += PhotographyCameraControlsScreen.SIDE_BUTTONS_WIDTH + PhotographyCameraControlsScreen.SEPARATOR_WIDTH;
            if (inside(mouseX, mouseY, elementX, focalY, PhotographyCameraControlsScreen.BUTTON_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT)) {
                return ControlHit.GUIDE;
            }
            elementX += PhotographyCameraControlsScreen.BUTTON_WIDTH + PhotographyCameraControlsScreen.SEPARATOR_WIDTH;
            if (inside(mouseX, mouseY, elementX, focalY, PhotographyCameraControlsScreen.BUTTON_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT)) {
                return ControlHit.TIMER;
            }
            elementX += PhotographyCameraControlsScreen.BUTTON_WIDTH + PhotographyCameraControlsScreen.SEPARATOR_WIDTH;
            if (inside(mouseX, mouseY, elementX, focalY, PhotographyCameraControlsScreen.SIDE_BUTTONS_WIDTH, PhotographyCameraControlsScreen.BUTTON_HEIGHT)) {
                return ControlHit.FLASH;
            }
            return ControlHit.NONE;
        }

        private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private enum ControlHit {
        NONE(false),
        SHUTTER(false),
        FOCAL(false),
        GUIDE(true),
        TIMER(true),
        FLASH(true);

        private final boolean genericReleaseSound;

        ControlHit(boolean genericReleaseSound) {
            this.genericReleaseSound = genericReleaseSound;
        }
    }
}
