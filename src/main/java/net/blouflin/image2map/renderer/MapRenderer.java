package net.blouflin.image2map.renderer;

import net.blouflin.image2map.Image2Map;
import net.blouflin.photography.PhotographyUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Arrays;
import java.util.Objects;

public class MapRenderer {
    private static final double[] SHADE_COEFFS = {0.71, 0.86, 1.0, 0.53};

    public static MapColor[] getColors() {
        MapColor[] colors = new MapColor[64];
        for (int i = 0; i <= 63; i++) {
            colors[i] = MapColor.byId(i);
        }
        return colors;
    }

    private static double distance(double[] vectorA, double[] vectorB) {
        return Math.sqrt(Math.pow(vectorA[0] - vectorB[0], 2) + Math.pow(vectorA[1] - vectorB[1], 2)
                + Math.pow(vectorA[2] - vectorB[2], 2));
    }

    private static double[] applyShade(double[] color, int ind) {
        double coeff = SHADE_COEFFS[ind];
        return new double[]{color[0] * coeff, color[1] * coeff, color[2] * coeff};
    }

    public static ItemStack render(int[][] pixels, Image2Map.DitherMode mode, ServerLevel world, double x, double z,
                                   Player player) {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        MapId id = world.getFreeMapId();
        CompoundTag nbt = new CompoundTag();
        nbt.putString("dimension", player.level().dimension().identifier().toString());
        nbt.putInt("xCenter", (int) player.getX());
        nbt.putInt("zCenter", (int) player.getZ());
        nbt.putBoolean("locked", true);
        nbt.putBoolean("unlimitedTracking", false);
        nbt.putBoolean("showDecorations", false);
        nbt.putByte("scale", (byte) 3);
        nbt.put("banners", new ListTag());
        nbt.put("frames", new ListTag());
        MapItemSavedData state = PhotographyUtil.fromNbt(nbt);

        world.setMapData(id, state);
        stack.set(DataComponents.MAP_ID, id);

        renderPixels(pixels, mode, state);
        return stack;
    }

    public static MapItemSavedData render(int[][] pixels, Image2Map.DitherMode mode, int id, MapItemSavedData state) {
        renderPixels(pixels, mode, state);
        return state;
    }

    private static void renderPixels(int[][] pixels, Image2Map.DitherMode mode, MapItemSavedData state) {
        int width = pixels[0].length;
        int height = pixels.length;
        MapColor[] mapColors = Arrays.stream(getColors()).filter(Objects::nonNull).toArray(MapColor[]::new);

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (mode.equals(Image2Map.DitherMode.FLOYD)) {
                    state.colors[i + j * width] = (byte) floydDither(mapColors, pixels, i, j, pixels[j][i]);
                } else {
                    state.colors[i + j * width] = (byte) nearestColor(mapColors, pixels[j][i]);
                }
            }
        }
    }

    private static int mapColorToRGBColor(MapColor[] colors, int color) {
        int mcColor = colors[color >> 2].col;
        double[] mcColorVec = {(double) red(mcColor), (double) green(mcColor), (double) blue(mcColor)};
        double coeff = SHADE_COEFFS[color & 3];
        return argb(255, (int) (mcColorVec[0] * coeff), (int) (mcColorVec[1] * coeff), (int) (mcColorVec[2] * coeff));
    }

    private static int floydDither(MapColor[] mapColors, int[][] pixels, int x, int y, int imageColor) {
        int colorIndex = nearestColor(mapColors, imageColor);
        int palletedColor = mapColorToRGBColor(mapColors, colorIndex);
        int errorR = red(imageColor) - red(palletedColor);
        int errorG = green(imageColor) - green(palletedColor);
        int errorB = blue(imageColor) - blue(palletedColor);

        if (pixels[0].length > x + 1) {
            pixels[y][x + 1] = applyError(pixels[y][x + 1], errorR, errorG, errorB, 7.0 / 16.0);
        }
        if (pixels.length > y + 1) {
            if (x > 0) {
                pixels[y + 1][x - 1] = applyError(pixels[y + 1][x - 1], errorR, errorG, errorB, 3.0 / 16.0);
            }
            pixels[y + 1][x] = applyError(pixels[y + 1][x], errorR, errorG, errorB, 5.0 / 16.0);
            if (pixels[0].length > x + 1) {
                pixels[y + 1][x + 1] = applyError(pixels[y + 1][x + 1], errorR, errorG, errorB, 1.0 / 16.0);
            }
        }

        return colorIndex;
    }

    private static int applyError(int pixelColor, int errorR, int errorG, int errorB, double quantConst) {
        int pR = clamp(red(pixelColor) + (int) ((double) errorR * quantConst), 0, 255);
        int pG = clamp(green(pixelColor) + (int) ((double) errorG * quantConst), 0, 255);
        int pB = clamp(blue(pixelColor) + (int) ((double) errorB * quantConst), 0, 255);
        return argb(alpha(pixelColor), pR, pG, pB);
    }

    private static int clamp(int i, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("max value cannot be less than min value");
        }
        if (i < min) {
            return min;
        }
        if (i > max) {
            return max;
        }
        return i;
    }

    private static int nearestColor(MapColor[] colors, int imageColor) {
        double[] imageVec = {(double) red(imageColor) / 255.0, (double) green(imageColor) / 255.0,
                (double) blue(imageColor) / 255.0};
        int bestColor = 0;
        double lowestDistance = 10000;
        for (int k = 0; k < colors.length; k++) {
            int mcColor = colors[k].col;
            double[] mcColorVec = {(double) red(mcColor) / 255.0, (double) green(mcColor) / 255.0,
                    (double) blue(mcColor) / 255.0};
            for (int shadeInd = 0; shadeInd < SHADE_COEFFS.length; shadeInd++) {
                double distance = distance(imageVec, applyShade(mcColorVec, shadeInd));
                if (distance < lowestDistance) {
                    lowestDistance = distance;
                    if (k == 0 && alpha(imageColor) == 255) {
                        bestColor = 119;
                    } else {
                        bestColor = k * SHADE_COEFFS.length + shadeInd;
                    }
                }
            }
        }
        return bestColor;
    }

    private static int alpha(int color) {
        return (color >>> 24) & 0xff;
    }

    private static int red(int color) {
        return (color >>> 16) & 0xff;
    }

    private static int green(int color) {
        return (color >>> 8) & 0xff;
    }

    private static int blue(int color) {
        return color & 0xff;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
