package net.blouflin.photography;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class PhotographyCamera {
    private PhotographyCamera() {
    }

    public static boolean isPhotographyCamera(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.getComponents().has(DataComponents.CUSTOM_DATA)) {
            return false;
        }

        return stack.getComponents().get(DataComponents.CUSTOM_DATA).toString().contains("isPhotographyCamera:1b");
    }
}
