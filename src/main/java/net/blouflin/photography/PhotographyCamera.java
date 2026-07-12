package net.blouflin.photography;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

public final class PhotographyCamera {
    private static final Identifier CLOSED_MODEL = Identifier.fromNamespaceAndPath("photography", "camera");
    private static final Identifier OPEN_MODEL = Identifier.fromNamespaceAndPath("photography", "camera_active");
    private static final Identifier SELFIE_MODEL = Identifier.fromNamespaceAndPath("photography", "camera_selfie");

    private PhotographyCamera() {
    }

    public static boolean isPhotographyCamera(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.getComponents().has(DataComponents.CUSTOM_DATA)) {
            return false;
        }

        boolean isCamera = stack.getComponents().get(DataComponents.CUSTOM_DATA).toString().contains("isPhotographyCamera:1b");
        if (isCamera) {
            ensureCameraModel(stack);
        }
        return isCamera;
    }

    public static void ensureCameraModel(ItemStack stack) {
        if (!stack.getComponents().has(DataComponents.CUSTOM_MODEL_DATA)) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(56774F), List.of(), List.of(), List.of()));
        }
        if (!stack.getComponents().has(DataComponents.ITEM_MODEL)) {
            stack.set(DataComponents.ITEM_MODEL, CLOSED_MODEL);
        }
    }

    public static void setCameraModelState(ItemStack stack, boolean usingCamera, boolean selfie) {
        if (!isPhotographyCamera(stack)) {
            return;
        }

        ensureCameraModel(stack);
    }

    public static Identifier modelForState(boolean usingCamera, boolean selfie, boolean localSelfieView) {
        if (!usingCamera) {
            return CLOSED_MODEL;
        }
        if (selfie) {
            return localSelfieView
                    ? Identifier.fromNamespaceAndPath("photography", "camera_selfie_local")
                    : SELFIE_MODEL;
        }
        return OPEN_MODEL;
    }
}
