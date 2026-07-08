package net.blouflin.photography.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.Window;
import net.blouflin.photography.client.PhotographyHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void photography$onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (PhotographyHud.isUsingPhotographyCamera
                && action == GLFW.GLFW_PRESS
                && buttonInfo.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && client.player != null) {
            double[] cursorX = new double[1];
            double[] cursorY = new double[1];
            GLFW.glfwGetCursorPos(window, cursorX, cursorY);
            Window minecraftWindow = client.getWindow();
            double guiX = cursorX[0] * minecraftWindow.getGuiScaledWidth() / minecraftWindow.getWidth();
            double guiY = cursorY[0] * minecraftWindow.getGuiScaledHeight() / minecraftWindow.getHeight();
            if (PhotographyHud.handleControlsClick(guiX, guiY)) {
                ci.cancel();
                return;
            }
        }

        if (PhotographyHud.isUsingPhotographyCamera
                && action == GLFW.GLFW_PRESS
                && buttonInfo.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && client.player != null
                && client.player.isShiftKeyDown()) {
            PhotographyHud.debugViewfinder("shift+right-click detected by mouse fallback");
            PhotographyHud.toggleCameraControls();
            ci.cancel();
        }
    }

    // From Uku3lig's nowheel under MIT license: https://github.com/uku3lig/nowheel/blob/1.21.2/src/main/java/net/uku3lig/nowheel/mixin/MouseMixin.java
    @WrapWithCondition(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    public boolean onHotbarScroll(Inventory instance, int slot) {
        return !PhotographyHud.isUsingPhotographyCamera;
    }

    @Inject(at = @At("RETURN"), method = "onScroll(JDD)V", cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {

        Minecraft client = Minecraft.getInstance();
        vertical = -vertical;

        if(PhotographyHud.isUsingPhotographyCamera) {
            ci.cancel();
            if (vertical > 0) {
                PhotographyHud.zoomAmount *= 1.1;
            } else if (vertical < 0) {
                PhotographyHud.zoomAmount *= 0.9;
            }
            PhotographyHud.zoomAmount = Mth.clamp(PhotographyHud.zoomAmount, 0.2, 2);

            client.options.sensitivity().set(PhotographyHud.defaultMouseSensitivity * (PhotographyHud.zoomAmount / 2));
        }
    }
}
