package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyItemFramePhotoRenderState;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemFrameRenderState.class)
public class ItemFrameRenderStateMixin implements PhotographyItemFramePhotoRenderState {
    @Unique
    private ItemStack photography$framedPhoto = ItemStack.EMPTY;

    @Override
    public void photography$setFramedPhoto(ItemStack stack) {
        this.photography$framedPhoto = stack;
    }

    @Override
    public ItemStack photography$getFramedPhoto() {
        return photography$framedPhoto;
    }
}
