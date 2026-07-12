package net.blouflin.photography;

import net.blouflin.photography.networking.PhotographyAlbumOpenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class PhotographyAlbumItem extends Item {
    public PhotographyAlbumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        ensureAlbumDefaults(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new PhotographyAlbumOpenPayload(usedHand.name()));
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        int count = PhotographyAlbum.getPhotographsCount(stack);
        if (count > 0) {
            tooltip.accept(Component.translatable("item.photography.album.tooltip.photos_count", count)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static ItemStack createStack() {
        ItemStack stack = new ItemStack(Photography.PHOTO_ALBUM);
        ensureAlbumDefaults(stack);
        return stack;
    }

    public static void ensureAlbumDefaults(ItemStack stack) {
        if (!PhotographyAlbum.isAlbum(stack)) {
            return;
        }
        stack.set(DataComponents.ITEM_MODEL, Photography.ALBUM_ID);
        if (PhotographyAlbum.getTitle(stack).isBlank()) {
            stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("item.photography.album", "Photo Album"));
        }
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> customData.update(root -> {
            if (!root.contains(PhotographyAlbum.ALBUM_TAG)) {
                root.put(PhotographyAlbum.ALBUM_TAG, new net.minecraft.nbt.CompoundTag());
            }
        }));
    }

    public static void compactInventoryPhotos(Player player) {
        PhotographyPhotoMigration.compactPlayerInventory(player, "album_open");
    }
}
