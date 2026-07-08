package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyHud;
import net.blouflin.photography.PhotographyCamera;
import net.blouflin.photography.networking.CreateMapStatePayload;
import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(SpyglassItem.class)
public abstract class SpyglassItemMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void injected(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(new InteractionResult.Pass());

        if (world.isClientSide()) {

            Minecraft client = Minecraft.getInstance();
            boolean isPhotographyCamera = PhotographyCamera.isPhotographyCamera(user.getItemInHand(hand));

            if (isPhotographyCamera) {
                PhotographyHud.debugViewfinder("camera item use ({})", hand);
                if (client.options.getCameraType().isFirstPerson()) {
                    if (PhotographyHud.isUsingPhotographyCamera) {
                        if (Objects.equals(PhotographyHud.handUsingPhotographyCamera, hand.toString())) {
                            if (PhotographyHud.canTakePhoto) {
                                PhotographyHud.debugViewfinder("shutter/capture requested");
                                PhotographyHud.canTakePhoto = false;
                                PhotographyHud.isTakingPhoto = true;
                                CreateMapStatePayload payload = new CreateMapStatePayload();
                                ClientPlayNetworking.send(payload);
                            }
                        }
                    } else {
                        PhotographyHud.openViewfinder(hand);
                    }
                }
            } else {
                user.playSound(SoundEvents.SPYGLASS_USE, 1.0f, 1.0f);
                user.awardStat(Stats.ITEM_USED.get(Items.SPYGLASS));
                cir.setReturnValue(ItemUtils.startUsingInstantly(world, user, hand));
            }
        } else {
            boolean isPhotographyCamera = PhotographyCamera.isPhotographyCamera(user.getItemInHand(hand));
            if (!isPhotographyCamera) {
                if (!((PlayerIsUsingCamera) user).isUsingPhotographyCamera()) {
                    user.playSound(SoundEvents.SPYGLASS_USE, 1.0f, 1.0f);
                    user.awardStat(Stats.ITEM_USED.get(Items.SPYGLASS));
                    cir.setReturnValue(ItemUtils.startUsingInstantly(world, user, hand));
                }
            }
        }
    }
}
