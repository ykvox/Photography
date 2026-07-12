package net.blouflin.photography.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.blouflin.photography.Photography;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/**
 * Exposure-style direct capture timing adapted to Fabric's HUD element order.
 * Readback happens at the first HUD layer: the world frame exists, but Photography/vanilla HUD and Screens
 * have not rendered into the framebuffer yet.
 */
public final class PhotographyCaptureTask {
    private static final Queue<CaptureRequest> REQUESTS = new ArrayDeque<>();
    private static long clientTicks;
    private static long cleanCaptureFrames;

    private PhotographyCaptureTask() {
    }

    public static CompletableFuture<NativeImage> captureBeforeHud(String captureId) {
        CompletableFuture<NativeImage> future = new CompletableFuture<>();
        boolean flashActive = PhotographyHud.activeResolvedFlash() && PhotographyFlashDynamicLight.isActive();
        CaptureRequest request = new CaptureRequest(captureId, future, flashActive);
        REQUESTS.add(request);
        Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} scheduled clean capture phase=before_hand delayFrames={} flashLateWorldFrames={}",
                captureId, request.delayFrames, request.flashLateWorldFramesToWait);
        return future;
    }

    public static boolean isCapturingCleanFrame() {
        return !REQUESTS.isEmpty();
    }

    public static boolean hasPendingCapture() {
        return !REQUESTS.isEmpty();
    }

    public static void tickClient(Minecraft client) {
        clientTicks++;
    }

    public static void extractEarlyHudCapture(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
        extractCleanCapture("early_hud_fallback");
    }

    public static void extractBeforeHandCapture() {
        extractCleanCapture("before_hand");
    }

    public static void extractCleanCapture(String phase) {
        if (REQUESTS.isEmpty()) {
            return;
        }

        cleanCaptureFrames++;
        Minecraft client = Minecraft.getInstance();
        Iterator<CaptureRequest> iterator = REQUESTS.iterator();
        while (iterator.hasNext()) {
            CaptureRequest request = iterator.next();
            if (request.extract(client, phase)) {
                iterator.remove();
            }
        }
    }

    private static final class CaptureRequest {
        private final String captureId;
        private final CompletableFuture<NativeImage> future;
        private final boolean flashActive;
        private int delayFrames;
        private int flashLateWorldFramesToWait;
        private int lateWorldFramesWaited;
        private int attempts;
        private boolean readbackInProgress;

        private CaptureRequest(String captureId, CompletableFuture<NativeImage> future, boolean flashActive) {
            this.captureId = captureId;
            this.future = future;
            this.flashActive = flashActive;
            this.delayFrames = flashActive ? 0 : 1;
            this.flashLateWorldFramesToWait = flashActive ? 2 : 0;
        }

        private boolean extract(Minecraft client, String phase) {
            if (future.isDone()) {
                return true;
            }
            if (readbackInProgress) {
                return false;
            }
            boolean lateWorldPhase = "after_world_before_hand".equals(phase);
            if (flashActive) {
                if (!lateWorldPhase) {
                    return false;
                }
                if (flashLateWorldFramesToWait > 0) {
                    flashLateWorldFramesToWait--;
                    lateWorldFramesWaited++;
                    return false;
                }
            }
            if (delayFrames > 0) {
                delayFrames--;
                return false;
            }

            attempts++;
            readbackInProgress = true;
            logReadbackState(client, phase);
            if (flashActive) {
                PhotographyFlashDynamicLight.markReadback(captureId, lateWorldFramesWaited);
            }
            final boolean wasHudHidden = client.gui.hud.isHidden();
            final boolean hideGuiChanged = !wasHudHidden;
            if (!wasHudHidden) {
                client.gui.hud.toggle();
            }
            try {
                Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), nativeImage -> {
                    try {
                        int[] pixels = nativeImage.getPixels();
                        if (isAllBlack(pixels) && attempts < 2) {
                            Photography.LOGGER.warn("[PhotographyCaptureDebug] capture id={} clean raw frame was fully black; retrying next render frame attempt={}",
                                    captureId, attempts);
                            nativeImage.close();
                            readbackInProgress = false;
                            delayFrames = flashActive ? 0 : 1;
                            flashLateWorldFramesToWait = flashActive ? 1 : 0;
                            return;
                        }
                        if (isAllBlack(pixels)) {
                            nativeImage.close();
                            future.completeExceptionally(new IllegalStateException("Clean capture raw frame was fully black after retry"));
                        } else {
                            future.complete(nativeImage);
                        }
                    } catch (RuntimeException e) {
                        nativeImage.close();
                        future.completeExceptionally(e);
                    }
                });
            } finally {
                if (hideGuiChanged && client.gui.hud.isHidden() != wasHudHidden) {
                    client.gui.hud.toggle();
                }
            }
            return false;
        }

        private void logReadbackState(Minecraft client, String phase) {
            Photography.LOGGER.info("[capture-phase] phase={} cloudsExpected={} beforeHand={} beforeHud={}",
                    phase,
                    "after_world_before_hand".equals(phase) || phase.startsWith("early_hud"),
                    "after_world_before_hand".equals(phase) || phase.startsWith("early_hud"),
                    true);
            Photography.LOGGER.info("[PhotographyCaptureDebug] clean capture hook phase={} currentScreen={} hideGui={} renderHandSuppressed={}",
                    phase,
                    PhotographyHud.currentScreenClassName(),
                    client.gui.hud.isHidden(),
                    isCapturingCleanFrame());
            Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} clean readback state: phase={} cleanFrame=true hideHud={} hideHand={} screen={} target=mainFramebuffer framebuffer={}x{} window={}x{} gui={}x{} delayedFrames={} attempt={} clientTicks={} cleanCaptureFrames={}",
                    captureId,
                    phase,
                    client.gui.hud.isHidden(),
                    isCapturingCleanFrame(),
                    PhotographyHud.currentScreenClassName(),
                    client.gameRenderer.mainRenderTarget().width,
                    client.gameRenderer.mainRenderTarget().height,
                    client.getWindow().getWidth(),
                    client.getWindow().getHeight(),
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight(),
                    lateWorldFramesWaited,
                    attempts,
                    clientTicks,
                    cleanCaptureFrames);
            if (flashActive) {
                Photography.LOGGER.info("[flash-light-timing] captureId={} resolvedFlash=true flagSetTick=started readbackTick=pending flagClearTick=pending framesWaited={}",
                        captureId,
                        lateWorldFramesWaited);
            }
        }

        private boolean isAllBlack(int[] pixels) {
            for (int pixel : pixels) {
                if ((pixel & 0x00ffffff) != 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
