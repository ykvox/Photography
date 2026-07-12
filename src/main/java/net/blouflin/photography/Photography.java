package net.blouflin.photography;

import net.blouflin.photography.networking.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Photography implements ModInitializer {
	public static final String MOD_ID = "Photography";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier CAMERA_SHUTTER_SOUND = Identifier.fromNamespaceAndPath("photography","camera_shutter");
	public static SoundEvent CAMERA_SHUTTER = SoundEvent.createVariableRangeEvent(CAMERA_SHUTTER_SOUND);
	public static final Identifier CAMERA_VIEWFINDER_OPEN_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.viewfinder_open");
	public static final SoundEvent CAMERA_VIEWFINDER_OPEN = SoundEvent.createVariableRangeEvent(CAMERA_VIEWFINDER_OPEN_SOUND);
	public static final Identifier CAMERA_VIEWFINDER_CLOSE_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.viewfinder_close");
	public static final SoundEvent CAMERA_VIEWFINDER_CLOSE = SoundEvent.createVariableRangeEvent(CAMERA_VIEWFINDER_CLOSE_SOUND);
	public static final Identifier CAMERA_SHUTTER_OPEN_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.shutter_open");
	public static final SoundEvent CAMERA_SHUTTER_OPEN = SoundEvent.createVariableRangeEvent(CAMERA_SHUTTER_OPEN_SOUND);
	public static final Identifier CAMERA_SHUTTER_CLOSE_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.shutter_close");
	public static final SoundEvent CAMERA_SHUTTER_CLOSE = SoundEvent.createVariableRangeEvent(CAMERA_SHUTTER_CLOSE_SOUND);
	public static final Identifier CAMERA_FILM_ADVANCE_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.film_advance");
	public static final SoundEvent CAMERA_FILM_ADVANCE = SoundEvent.createVariableRangeEvent(CAMERA_FILM_ADVANCE_SOUND);
	public static final Identifier CAMERA_FILM_ADVANCE_LAST_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.film_advance_last");
	public static final SoundEvent CAMERA_FILM_ADVANCE_LAST = SoundEvent.createVariableRangeEvent(CAMERA_FILM_ADVANCE_LAST_SOUND);
	public static final Identifier CAMERA_BUTTON_CLICK_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.button_click");
	public static final SoundEvent CAMERA_BUTTON_CLICK = SoundEvent.createVariableRangeEvent(CAMERA_BUTTON_CLICK_SOUND);
	public static final Identifier CAMERA_RELEASE_BUTTON_CLICK_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.release_button_click");
	public static final SoundEvent CAMERA_RELEASE_BUTTON_CLICK = SoundEvent.createVariableRangeEvent(CAMERA_RELEASE_BUTTON_CLICK_SOUND);
	public static final Identifier CAMERA_DIAL_CLICK_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.dial_click");
	public static final SoundEvent CAMERA_DIAL_CLICK = SoundEvent.createVariableRangeEvent(CAMERA_DIAL_CLICK_SOUND);
	public static final Identifier CAMERA_LENS_RING_CLICK_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.lens_ring_click");
	public static final SoundEvent CAMERA_LENS_RING_CLICK = SoundEvent.createVariableRangeEvent(CAMERA_LENS_RING_CLICK_SOUND);
	public static final Identifier CAMERA_TIMER_TICK_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.timer_tick");
	public static final SoundEvent CAMERA_TIMER_TICK = SoundEvent.createVariableRangeEvent(CAMERA_TIMER_TICK_SOUND);
	public static final Identifier CAMERA_FLASH_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.flash");
	public static final SoundEvent CAMERA_FLASH = SoundEvent.createVariableRangeEvent(CAMERA_FLASH_SOUND);
	public static final Identifier CAMERA_PRINT_SOUND = Identifier.fromNamespaceAndPath("photography","item.camera.print");
	public static final SoundEvent CAMERA_PRINT = SoundEvent.createVariableRangeEvent(CAMERA_PRINT_SOUND);
	public static final Identifier PHOTOGRAPH_RUSTLE_SOUND = Identifier.fromNamespaceAndPath("photography", "item.photograph.rustle");
	public static final SoundEvent PHOTOGRAPH_RUSTLE = SoundEvent.createVariableRangeEvent(PHOTOGRAPH_RUSTLE_SOUND);
	public static final Identifier PHOTOGRAPH_PLACE_SOUND = Identifier.fromNamespaceAndPath("photography", "item.photograph.place");
	public static final SoundEvent PHOTOGRAPH_PLACE = SoundEvent.createVariableRangeEvent(PHOTOGRAPH_PLACE_SOUND);
	public static final Identifier PHOTO_DUPLICATION_RECIPE_ID = Identifier.fromNamespaceAndPath("photography", "photograph_duplication");
	public static final RecipeSerializer<PhotographyPhotoDuplicationRecipe> PHOTO_DUPLICATION_RECIPE_SERIALIZER =
			new RecipeSerializer<>(PhotographyPhotoDuplicationRecipe.MAP_CODEC, PhotographyPhotoDuplicationRecipe.STREAM_CODEC);
	public static final Identifier PHOTO_COMPONENT_ID = Identifier.fromNamespaceAndPath("photography", "photo");
	public static final DataComponentType<CustomData> PHOTO_COMPONENT = DataComponentType.<CustomData>builder()
			.persistent(CustomData.CODEC)
			.networkSynchronized(CustomData.STREAM_CODEC)
			.cacheEncoding()
			.build();
	public static final Identifier ALBUM_ID = Identifier.fromNamespaceAndPath("photography", "album");
	public static final ResourceKey<Item> ALBUM_KEY = ResourceKey.create(Registries.ITEM, ALBUM_ID);
	public static final PhotographyAlbumItem PHOTO_ALBUM = new PhotographyAlbumItem(new Item.Properties()
			.stacksTo(1)
			.setId(ALBUM_KEY));
	public static final Identifier CAMERA_STAND_ID = Identifier.fromNamespaceAndPath("photography", "camera_stand");
	public static final ResourceKey<Item> CAMERA_STAND_KEY = ResourceKey.create(Registries.ITEM, CAMERA_STAND_ID);
	public static final ResourceKey<EntityType<?>> CAMERA_STAND_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, CAMERA_STAND_ID);
	public static final PhotographyCameraStandItem CAMERA_STAND_ITEM = new PhotographyCameraStandItem(new Item.Properties()
			.stacksTo(16)
			.setId(CAMERA_STAND_KEY));
	public static final EntityType<PhotographyCameraStandEntity> CAMERA_STAND_ENTITY = EntityType.Builder
			.of(PhotographyCameraStandEntity::new, MobCategory.MISC)
			.sized(0.7f, 1.6f)
			.eyeHeight(1.40625f)
			.clientTrackingRange(10)
			.updateInterval(3)
			.build(CAMERA_STAND_ENTITY_KEY);

	@Override
	public void onInitialize() {
		//LOGGER.info("Photography mod (by BlouFlin) loaded !");
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_SHUTTER_SOUND, CAMERA_SHUTTER);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_VIEWFINDER_OPEN_SOUND, CAMERA_VIEWFINDER_OPEN);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_VIEWFINDER_CLOSE_SOUND, CAMERA_VIEWFINDER_CLOSE);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_SHUTTER_OPEN_SOUND, CAMERA_SHUTTER_OPEN);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_SHUTTER_CLOSE_SOUND, CAMERA_SHUTTER_CLOSE);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_FILM_ADVANCE_SOUND, CAMERA_FILM_ADVANCE);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_FILM_ADVANCE_LAST_SOUND, CAMERA_FILM_ADVANCE_LAST);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_BUTTON_CLICK_SOUND, CAMERA_BUTTON_CLICK);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_RELEASE_BUTTON_CLICK_SOUND, CAMERA_RELEASE_BUTTON_CLICK);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_DIAL_CLICK_SOUND, CAMERA_DIAL_CLICK);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_LENS_RING_CLICK_SOUND, CAMERA_LENS_RING_CLICK);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_TIMER_TICK_SOUND, CAMERA_TIMER_TICK);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_FLASH_SOUND, CAMERA_FLASH);
		Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_PRINT_SOUND, CAMERA_PRINT);
		Registry.register(BuiltInRegistries.SOUND_EVENT, PHOTOGRAPH_RUSTLE_SOUND, PHOTOGRAPH_RUSTLE);
		Registry.register(BuiltInRegistries.SOUND_EVENT, PHOTOGRAPH_PLACE_SOUND, PHOTOGRAPH_PLACE);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, PHOTO_DUPLICATION_RECIPE_ID, PHOTO_DUPLICATION_RECIPE_SERIALIZER);
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, PHOTO_COMPONENT_ID, PHOTO_COMPONENT);
		Registry.register(BuiltInRegistries.ITEM, ALBUM_ID, PHOTO_ALBUM);
		Registry.register(BuiltInRegistries.ITEM, CAMERA_STAND_ID, CAMERA_STAND_ITEM);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, CAMERA_STAND_ID, CAMERA_STAND_ENTITY);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(this::addItemsToCreativeTab);

		PayloadTypeRegistry.clientboundPlay().register(CreatePicturePayload.ID, CreatePicturePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CameraFlashStatePayload.ID, CameraFlashStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GetUsingPhotographyCameraPayload.ID, GetUsingPhotographyCameraPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayCameraShutterSoundPayload.ID, PlayCameraShutterSoundPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PhotographyAlbumOpenPayload.ID, PhotographyAlbumOpenPayload.CODEC);

		PayloadTypeRegistry.serverboundPlay().register(CreateMapStatePayload.ID, CreateMapStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CreateMapStatePayload.ID, (payload, handler) -> CreateMapStatePayload.receive(handler.player(), payload.resolvedFlash(), payload.shutterTicks(), payload.lastFrameAdvance()));

		PayloadTypeRegistry.serverboundPlay().register(CameraPhysicalSoundPayload.ID, CameraPhysicalSoundPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CameraPhysicalSoundPayload.ID, (payload, handler) -> CameraPhysicalSoundPayload.receive(handler.player(), payload.action()));

		PayloadTypeRegistry.serverboundPlay().register(SpawnPicturePayload.ID, SpawnPicturePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpawnPicturePayload.ID, (payload, handler) -> SpawnPicturePayload.receive(handler.player(), payload.id(), payload.nbtCompound(), payload.shotMetadata(), payload.photoImage()));

		PayloadTypeRegistry.serverboundPlay().register(SetUsingPhotographyCameraPayload.ID, SetUsingPhotographyCameraPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetUsingPhotographyCameraPayload.ID, (payload, handler) -> SetUsingPhotographyCameraPayload.receive(handler.player(), payload.isUsingPhotographyCamera(), payload.handUsingPhotographyCamera(), payload.selfie()));

		PayloadTypeRegistry.serverboundPlay().register(PhotographyAlbumActionPayload.ID, PhotographyAlbumActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PhotographyAlbumActionPayload.ID, (payload, handler) -> PhotographyAlbumActionPayload.receive(handler.player(), payload.hand(), payload.page(), payload.action()));

		ServerTickEvents.END_SERVER_TICK.register(PhotographyPhysicalSounds::tick);
	}

	private void addItemsToCreativeTab(FabricCreativeModeTabOutput entries) {
		ItemStack photographyCamera = new ItemStack(Items.SPYGLASS);
		photographyCamera.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, comp -> comp.update(currentNbt -> {
			currentNbt.putBoolean("isPhotographyCamera",true);
		}));
        photographyCamera.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(56774F), List.of(), List.of(), List.of()));
		photographyCamera.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("photography", "camera"));
		photographyCamera.set(DataComponents.ITEM_NAME, Component.literal("Camera"));
		entries.insertAfter(Items.MAP, photographyCamera);

		ItemStack photographicPaper = new ItemStack(Items.PAPER);
		PhotographyPaper.setPaperTag(photographicPaper);
		photographicPaper.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("photography:empty_map", "Photographic Paper"));
		entries.insertBefore(Items.WRITABLE_BOOK, photographicPaper);

		entries.insertAfter(Items.WRITABLE_BOOK, PhotographyAlbumItem.createStack());
		entries.insertAfter(PHOTO_ALBUM, new ItemStack(CAMERA_STAND_ITEM));
	}

//    private void countMaps(MapIdComponent id) throws IOException {
//        // create a list of maps made using the mod with an associated timestamp
//		try {
//			Files.createFile(FabricLoader.getInstance().getConfigDir());
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//		String mapCount = id.asString() + ;
//
//		Files.writeString(, id);
//	}
}
