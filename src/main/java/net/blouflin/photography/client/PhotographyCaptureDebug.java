package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PngWriter;
import net.minecraft.client.Minecraft;
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

    public static void logCaptureStart() {
        if (DEBUG_CAPTURE_IMAGES) {
            Photography.LOGGER.info("[PhotographyDebug] capture diagnostics enabled; writing to {}", debugDirectory());
        } else if (!loggedCaptureDiagnosticsDisabled) {
            loggedCaptureDiagnosticsDisabled = true;
            Photography.LOGGER.info("[PhotographyDebug] capture diagnostics disabled; enable -Dphotography.debugCaptureImages=true");
        }
    }

    public static void writeArgb(String stage, int[] pixels, int width, int height) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        logEdgeStats(stage, pixels, width, height);
        Path outputPath = debugDirectory().resolve(stage + ".png");
        try {
            PngWriter.writeArgb(outputPath, pixels, width, height);
            Photography.LOGGER.info("[capture-images] wrote {}", outputPath);
        } catch (IOException e) {
            Photography.LOGGER.error("[capture-images] failed to write {}", outputPath, e);
        }
    }

    public static void writeMapColors(String stage, MapItemSavedData mapState) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        int size = (int) Math.sqrt(mapState.colors.length);
        if (size * size != mapState.colors.length) {
            Photography.LOGGER.warn("[capture-images] cannot dump non-square map color array: {}", mapState.colors.length);
            return;
        }

        int[] pixels = new int[mapState.colors.length];
        for (int i = 0; i < mapState.colors.length; i++) {
            pixels[i] = MapColor.getColorFromPackedId(Byte.toUnsignedInt(mapState.colors[i]));
        }
        writeArgb(stage, pixels, size, size);
    }

    public static void identifyLikelyBlackEdgeSource() {
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
        Photography.LOGGER.info("[PhotographyDebug] black-edge source stage={} likely cause={} bottomBlackRatio={}",
                likelyStage, reason, highestBottomBlackRatio);
    }

    public static void logSampleBounds(String stage, int sourceSize, int destinationSize, int firstSource, int lastSource) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Photography.LOGGER.info("[capture-images] {} sample bounds: sourceSize={}, destinationSize={}, firstSource={}, lastSource={}",
                stage, sourceSize, destinationSize, firstSource, lastSource);
    }

    public static void logCropSource(int cropX, int cropY, int cropSize, int framebufferWidth, int framebufferHeight) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Photography.LOGGER.info("[capture-images] crop source: x={}, y={}, size={}, framebuffer={}x{}",
                cropX, cropY, cropSize, framebufferWidth, framebufferHeight);
    }

    private static void logEdgeStats(String stage, int[] pixels, int width, int height) {
        EdgeStats top = analyzeHorizontalEdge(pixels, width, 0);
        EdgeStats bottom = analyzeHorizontalEdge(pixels, width, height - 1);
        EdgeStats left = analyzeVerticalEdge(pixels, width, height, 0);
        EdgeStats right = analyzeVerticalEdge(pixels, width, height, width - 1);
        STAGE_EDGE_STATS.put(stage, new StageEdgeStats(top, bottom, left, right));

        Photography.LOGGER.info("[capture-images] {} edge stats: top={}, bottom={}, left={}, right={}, size={}x{}",
                stage, top, bottom, left, right, width, height);
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
