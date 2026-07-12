package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyHud;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void photography$cancelAttackWhileUsingCamera(CallbackInfoReturnable<Boolean> cir) {
        if (PhotographyHud.shouldConsumeAttackInput()) {
            if (PhotographyHud.isDebugViewfinderEnabled()) {
                net.blouflin.photography.Photography.LOGGER.info(
                        "[camera-input] consumedAttack=true state=viewfinder target=unknown captureRequested=false");
            }
            cir.setReturnValue(false);
        }
    }
}
