package net.blouflin.photography.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.blouflin.photography.client.PhotographyHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.MouseHandler;
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
        if (PhotographyHud.isUsingPhotographyCamera && client.player != null
                && PhotographyHud.handleCameraMouseButton(buttonInfo.button(), action)) {
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

        vertical = -vertical;

        if(PhotographyHud.isUsingPhotographyCamera) {
            ci.cancel();
            PhotographyHud.adjustZoomFromScroll(vertical);
        }
    }
}
