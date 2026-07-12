package net.blouflin.photography.mixin;

import net.blouflin.photography.PhotographyClient;
import net.blouflin.photography.client.PhotographyHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void injected(long window, int action, KeyEvent input, CallbackInfo ci) {
        if (PhotographyHud.isUsingPhotographyCamera) {
            if (PhotographyClient.SHOOT_KEY != null && PhotographyClient.SHOOT_KEY.matches(input) && action == GLFW.GLFW_PRESS) {
                PhotographyHud.requestCapture("shoot-hotkey");
                ci.cancel();
            } else if (input.key() == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                PhotographyHud.stopRenderPhotographyCameraOverlay();
                ci.cancel();
            } else if (Minecraft.getInstance().options.keyTogglePerspective.matches(input) && action == GLFW.GLFW_PRESS) {
                PhotographyHud.toggleSelfie();
                ci.cancel();
            } else if (input.key() == GLFW.GLFW_KEY_F1) {
                ci.cancel();
            }
        }
    }
}
