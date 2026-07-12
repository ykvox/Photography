package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PngWriter;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PhotographyCaptureDebug {
    public static final boolean DEBUG_CAPTURE_IMAGES = Boolean.getBoolean("photography.debugCaptureImages");
    private static boolean loggedCaptureDiagnosticsDisabled;
    private static final Map<String, StageEdgeStats> STAGE_EDGE_STATS = new LinkedHashMap<>();

    private PhotographyCaptureDebug() {
    }

    public static void logCaptureStart(String captureId) {
        STAGE_EDGE_STATS.clear();
        if (DEBUG_CAPTURE_IMAGES) {
            Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} diagnostics enabled; writing to {}", captureId, debugDirectory());
        } else if (!loggedCaptureDiagnosticsDisabled) {
            loggedCaptureDiagnosticsDisabled = true;
            Photography.LOGGER.info("[PhotographyCaptureDebug] capture diagnostics disabled; enable -Dphotography.debugCaptureImages=true");
        }
    }

    public static void logCaptureContext(String captureId, String cameraState, boolean settingsOpen, boolean selfieMode,
                                         double zoom, double fovMultiplier, int focalLength, String shutterSpeed,
                                         String flashMode, boolean resolvedFlash, String shutterFeedbackColor,
                                         float flashBrightnessMultiplier, float shutterSpeedBrightnessMultiplier,
                                         float totalBrightnessMultiplier) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} state={} settingsOpen={} selfie={} zoom={} fovMultiplier={} focalLength={}mm shutterSpeed={} flashMode={} resolvedFlash={} shutterFeedbackColor={} flashBrightnessMultiplier={} shutterSpeedBrightnessMultiplier={} totalBrightnessMultiplier={}",
                captureId, cameraState, settingsOpen, selfieMode, zoom, fovMultiplier, focalLength, shutterSpeed, flashMode, resolvedFlash, shutterFeedbackColor, flashBrightnessMultiplier, shutterSpeedBrightnessMultiplier, totalBrightnessMultiplier);
    }

    public static void logRawReadbackState(String captureId, boolean suppressHud, boolean controlsOpen,
                                           boolean visualShutter, float flashOpacity, boolean renderMask) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        String scissorState = String.valueOf(com.mojang.blaze3d.systems.RenderSystem.getScissorStateForRenderTypeDraws());
        Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} raw readback state: suppressHud={} controlsOpen={} visualShutter={} flash={} mask={} viewport=<unavailable> scissor={} framebuffer={}x{} window={}x{} gui={}x{}",
                captureId,
                suppressHud,
                controlsOpen,
                visualShutter,
                flashOpacity,
                renderMask,
                scissorState,
                client.gameRenderer.mainRenderTarget().width,
                client.gameRenderer.mainRenderTarget().height,
                window.getWidth(),
                window.getHeight(),
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight());
    }

    public static void logControlsOpenReadbackState(String captureId, String screenClass, boolean controlsOpen,
                                                    boolean suppressHud, boolean controlsRenderSuppressed,
                                                    boolean visualShutter, boolean screenBackground) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} controls-open readback state: screen={} controlsOpen={} suppressHud={} controlsRenderSuppressed={} visualShutter={} screenBackground={}",
                captureId,
                screenClass,
                controlsOpen,
                suppressHud,
                controlsRenderSuppressed,
                visualShutter,
                screenBackground);
    }

    public static void logRawReadbackComplete(String captureId, int[] pixels, int width, int height) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        EdgeStats top = analyzeHorizontalEdge(pixels, width, 0);
        EdgeStats bottom = analyzeHorizontalEdge(pixels, width, height - 1);
        EdgeStats left = analyzeVerticalEdge(pixels, width, height, 0);
        EdgeStats right = analyzeVerticalEdge(pixels, width, height, width - 1);
        Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} raw readback complete: bottomEdgeBlack={} topEdgeBlack={} leftEdgeBlack={} rightEdgeBlack={}",
                captureId, bottom.blackRatio(), top.blackRatio(), left.blackRatio(), right.blackRatio());
    }

    public static void writeArgb(String captureId, String stage, int[] pixels, int width, int height) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        logEdgeStats(captureId, stage, pixels, width, height);
        Path outputPath = debugDirectory().resolve(captureId + "_" + stage + ".png");
        try {
            PngWriter.writeArgb(outputPath, pixels, width, height);
            Photography.LOGGER.info("[PhotographyCaptureDebug] id={} wrote {}", captureId, outputPath);
        } catch (IOException e) {
            Photography.LOGGER.error("[PhotographyCaptureDebug] id={} failed to write {}", captureId, outputPath, e);
        }
    }

    public static void writeMapColors(String captureId, String stage, MapItemSavedData mapState) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        int size = (int) Math.sqrt(mapState.colors.length);
        if (size * size != mapState.colors.length) {
            Photography.LOGGER.warn("[PhotographyCaptureDebug] id={} cannot dump non-square map color array: {}", captureId, mapState.colors.length);
            return;
        }

        int[] pixels = new int[mapState.colors.length];
        for (int i = 0; i < mapState.colors.length; i++) {
            pixels[i] = MapColor.getColorFromPackedId(Byte.toUnsignedInt(mapState.colors[i]));
        }
        writeArgb(captureId, stage, pixels, size, size);
    }

    public static void identifyLikelyBlackEdgeSource(String captureId) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        String likelyStage = "unknown";
        double highestBottomBlackRatio = 0.0d;
        for (Map.Entry<String, StageEdgeStats> entry : STAGE_EDGE_STATS.entrySet()) {
            double ratio = entry.getValue().bottom.blackRatio();
            if (ratio > highestBottomBlackRatio) {
                highestBottomBlackRatio = ratio;
                likelyStage = entry.getKey();
            }
        }

        String reason = highestBottomBlackRatio > 0.25d
                ? "bottom edge contains many black pixels"
                : "no stage has a strongly black bottom edge; inspect PNGs for color/alpha artifact";
        Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} black-edge source stage={} likely cause={} bottomBlackRatio={}",
                captureId, likelyStage, reason, highestBottomBlackRatio);
    }

    public static void logSampleBounds(String captureId, String stage, int sourceSize, int destinationSize, int firstSource, int lastSource) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Photography.LOGGER.info("[PhotographyCaptureDebug] id={} {} sample bounds: sourceSize={}, destinationSize={}, firstSource={}, lastSource={}",
                captureId, stage, sourceSize, destinationSize, firstSource, lastSource);
    }

    public static void logCropSource(String captureId, int cropX, int cropY, int cropSize, int framebufferWidth, int framebufferHeight) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Photography.LOGGER.info("[PhotographyCaptureDebug] id={} crop source: x={}, y={}, size={}, framebuffer={}x{}, finalOutput=128x128",
                captureId, cropX, cropY, cropSize, framebufferWidth, framebufferHeight);
    }

    private static void logEdgeStats(String captureId, String stage, int[] pixels, int width, int height) {
        EdgeStats top = analyzeHorizontalEdge(pixels, width, 0);
        EdgeStats bottom = analyzeHorizontalEdge(pixels, width, height - 1);
        EdgeStats left = analyzeVerticalEdge(pixels, width, height, 0);
        EdgeStats right = analyzeVerticalEdge(pixels, width, height, width - 1);
        STAGE_EDGE_STATS.put(stage, new StageEdgeStats(top, bottom, left, right));

        Photography.LOGGER.info("[PhotographyCaptureDebug] id={} {} edge stats: top={}, bottom={}, left={}, right={}, size={}x{}",
                captureId, stage, top, bottom, left, right, width, height);
        Photography.LOGGER.info("[PhotographyCaptureDebug] id={} {} bottom edge stats: {}", captureId, stage, bottom);
    }

    private static EdgeStats analyzeHorizontalEdge(int[] pixels, int width, int y) {
        EdgeStats stats = new EdgeStats();
        for (int x = 0; x < width; x++) {
            stats.accept(pixels[x + y * width]);
        }
        return stats;
    }

    private static EdgeStats analyzeVerticalEdge(int[] pixels, int width, int height, int x) {
        EdgeStats stats = new EdgeStats();
        for (int y = 0; y < height; y++) {
            stats.accept(pixels[x + y * width]);
        }
        return stats;
    }

    private static final class EdgeStats {
        private int total;
        private int black;
        private int transparent;
        private int zero;

        private void accept(int argb) {
            total++;
            int alpha = (argb >>> 24) & 0xff;
            int red = (argb >>> 16) & 0xff;
            int green = (argb >>> 8) & 0xff;
            int blue = argb & 0xff;
            if (alpha == 0) {
                transparent++;
            }
            if ((argb & 0x00ffffff) == 0) {
                black++;
            }
            if (argb == 0) {
                zero++;
            }
        }

        private double blackRatio() {
            if (total == 0) {
                return 0.0d;
            }
            return (double) black / (double) total;
        }

        @Override
        public String toString() {
            return "black=" + black + "/" + total
                    + ", transparent=" + transparent + "/" + total
                    + ", zero=" + zero + "/" + total;
        }
    }

    private record StageEdgeStats(EdgeStats top, EdgeStats bottom, EdgeStats left, EdgeStats right) {
    }

    public static Path debugDirectory() {
        Minecraft client = Minecraft.getInstance();
        return client.gameDirectory.toPath().resolve("photography_debug");
    }
}
