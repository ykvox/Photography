package net.blouflin.photography.networking;

import net.blouflin.photography.PhotographyArchive;
import net.blouflin.photography.PhotographyUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.List;
import java.util.Objects;

public record SpawnPicturePayload(Integer id, CompoundTag nbtCompound) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SpawnPicturePayload> ID = CustomPacketPayload.createType("photography_spawn_picture");
    public static final StreamCodec<FriendlyByteBuf, SpawnPicturePayload> CODEC = StreamCodec.ofMember((value, buf) -> buf.writeInt(value.id).writeNbt(value.nbtCompound), buf -> new SpawnPicturePayload(buf.readInt(),buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(ServerPlayer player, Integer id, CompoundTag nbtCompound) {

        MapId mapId = new MapId(id);
        MapItemSavedData mapState = PhotographyUtil.fromNbt(nbtCompound);

        player.level().getServer().execute(() -> {

            ItemStack stack = new ItemStack(Items.FILLED_MAP);
            player.level().setMapData(mapId, mapState);
            stack.set(DataComponents.MAP_ID, mapId);
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, comp -> comp.update(currentNbt -> {
                currentNbt.putBoolean("isPhotographyFilledMap",true);
            }));
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(56776F), List.of(), List.of(), List.of()));
            stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("photography:filled_map", "Photograph"));

            boolean photoGranted = false;
            if(!player.isCreative()) {
                // legacy item format support; TODO: remove later
                ItemStack itemStack = new ItemStack(Items.FILLED_MAP);
                itemStack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, comp -> comp.update(currentNbt -> {
                    currentNbt.putBoolean("isPhotographyEmptyMap",true);
                }));
                itemStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(56775F), List.of(), List.of(), List.of()));
                itemStack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("photography:empty_map", "Photographic Paper"));


                ItemStack itemStackNew = new ItemStack(Items.PAPER);
                itemStackNew.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, comp -> comp.update(currentNbt -> {
                    currentNbt.putBoolean("isPhotographyEmptyMap",true);
                }));
                itemStackNew.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(56775F), List.of(), List.of(), List.of()));
                itemStackNew.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("photography:empty_map", "Photographic Paper"));

                int itemCheck1 = player.getInventory().findSlotMatchingItem(itemStack);
                int itemCheck2 = player.getInventory().findSlotMatchingItem(itemStackNew);
                int slot = -1;
                if (itemCheck1 != -1) {
                    slot = itemCheck1;
                } else if (itemCheck2 != -1) {
                    slot = itemCheck2;
                }
                if(slot != -1) {
                    photoGranted = convertStack(player, slot, stack);
                } else if (player.getItemInHand(InteractionHand.OFF_HAND).getItem() == itemStack.getItem()) { // required to decrement offhand
                    if (Objects.equals(player.getItemInHand(InteractionHand.OFF_HAND).getComponents().get(DataComponents.CUSTOM_DATA), itemStack.getComponents().get(DataComponents.CUSTOM_DATA))) {
                        photoGranted = convertStack(player, 40, stack);
                    }
                }
            }
            else {
                photoGranted = addOrDropStack(player, stack);
            }

            if (photoGranted) {
                PhotographyArchive.saveAsync(player, mapState);
            }
        });
    }

    private static boolean convertStack(ServerPlayer player, int slot, ItemStack stack) {
        player.getInventory().getItem(slot).shrink(1);
        return addOrDropStack(player, stack);
    }

    private static boolean addOrDropStack(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            ItemEntity itemEntity = new ItemEntity(player.level(), player.position().x, player.position().y, player.position().z, stack);
            player.level().addFreshEntity(itemEntity);
        }
        return true;
    }
}
