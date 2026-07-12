package net.blouflin.photography.mixin;

import net.blouflin.photography.PhotographyPhoto;
import net.blouflin.photography.client.PhotographyPhotoTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Item.class)
public class ItemTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void photography$getTooltipImage(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (PhotographyPhoto.isPhotographyPhoto(stack)) {
            cir.setReturnValue(Optional.of(new PhotographyPhotoTooltip(stack)));
        }
    }
}
