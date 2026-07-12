package net.blouflin.photography.client;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record PhotographyPhotoTooltip(ItemStack stack) implements TooltipComponent {
    public PhotographyPhotoTooltip {
        stack = stack.copyWithCount(1);
    }
}
