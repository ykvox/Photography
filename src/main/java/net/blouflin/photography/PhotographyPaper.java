package net.blouflin.photography;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

public final class PhotographyPaper {
    private PhotographyPaper() {
    }

    public static boolean isPhotographicPaper(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.is(Items.PAPER)
                && stack.getComponents().has(DataComponents.CUSTOM_DATA)
                && stack.getComponents().get(DataComponents.CUSTOM_DATA).toString().contains("isPhotographyEmptyMap:1b");
    }

    public static void ensurePaperModel(ItemStack stack) {
        if (!stack.getComponents().has(DataComponents.CUSTOM_MODEL_DATA)) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(56775F), List.of(), List.of(), List.of()));
        }
        if (!stack.getComponents().has(DataComponents.ITEM_MODEL)) {
            stack.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath("photography", "empty_photographic_paper"));
        }
    }

    public static void setPaperTag(ItemStack stack) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, comp -> comp.update(currentNbt -> {
            currentNbt.putBoolean("isPhotographyEmptyMap", true);
        }));
        ensurePaperModel(stack);
    }
}
