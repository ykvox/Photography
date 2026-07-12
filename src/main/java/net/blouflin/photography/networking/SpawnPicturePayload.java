package net.blouflin.photography.networking;

import net.blouflin.photography.PhotographyArchive;
import net.blouflin.photography.PhotographyPaper;
import net.blouflin.photography.PhotographyPhoto;
import net.blouflin.photography.PhotographyUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import java.util.List;

public record SpawnPicturePayload(Integer id, CompoundTag nbtCompound, CompoundTag shotMetadata, CompoundTag photoImage) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SpawnPicturePayload> ID = CustomPacketPayload.createType("photography_spawn_picture");
    public static final StreamCodec<FriendlyByteBuf, SpawnPicturePayload> CODEC = StreamCodec.ofMember((value, buf) -> {
        buf.writeInt(value.id);
        buf.writeNbt(value.nbtCompound);
        buf.writeNbt(value.shotMetadata);
        buf.writeNbt(value.photoImage);
    }, buf -> new SpawnPicturePayload(buf.readInt(), buf.readNbt(), buf.readNbt(), buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(ServerPlayer player, Integer id, CompoundTag nbtCompound, CompoundTag shotMetadata, CompoundTag photoImage) {

        MapId mapId = new MapId(id);
        MapItemSavedData mapState = PhotographyUtil.fromNbt(nbtCompound);

        player.level().getServer().execute(() -> {

            ItemStack stack = new ItemStack(Items.FILLED_MAP);
            String takenTime = formatMinecraftTime(player.level().getOverworldClockTime());
            boolean anonymous = isAnonymousPhotographer(player);
            String visiblePhotographerName = anonymous ? "" : player.getScoreboardName();
            shotMetadata.putString("takenTime", takenTime);
            shotMetadata.putBoolean("anonymousPhotographer", anonymous);
            if (!visiblePhotographerName.isBlank()) {
                shotMetadata.putString("visiblePhotographerName", visiblePhotographerName);
            }
            net.blouflin.photography.Photography.LOGGER.debug("[photo-author] player={} anonymous={} reason={} loreName={}",
                    player.getScoreboardName(), anonymous, anonymityReason(player), visiblePhotographerName.isBlank() ? "omitted" : "shown");
            player.level().setMapData(mapId, mapState);
            stack.set(DataComponents.MAP_ID, mapId);
            PhotographyPhoto.ImageData image = PhotographyPhoto.imageFromTag(photoImage);
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, comp -> comp.update(currentNbt -> {
                currentNbt.putBoolean("isPhotographyFilledMap",true);
                currentNbt.put(PhotographyPhoto.SHOT_METADATA_TAG, shotMetadata.copy());
                if (!image.isEmpty()) {
                    currentNbt.put(PhotographyPhoto.IMAGE_TAG, PhotographyPhoto.createImageTag(image.width(), image.height(), image.pixels()));
                }
            }));
            if (!image.isEmpty()) {
                PhotographyPhoto.putImage(stack, image);
            }
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(56776F), List.of(), List.of(), List.of()));
            stack.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath("photography", "filled_photographic_paper"));
            stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("photography:filled_map", "Photograph"));
            stack.set(DataComponents.LORE, createShotLore(player, shotMetadata));
            stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.MAP_ID, true));

            boolean photoGranted = false;
            if(!player.isCreative()) {
                int slot = findPhotographicPaperSlot(player);
                if(slot != -1) {
                    photoGranted = convertStack(player, slot, stack);
                } else {
                    net.blouflin.photography.Photography.LOGGER.info("[camera-shot] blocked=no_photographic_paper mode=survival_adventure");
                }
            }
            else {
                photoGranted = addOrDropStack(player, stack);
            }

            if (photoGranted) {
                PhotographyPhoto.ImageData archiveImage = imageDataFromTag(photoImage);
                if (!archiveImage.isEmpty()) {
                    PhotographyArchive.saveArgbAsync(player, archiveImage.pixels(), archiveImage.width(), archiveImage.height());
                } else {
                    PhotographyArchive.saveAsync(player, mapState);
                }
            }
        });
    }

    private static boolean isValidPhotoImage(CompoundTag photoImage) {
        return !imageDataFromTag(photoImage).isEmpty();
    }

    private static PhotographyPhoto.ImageData imageDataFromTag(CompoundTag photoImage) {
        if (photoImage == null) {
            return PhotographyPhoto.ImageData.empty();
        }
        return PhotographyPhoto.imageFromTag(photoImage);
    }

    private static boolean convertStack(ServerPlayer player, int slot, ItemStack stack) {
        player.getInventory().getItem(slot).shrink(1);
        return addOrDropStack(player, stack);
    }

    private static int findPhotographicPaperSlot(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (PhotographyPaper.isPhotographicPaper(player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean addOrDropStack(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            ItemEntity itemEntity = new ItemEntity(player.level(), player.position().x, player.position().y, player.position().z, stack);
            player.level().addFreshEntity(itemEntity);
        }
        return true;
    }

    private static ItemLore createShotLore(ServerPlayer player, CompoundTag shotMetadata) {
        List<Component> lines = new java.util.ArrayList<>();
        int focalLength = shotMetadata.getIntOr("focalLengthMm", 35);
        String shutterSpeed = shotMetadata.getStringOr("shutterSpeed", "1/60");
        String flashMode = shotMetadata.getStringOr("flashMode", "off");
        lines.add(Component.literal("Lens " + focalLength + "mm").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Shutter " + shutterSpeed).withStyle(ChatFormatting.GRAY));
        boolean resolvedFlash = shotMetadata.getBooleanOr("resolvedFlash", false);
        if (resolvedFlash) {
            lines.add(Component.literal("Flash" + ("auto".equals(flashMode) ? ": Auto" : "")).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.literal("Taken " + shotMetadata.getStringOr("takenTime", formatMinecraftTime(player.level().getOverworldClockTime()))).withStyle(ChatFormatting.DARK_GRAY));
        if (!shotMetadata.getBooleanOr("anonymousPhotographer", false)) {
            String visibleName = shotMetadata.getStringOr("visiblePhotographerName", "");
            if (!visibleName.isBlank()) {
                lines.add(Component.literal("by " + visibleName).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return new ItemLore(lines);
    }

    private static boolean isAnonymousPhotographer(ServerPlayer player) {
        return player.isInvisible() || hidesNameTag(player);
    }

    private static boolean hidesNameTag(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team == null) {
            return false;
        }
        Team.Visibility visibility = team.getNameTagVisibility();
        return visibility == Team.Visibility.NEVER
                || visibility == Team.Visibility.HIDE_FOR_OTHER_TEAMS
                || visibility == Team.Visibility.HIDE_FOR_OWN_TEAM;
    }

    private static String anonymityReason(ServerPlayer player) {
        if (player.isInvisible()) {
            return "invisible";
        }
        PlayerTeam team = player.getTeam();
        if (team == null) {
            return "visible";
        }
        Team.Visibility visibility = team.getNameTagVisibility();
        if (visibility == Team.Visibility.NEVER) {
            return "team_nametag_never";
        }
        if (visibility == Team.Visibility.HIDE_FOR_OTHER_TEAMS || visibility == Team.Visibility.HIDE_FOR_OWN_TEAM) {
            return "team_nametag_hidden";
        }
        return "visible";
    }

    private static String formatMinecraftTime(long dayTime) {
        long dayTicks = Math.floorMod(dayTime, 24000L);
        long timeTicks = (dayTicks + 6000L) % 24000L;
        long hours = timeTicks / 1000L;
        long minutes = Math.round((timeTicks % 1000L) * 60.0d / 1000.0d);
        if (minutes >= 60L) {
            minutes = 0L;
            hours = (hours + 1L) % 24L;
        }
        return String.format("%02d:%02d", hours, minutes);
    }
}
