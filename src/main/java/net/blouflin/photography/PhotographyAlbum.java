package net.blouflin.photography;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PhotographyAlbum {
    public static final int MAX_PAGES = 16;
    public static final String ALBUM_TAG = "photographyAlbum";
    private static final String PAGES_TAG = "pages";
    private static final String PHOTOGRAPH_TAG = "photograph";
    private static final String PHOTOGRAPH_IMAGE_TAG = "photographImage";
    private static final String NOTE_TAG = "note";
    private static final String TITLE_TAG = "title";

    private PhotographyAlbum() {
    }

    public static boolean isAlbum(ItemStack stack) {
        return stack.is(Photography.PHOTO_ALBUM);
    }

    public static List<Page> getPages(ItemStack albumStack) {
        if (!isAlbum(albumStack)) {
            return emptyPages();
        }
        CustomData customData = albumStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return emptyPages();
        }
        CompoundTag albumTag = customData.copyTag().getCompoundOrEmpty(ALBUM_TAG);
        ListTag pagesTag = albumTag.getListOrEmpty(PAGES_TAG);
        ArrayList<Page> pages = new ArrayList<>(MAX_PAGES);
        for (int index = 0; index < MAX_PAGES; index++) {
            CompoundTag pageTag = pagesTag.getCompoundOrEmpty(index);
            ItemStack photograph = pageTag.read(PHOTOGRAPH_TAG, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            photograph = restorePageImage(photograph, pageTag.getCompoundOrEmpty(PHOTOGRAPH_IMAGE_TAG));
            if (!PhotographyPhoto.isPhotographyPhoto(photograph)) {
                photograph = ItemStack.EMPTY;
            }
            String note = pageTag.getStringOr(NOTE_TAG, "");
            pages.add(new Page(photograph, note));
        }
        return pages;
    }

    public static Optional<Page> getPage(ItemStack albumStack, int index) {
        if (index < 0 || index >= MAX_PAGES) {
            return Optional.empty();
        }
        if (!isAlbum(albumStack)) {
            return Optional.of(Page.EMPTY);
        }
        CustomData customData = albumStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return Optional.of(Page.EMPTY);
        }
        CompoundTag albumTag = customData.copyTag().getCompoundOrEmpty(ALBUM_TAG);
        ListTag pagesTag = albumTag.getListOrEmpty(PAGES_TAG);
        return Optional.of(readPage(pagesTag.getCompoundOrEmpty(index)));
    }

    public static void setPage(ItemStack albumStack, int index, Page page) {
        if (!isAlbum(albumStack) || index < 0 || index >= MAX_PAGES) {
            return;
        }
        List<Page> pages = getPages(albumStack);
        pages.set(index, page);
        setPages(albumStack, pages);
    }

    public static void setPages(ItemStack albumStack, List<Page> pages) {
        if (!isAlbum(albumStack)) {
            return;
        }
        String title = getTitle(albumStack);
        albumStack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> customData.update(root -> {
            CompoundTag albumTag = new CompoundTag();
            if (!title.isBlank()) {
                albumTag.putString(TITLE_TAG, title);
            }
            ListTag pagesTag = new ListTag();
            for (int index = 0; index < MAX_PAGES; index++) {
                Page page = index < pages.size() ? pages.get(index) : Page.EMPTY;
                CompoundTag pageTag = new CompoundTag();
                if (PhotographyPhoto.isPhotographyPhoto(page.photograph())) {
                    ItemStack compactPhotograph = stripPageImage(page.photograph().copyWithCount(1));
                    pageTag.store(PHOTOGRAPH_TAG, ItemStack.OPTIONAL_CODEC, compactPhotograph);
                    CompoundTag pageImage = storePageImage(page.photograph());
                    if (!pageImage.isEmpty()) {
                        pageTag.put(PHOTOGRAPH_IMAGE_TAG, pageImage);
                    }
                }
                if (!page.note().isBlank()) {
                    pageTag.putString(NOTE_TAG, page.note());
                }
                pagesTag.add(pageTag);
            }
            albumTag.put(PAGES_TAG, pagesTag);
            root.put(ALBUM_TAG, albumTag);
        }));
    }

    public static String getTitle(ItemStack albumStack) {
        if (!isAlbum(albumStack)) {
            return "";
        }
        CustomData customData = albumStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return "";
        }
        CompoundTag albumTag = customData.copyTag().getCompoundOrEmpty(ALBUM_TAG);
        return albumTag.getStringOr(TITLE_TAG, "");
    }

    public static void setTitle(ItemStack albumStack, String title) {
        if (!isAlbum(albumStack)) {
            return;
        }
        String safe = title == null ? "" : title;
        if (safe.length() > 32) {
            safe = safe.substring(0, 32);
        }
        String finalSafe = safe;
        albumStack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> customData.update(root -> {
            CompoundTag albumTag = root.getCompoundOrEmpty(ALBUM_TAG).copy();
            if (finalSafe.isBlank()) {
                albumTag.remove(TITLE_TAG);
            } else {
                albumTag.putString(TITLE_TAG, finalSafe);
            }
            root.put(ALBUM_TAG, albumTag);
        }));
        if (finalSafe.isBlank()) {
            albumStack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("item.photography.album", "Photo Album"));
        } else {
            albumStack.set(DataComponents.ITEM_NAME, Component.literal(finalSafe));
        }
    }

    public static int getPhotographsCount(ItemStack albumStack) {
        if (!isAlbum(albumStack)) {
            return 0;
        }
        CustomData customData = albumStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return 0;
        }
        CompoundTag albumTag = customData.copyTag().getCompoundOrEmpty(ALBUM_TAG);
        ListTag pagesTag = albumTag.getListOrEmpty(PAGES_TAG);
        int count = 0;
        for (int index = 0; index < MAX_PAGES; index++) {
            CompoundTag pageTag = pagesTag.getCompoundOrEmpty(index);
            if (pageTag.contains(PHOTOGRAPH_TAG) || pageTag.contains(PHOTOGRAPH_IMAGE_TAG)) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasContent(ItemStack albumStack) {
        if (!isAlbum(albumStack)) {
            return false;
        }
        CustomData customData = albumStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return false;
        }
        CompoundTag albumTag = customData.copyTag().getCompoundOrEmpty(ALBUM_TAG);
        ListTag pagesTag = albumTag.getListOrEmpty(PAGES_TAG);
        for (int index = 0; index < MAX_PAGES; index++) {
            CompoundTag pageTag = pagesTag.getCompoundOrEmpty(index);
            if (pageTag.contains(PHOTOGRAPH_TAG) || pageTag.contains(PHOTOGRAPH_IMAGE_TAG)
                    || !pageTag.getStringOr(NOTE_TAG, "").isBlank()) {
                return true;
            }
        }
        return false;
    }

    public static List<Page> emptyPages() {
        return new ArrayList<>(Collections.nCopies(MAX_PAGES, Page.EMPTY));
    }

    public static int albumPayloadSizeBytes(ItemStack albumStack) {
        CustomData customData = albumStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return 0;
        }
        return customData.copyTag().sizeInBytes();
    }

    private static ItemStack stripPageImage(ItemStack photograph) {
        photograph.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> customData.update(tag -> tag.remove(PhotographyPhoto.IMAGE_TAG)));
        return photograph;
    }

    private static CompoundTag storePageImage(ItemStack photograph) {
        PhotographyPhoto.ImageData image = PhotographyPhoto.getImage(photograph);
        if (image.isEmpty()) {
            return new CompoundTag();
        }
        return PhotographyPhoto.createImageTag(image.width(), image.height(), image.pixels());
    }

    private static ItemStack restorePageImage(ItemStack photograph, CompoundTag pageImage) {
        if (photograph.isEmpty() || pageImage.isEmpty()) {
            return photograph;
        }
        PhotographyPhoto.ImageData image = PhotographyPhoto.imageFromTag(pageImage);
        if (image.isEmpty()) {
            return photograph;
        }
        photograph.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> customData.update(tag -> {
            tag.put(PhotographyPhoto.IMAGE_TAG, PhotographyPhoto.createImageTag(image.width(), image.height(), image.pixels()));
        }));
        return photograph;
    }

    private static Page readPage(CompoundTag pageTag) {
        ItemStack photograph = pageTag.read(PHOTOGRAPH_TAG, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        photograph = restorePageImage(photograph, pageTag.getCompoundOrEmpty(PHOTOGRAPH_IMAGE_TAG));
        if (!PhotographyPhoto.isPhotographyPhoto(photograph)) {
            photograph = ItemStack.EMPTY;
        }
        String note = pageTag.getStringOr(NOTE_TAG, "");
        return new Page(photograph, note);
    }

    public record Page(ItemStack photograph, String note) {
        public static final Page EMPTY = new Page(ItemStack.EMPTY, "");

        public boolean isEmpty() {
            return photograph().isEmpty() && note().isBlank();
        }

        public Page setPhotograph(ItemStack stack) {
            return new Page(PhotographyPhoto.isPhotographyPhoto(stack) ? stack.copyWithCount(1) : ItemStack.EMPTY, note);
        }

        public Page setNote(String value) {
            String safe = value == null ? "" : value;
            if (safe.length() > 512) {
                safe = safe.substring(0, 512);
            }
            return new Page(photograph, safe);
        }
    }
}
