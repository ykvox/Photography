package net.blouflin.photography.mixin;

import net.blouflin.photography.PhotographyPhotoMigration;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void photography$compactPhotoStacksBeforeInitialSync(Connection connection, ServerPlayer player,
                                                                 CommonListenerCookie cookie, CallbackInfo ci) {
        PhotographyPhotoMigration.compactPlayerInventory(player, "login");
    }
}
