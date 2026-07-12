package net.blouflin.photography.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.blouflin.photography.PhotographyPhoto;
import net.blouflin.photography.client.PhotographyCaptureDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PhotographyPhotoRenderCache {
    private static final boolean DEBUG_PHOTO_RENDER = PhotographyCaptureDebug.DEBUG_CAPTURE_IMAGES;
    private static final int MAX_TEXTURES = 64;
    private static final int PREVIEW_SIZE = 80;
    private static final int PREVIEW_IMAGE_OFFSET = Math.round(PREVIEW_SIZE * 0.0625f);
    private static final int PREVIEW_IMAGE_SIZE = Math.round(PREVIEW_SIZE * 0.875f);
    private static final int HELD_SIZE = PhotographyPhoto.PHOTO_SIZE;
    private static final int HELD_IMAGE_OFFSET = Math.round(HELD_SIZE * 0.0625f);
    private static final int HELD_IMAGE_SIZE = Math.round(HELD_SIZE * 0.875f);
    private static final Identifier PAPER_TEXTURE = Identifier.fromNamespaceAndPath(
            "photography", "textures/item/exposure/photograph/photograph.png");
    private static final Map<Key, CachedTexture> TEXTURES = new LinkedHashMap<>(16, 0.75f, true);
    private static int cacheHits;
    private static int cacheMisses;
    private static int evictions;
    private static NativeImage paperImage;

    private PhotographyPhotoRenderCache() {
    }

    public static Identifier getPhotoTexture(ItemStack stack) {
        PhotographyPhoto.ImageData image = PhotographyPhoto.getImage(stack);
        if (image.isEmpty()) {
            return null;
        }
        return getTexture(image, TextureKind.PHOTO);
    }

    private static Identifier getTexture(PhotographyPhoto.ImageData image, TextureKind kind) {
        Key key = Key.of(image, kind);
        CachedTexture cached = TEXTURES.get(key);
        if (cached != null) {
            cacheHits++;
            logCacheStats(true);
            return cached.id;
        }
        cacheMisses++;
        logCacheStats(false);

        Identifier id = Identifier.fromNamespaceAndPath("photography",
                "dynamic/photo/" + kind.id + "/" + key.width + "x" + key.height + "/" + Integer.toUnsignedString(key.hash));
        NativeImage nativeImage = switch (kind) {
            case PHOTO -> createPhotoImage(image);
            case PREVIEW -> createFramedImage(image, PREVIEW_SIZE, PREVIEW_IMAGE_OFFSET, PREVIEW_IMAGE_SIZE);
            case HELD_FRAMED -> createHeldFramedImage(image);
        };

        DynamicTexture texture = new DynamicTexture(id::toString, nativeImage);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(id, texture);
        TEXTURES.put(key, new CachedTexture(id, texture, true));
        trimCache();
        return id;
    }

    public static void drawTooltipPreview(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int size) {
        PhotographyPhoto.ImageData image = PhotographyPhoto.getImage(stack);
        if (image.isEmpty()) {
            return;
        }
        Identifier texture = getTexture(image, TextureKind.PREVIEW);
        if (texture == null) {
            return;
        }
        if (DEBUG_PHOTO_RENDER) {
            net.blouflin.photography.Photography.LOGGER.info(
                    "[photo-preview] frame={} contentX={} contentY={} contentW={} contentH={} scale={}",
                    PAPER_TEXTURE, PREVIEW_IMAGE_OFFSET, PREVIEW_IMAGE_OFFSET, PREVIEW_IMAGE_SIZE, PREVIEW_IMAGE_SIZE,
                    String.format("%.4f", PREVIEW_IMAGE_SIZE / (float) image.width()));
        }
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, size, size, PREVIEW_SIZE, PREVIEW_SIZE);
    }

    public static void drawAlbumPhoto(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int size) {
        PhotographyPhoto.ImageData image = PhotographyPhoto.getImage(stack);
        drawAlbumPhoto(context, image, x, y, size);
    }

    public static void drawAlbumPhoto(GuiGraphicsExtractor context, PhotographyPhoto.ImageData image, int x, int y, int size) {
        if (image.isEmpty()) {
            return;
        }
        Identifier texture = getTexture(image, TextureKind.PHOTO);
        if (texture == null) {
            return;
        }
        int inset = Math.max(1, Math.round(size * (6.0f / 108.0f)));
        int imageSize = Math.max(1, size - inset * 2);
        context.blit(RenderPipelines.GUI_TEXTURED, PAPER_TEXTURE, x, y, 0.0f, 0.0f,
                size, size, 64, 64, 64, 64);
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x + inset, y + inset, 0.0f, 0.0f,
                imageSize, imageSize, image.width(), image.height(), image.width(), image.height());
        if (DEBUG_PHOTO_RENDER) {
            net.blouflin.photography.Photography.LOGGER.info(
                    "[album-photo-debug] cacheType=raw textureId={} actualSize={}x{} source=<0,0,{},{}> destination=<{},{},{},{}> poseScale=1 scissor=unknown",
                    texture, image.width(), image.height(), image.width(), image.height(),
                    x + inset, y + inset, imageSize, imageSize);
            net.blouflin.photography.Photography.LOGGER.info(
                    "[album-photo-layout] page=unknown slot={},{},{},{} imageRect={},{},{},{} source={}x{} cacheHit={}",
                    x, y, size, size, x + inset, y + inset, imageSize, imageSize, image.width(), image.height(),
                    TEXTURES.containsKey(Key.of(image, TextureKind.PHOTO)));
        }
    }

    public static boolean renderHeldPhotograph(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        return renderHeldPhotograph(stack, poseStack, collector, packedLight, "first_person", "unknown");
    }

    public static boolean renderHeldPhotograph(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector,
                                               int packedLight, String context, String hand) {
        PhotographyPhoto.ImageData image = PhotographyPhoto.getImage(stack);
        boolean hasImage = !image.isEmpty();
        Identifier texture = hasImage ? getTexture(image, TextureKind.HELD_FRAMED) : null;
        logHeldRender(stack, image, texture, context, hand);
        if (texture == null) {
            return false;
        }

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0f));
        poseStack.scale(0.38f, 0.38f, 0.38f);
        poseStack.translate(-0.5f, -0.5f, 0.0f);

        submitQuad(collector, poseStack, texture, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, packedLight);
        return true;
    }

    public static boolean renderItemFramePhotograph(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector,
                                                    int packedLight, boolean glowFrame, int rotation) {
        PhotographyPhoto.ImageData image = PhotographyPhoto.getImage(stack);
        Identifier texture = image.isEmpty() ? null : getTexture(image, TextureKind.HELD_FRAMED);
        if (texture == null) {
            return false;
        }
        boolean flipU = true;
        boolean flipV = true;
        if (DEBUG_PHOTO_RENDER) {
            net.blouflin.photography.Photography.LOGGER.info(
                    "[photo-frame-render] rotation={} poseRotation={} uvFlip={} upright={} entity=item_frame glow={} has320={} texture={} cacheHit={}",
                    rotation, rotation * 360.0f / 8.0f, flipV, true, glowFrame, true, texture,
                    TEXTURES.containsKey(Key.of(image, TextureKind.HELD_FRAMED)));
            net.blouflin.photography.Photography.LOGGER.info(
                    "[photo-frame-uv] rotation={} u0={} u1={} v0={} v1={} mirroredX=false mirroredY=false",
                    rotation, flipU ? 1.0f : 0.0f, flipU ? 0.0f : 1.0f, flipV ? 0.0f : 1.0f, flipV ? 1.0f : 0.0f);
        }
        submitQuad(collector, poseStack, texture, -0.5f, -0.5f, 0.5f, 0.5f, -0.002f, packedLight, flipU, flipV);
        return true;
    }

    private static NativeImage createPhotoImage(PhotographyPhoto.ImageData image) {
        NativeImage nativeImage = new NativeImage(image.width(), image.height(), false);
        copyPhotoPixels(nativeImage, image, 0, 0, image.width(), image.height());
        return nativeImage;
    }

    private static NativeImage createHeldFramedImage(PhotographyPhoto.ImageData image) {
        return createFramedImage(image, HELD_SIZE, HELD_IMAGE_OFFSET, HELD_IMAGE_SIZE);
    }

    private static NativeImage createFramedImage(PhotographyPhoto.ImageData image, int size, int imageOffset, int imageSize) {
        NativeImage nativeImage = new NativeImage(size, size, false);
        NativeImage paper = getPaperImage();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int paperX = x * paper.getWidth() / size;
                int paperY = y * paper.getHeight() / size;
                nativeImage.setPixel(x, y, paper.getPixel(paperX, paperY));
            }
        }
        copyPhotoPixels(nativeImage, image, imageOffset, imageOffset, imageSize, imageSize);
        return nativeImage;
    }

    private static void copyPhotoPixels(NativeImage target, PhotographyPhoto.ImageData image,
                                        int targetX, int targetY, int width, int height) {
        int[] pixels = image.pixels();
        for (int y = 0; y < height; y++) {
            int sourceY = y * image.height() / height;
            for (int x = 0; x < width; x++) {
                int sourceX = x * image.width() / width;
                target.setPixel(targetX + x, targetY + y, pixels[sourceX + sourceY * image.width()]);
            }
        }
    }

    private static NativeImage getPaperImage() {
        if (paperImage != null) {
            return paperImage;
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(PAPER_TEXTURE);
        if (resource.isPresent()) {
            try (InputStream stream = resource.get().open()) {
                paperImage = NativeImage.read(stream);
                return paperImage;
            } catch (IOException e) {
                net.blouflin.photography.Photography.LOGGER.warn("[photo-render] failed to load paper texture {}", PAPER_TEXTURE, e);
            }
        }
        paperImage = new NativeImage(64, 64, false);
        paperImage.fillRect(0, 0, 64, 64, 0xfff8f8f2);
        return paperImage;
    }

    private static void submitQuad(SubmitNodeCollector collector, PoseStack poseStack, Identifier texture,
                                   float minX, float minY, float maxX, float maxY, float z, int packedLight) {
        submitQuad(collector, poseStack, texture, minX, minY, maxX, maxY, z, packedLight, false);
    }

    private static void submitQuad(SubmitNodeCollector collector, PoseStack poseStack, Identifier texture,
                                   float minX, float minY, float maxX, float maxY, float z, int packedLight,
                                   boolean flipV) {
        submitQuad(collector, poseStack, texture, minX, minY, maxX, maxY, z, packedLight, false, flipV);
    }

    private static void submitQuad(SubmitNodeCollector collector, PoseStack poseStack, Identifier texture,
                                   float minX, float minY, float maxX, float maxY, float z, int packedLight,
                                   boolean flipU, boolean flipV) {
        collector.submitCustomGeometry(poseStack, RenderTypes.text(texture), (pose, vertexConsumer) ->
                drawQuad(pose, vertexConsumer, minX, minY, maxX, maxY, z, packedLight, flipU, flipV));
    }

    private static void drawQuad(PoseStack.Pose pose, VertexConsumer vertexConsumer,
                                 float minX, float minY, float maxX, float maxY, float z, int packedLight,
                                 boolean flipU, boolean flipV) {
        float leftU = flipU ? 1.0f : 0.0f;
        float rightU = flipU ? 0.0f : 1.0f;
        float topV = flipV ? 0.0f : 1.0f;
        float bottomV = flipV ? 1.0f : 0.0f;
        vertexConsumer.addVertex(pose, minX, maxY, z).setColor(0xffffffff).setUv(leftU, topV).setLight(packedLight);
        vertexConsumer.addVertex(pose, maxX, maxY, z).setColor(0xffffffff).setUv(rightU, topV).setLight(packedLight);
        vertexConsumer.addVertex(pose, maxX, minY, z).setColor(0xffffffff).setUv(rightU, bottomV).setLight(packedLight);
        vertexConsumer.addVertex(pose, minX, minY, z).setColor(0xffffffff).setUv(leftU, bottomV).setLight(packedLight);
    }

    private static void trimCache() {
        while (TEXTURES.size() > MAX_TEXTURES) {
            Map.Entry<Key, CachedTexture> eldest = TEXTURES.entrySet().iterator().next();
            Minecraft.getInstance().getTextureManager().release(eldest.getValue().id);
            eldest.getValue().texture.close();
            TEXTURES.remove(eldest.getKey());
            evictions++;
            logCacheStats(false);
        }
    }

    private static void logCacheStats(boolean hit) {
        if (DEBUG_PHOTO_RENDER) {
            net.blouflin.photography.Photography.LOGGER.info("[photo-preview-cache] hit={} miss={} entries={} evicted={}",
                    hit, cacheMisses, TEXTURES.size(), evictions);
        }
    }

    private static void logHeldRender(ItemStack stack, PhotographyPhoto.ImageData image, Identifier texture,
                                      String context, String hand) {
        if (!DEBUG_PHOTO_RENDER) {
            return;
        }
        net.blouflin.photography.Photography.LOGGER.info(
                "[held-photo-render] player=unknown local=unknown hand={} hasPhotographyImage={} hash={} texture={} context={}",
                hand,
                !image.isEmpty(),
                image.hash(),
                texture,
                context);
        net.blouflin.photography.Photography.LOGGER.info(
                "[photo-render] context={} hand={} hasPhotographyImage={} imageBytes={} imageSize={}x{} textureId={} cacheHit={} uploaded={}",
                context,
                hand,
                !image.isEmpty(),
                image.pixels().length * Integer.BYTES,
                image.width(),
                image.height(),
                texture,
                texture != null && TEXTURES.containsKey(Key.of(image, TextureKind.HELD_FRAMED)),
                texture != null);
    }

    private record CachedTexture(Identifier id, DynamicTexture texture, boolean uploaded) {
    }

    private record Key(TextureKind kind, int width, int height, int hash) {
        private static Key of(PhotographyPhoto.ImageData image, TextureKind kind) {
            return new Key(kind, image.width(), image.height(), image.hash());
        }
    }

    private enum TextureKind {
        PHOTO("photo"),
        PREVIEW("preview"),
        HELD_FRAMED("held");

        private final String id;

        TextureKind(String id) {
            this.id = id;
        }
    }
}
