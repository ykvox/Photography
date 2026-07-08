package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PngWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.IOException;
import java.nio.file.Path;

public final class PhotographyCaptureDebug {
    public static final boolean DEBUG_CAPTURE_IMAGES = Boolean.getBoolean("photography.debugCaptureImages");

    private PhotographyCaptureDebug() {
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

    public static void logSampleBounds(String stage, int sourceSize, int destinationSize, int firstSource, int lastSource) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Photography.LOGGER.info("[capture-images] {} sample bounds: sourceSize={}, destinationSize={}, firstSource={}, lastSource={}",
                stage, sourceSize, destinationSize, firstSource, lastSource);
    }

    private static void logEdgeStats(String stage, int[] pixels, int width, int height) {
        EdgeStats top = analyzeHorizontalEdge(pixels, width, 0);
        EdgeStats bottom = analyzeHorizontalEdge(pixels, width, height - 1);
        EdgeStats left = analyzeVerticalEdge(pixels, width, height, 0);
        EdgeStats right = analyzeVerticalEdge(pixels, width, height, width - 1);

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

        @Override
        public String toString() {
            return "black=" + black + "/" + total
                    + ", transparent=" + transparent + "/" + total
                    + ", zero=" + zero + "/" + total;
        }
    }

    private static Path debugDirectory() {
        Minecraft client = Minecraft.getInstance();
        return client.gameDirectory.toPath().resolve("photography_debug");
    }
}
