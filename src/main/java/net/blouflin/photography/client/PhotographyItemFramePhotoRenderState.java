package net.blouflin.photography.client;

import net.minecraft.world.item.ItemStack;

public interface PhotographyItemFramePhotoRenderState {
    void photography$setFramedPhoto(ItemStack stack);

    ItemStack photography$getFramedPhoto();
}
