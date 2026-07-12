package net.blouflin.photography;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class PhotographyPhotoMigration {
    private PhotographyPhotoMigration() {
    }

    public static void compactPlayerInventory(Player player, String reason) {
        int compactedPhotos = 0;
        int compactedAlbums = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (PhotographyPhoto.isPhotographyPhoto(stack)) {
                int before = customDataSize(stack);
                PhotographyPhoto.compactImageInPlace(stack);
                if (customDataSize(stack) < before) {
                    compactedPhotos++;
                }
            } else if (PhotographyAlbum.isAlbum(stack)) {
                int before = customDataSize(stack);
                PhotographyAlbum.setPages(stack, PhotographyAlbum.getPages(stack));
                if (customDataSize(stack) < before) {
                    compactedAlbums++;
                }
            }
        }
        if (compactedPhotos > 0 || compactedAlbums > 0) {
            player.getInventory().setChanged();
            Photography.LOGGER.info("[photo-sync] reason={} compactedPhotos={} compactedAlbums={}",
                    reason, compactedPhotos, compactedAlbums);
        }
    }

    private static int customDataSize(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return 0;
        }
        return customData.copyTag().sizeInBytes();
    }
}
