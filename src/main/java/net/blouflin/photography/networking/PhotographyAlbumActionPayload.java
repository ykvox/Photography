package net.blouflin.photography.networking;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyAlbum;
import net.blouflin.photography.PhotographyAlbumItem;
import net.blouflin.photography.PhotographyPhoto;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public record PhotographyAlbumActionPayload(String hand, int page, String action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PhotographyAlbumActionPayload> ID =
            CustomPacketPayload.createType("photography_album_action");
    public static final StreamCodec<FriendlyByteBuf, PhotographyAlbumActionPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeUtf(value.hand);
                buf.writeInt(value.page);
                buf.writeUtf(value.action);
            },
            buf -> new PhotographyAlbumActionPayload(buf.readUtf(), buf.readInt(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(ServerPlayer player, String handName, int page, String action) {
        player.level().getServer().execute(() -> {
            InteractionHand hand = parseHand(handName);
            ItemStack albumStack = player.getItemInHand(hand);
            if (!PhotographyAlbum.isAlbum(albumStack)) {
                return;
            }
            PhotographyAlbumItem.ensureAlbumDefaults(albumStack);
            logAlbumSync("before_" + actionName(action), albumStack, page);
            if ("insert".equals(action)) {
                insertFirstInventoryPhoto(player, albumStack, page);
            } else if (action.startsWith("insertSlot:")) {
                insertInventoryPhoto(player, albumStack, page, action.substring("insertSlot:".length()));
            } else if ("remove".equals(action)) {
                removePhoto(player, albumStack, page);
            } else if (action.startsWith("note:")) {
                setNote(albumStack, page, action.substring("note:".length()));
            } else if (action.startsWith("title:")) {
                setTitle(albumStack, action.substring("title:".length()));
            }
            player.getInventory().setChanged();
            logAlbumSync("after_" + actionName(action), albumStack, page);
        });
    }

    private static void insertFirstInventoryPhoto(ServerPlayer player, ItemStack albumStack, int page) {
        Photography.LOGGER.debug("[album] ignored legacy first-photo insertion page={}", page);
    }

    private static void insertInventoryPhoto(ServerPlayer player, ItemStack albumStack, int page, String slotText) {
        if (PhotographyAlbum.getPage(albumStack, page).map(existing -> !existing.photograph().isEmpty()).orElse(true)) {
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(slotText);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return;
        }
        ItemStack candidate = player.getInventory().getItem(slot);
        if (!PhotographyPhoto.isPhotographyPhoto(candidate)) {
            return;
        }
        PhotographyPhoto.compactImageInPlace(candidate);
        PhotographyAlbum.Page existing = PhotographyAlbum.getPage(albumStack, page).orElse(PhotographyAlbum.Page.EMPTY);
        String note = existing.note().isBlank() ? defaultCaption(candidate) : existing.note();
        PhotographyAlbum.Page pageData = existing.setPhotograph(candidate).setNote(note);
        PhotographyAlbum.setPage(albumStack, page, pageData);
        candidate.shrink(1);
        Photography.LOGGER.debug("[album] inserted photograph page={} slot={}", page, slot);
    }

    private static String defaultCaption(ItemStack photograph) {
        net.minecraft.world.item.component.CustomData customData = photograph.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return "";
        }
        net.minecraft.nbt.CompoundTag metadata = customData.copyTag().getCompoundOrEmpty(PhotographyPhoto.SHOT_METADATA_TAG);
        String taken = metadata.getStringOr("takenTime", "");
        if (taken.isBlank()) {
            return "";
        }
        String author = metadata.getStringOr("visiblePhotographerName", "");
        if (metadata.getBooleanOr("anonymousPhotographer", false) || author.isBlank()) {
            return "Taken " + taken;
        }
        return "Taken " + taken + "\nby " + author;
    }

    private static void removePhoto(ServerPlayer player, ItemStack albumStack, int page) {
        PhotographyAlbum.getPage(albumStack, page).ifPresent(existing -> {
            if (existing.photograph().isEmpty()) {
                return;
            }
            ItemStack photograph = existing.photograph().copyWithCount(1);
            PhotographyAlbum.setPage(albumStack, page, new PhotographyAlbum.Page(ItemStack.EMPTY, existing.note()));
            if (!player.getInventory().add(photograph)) {
                player.drop(photograph, false);
            }
            Photography.LOGGER.debug("[album] removed photograph page={}", page);
        });
    }

    private static void setNote(ItemStack albumStack, int page, String note) {
        PhotographyAlbum.getPage(albumStack, page).ifPresent(existing -> {
            PhotographyAlbum.setPage(albumStack, page, existing.setNote(note));
            Photography.LOGGER.debug("[album-caption] page={} slot=0 length={} saved=true", page, note.length());
        });
    }

    private static void setTitle(ItemStack albumStack, String title) {
        PhotographyAlbum.setTitle(albumStack, title);
        Photography.LOGGER.debug("[album-title] length={} saved=true", title.length());
    }

    private static void logAlbumSync(String action, ItemStack albumStack, int page) {
        if (!Photography.LOGGER.isDebugEnabled()) {
            return;
        }
        int nonEmptyPages = 0;
        java.util.List<PhotographyAlbum.Page> pages = PhotographyAlbum.getPages(albumStack);
        for (PhotographyAlbum.Page albumPage : pages) {
            if (!albumPage.isEmpty()) {
                nonEmptyPages++;
            }
        }
        Photography.LOGGER.debug("[album-sync] action={} slot={} stackCount={} componentSize={} pages={}",
                action, page, albumStack.getCount(), PhotographyAlbum.albumPayloadSizeBytes(albumStack), nonEmptyPages);
        for (int index = 0; index < pages.size(); index++) {
            PhotographyAlbum.Page albumPage = pages.get(index);
            PhotographyPhoto.ImageData image = PhotographyPhoto.getImage(albumPage.photograph());
            Photography.LOGGER.debug("[album-sync] page={} empty={} photoHash={} image={}x{} pixels={} captionLength={}",
                    index, albumPage.isEmpty(), image.hash(), image.width(), image.height(),
                    image.pixels().length, albumPage.note().length());
        }
    }

    private static String actionName(String action) {
        if (action.startsWith("insertSlot:")) {
            return "insert";
        }
        if (action.startsWith("note:")) {
            return "caption_commit";
        }
        if (action.startsWith("title:")) {
            return "title_commit";
        }
        return action;
    }

    private static InteractionHand parseHand(String handName) {
        try {
            return InteractionHand.valueOf(handName);
        } catch (IllegalArgumentException ignored) {
            return InteractionHand.MAIN_HAND;
        }
    }
}
