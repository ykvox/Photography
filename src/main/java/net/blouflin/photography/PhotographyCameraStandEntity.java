package net.blouflin.photography;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class PhotographyCameraStandEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_CAMERA =
            SynchedEntityData.defineId(PhotographyCameraStandEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_HURT =
            SynchedEntityData.defineId(PhotographyCameraStandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HURT_DIR =
            SynchedEntityData.defineId(PhotographyCameraStandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(PhotographyCameraStandEntity.class, EntityDataSerializers.FLOAT);

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private UUID ownerPlayerId = NIL_UUID;

    public PhotographyCameraStandEntity(EntityType<? extends PhotographyCameraStandEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CAMERA, ItemStack.EMPTY);
        builder.define(DATA_HURT, 0);
        builder.define(DATA_HURT_DIR, 1);
        builder.define(DATA_DAMAGE, 0.0f);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (!getCamera().isEmpty()) {
            output.store("Camera", ItemStack.OPTIONAL_CODEC, getCamera());
        }
        if (!ownerPlayerId.equals(NIL_UUID)) {
            output.putString("Owner", ownerPlayerId.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setCamera(input.read("Camera", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        ownerPlayerId = input.getString("Owner").map(value -> {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                return NIL_UUID;
            }
        }).orElse(NIL_UUID);
    }

    public ItemStack getCamera() {
        return getEntityData().get(DATA_CAMERA);
    }

    public void setCamera(ItemStack stack) {
        getEntityData().set(DATA_CAMERA, stack);
    }

    public void setOwnerPlayer(Player player) {
        ownerPlayerId = player.getUUID();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack handStack = player.getItemInHand(hand);
        ItemStack camera = getCamera();
        if (camera.isEmpty() && PhotographyCamera.isPhotographyCamera(handStack)) {
            setCamera(handStack.copyWithCount(1));
            player.setItemInHand(hand, ItemStack.EMPTY);
            if (!level().isClientSide()) {
                playCameraSetSound();
                setYRot(player.getYRot());
            }
            return InteractionResult.SUCCESS;
        }
        if (!camera.isEmpty() && player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                ItemStack returned = camera.copy();
                setCamera(ItemStack.EMPTY);
                if (!player.getInventory().add(returned)) {
                    player.drop(returned, false);
                }
                playCameraRemoveSound();
            }
            return InteractionResult.SUCCESS;
        }
        return camera.isEmpty() ? InteractionResult.PASS : InteractionResult.CONSUME;
    }

    @Override
    public void tick() {
        super.tick();
        xo = getX();
        yo = getY();
        zo = getZ();
        applyGravity();
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(0.98, onGround() ? -0.5 : 0.98, 0.98));
        if (getEntityData().get(DATA_HURT) > 0) {
            getEntityData().set(DATA_HURT, getEntityData().get(DATA_HURT) - 1);
        }
        if (getEntityData().get(DATA_DAMAGE) > 0.0f) {
            getEntityData().set(DATA_DAMAGE, getEntityData().get(DATA_DAMAGE) - 1.0f);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableToBase(source)) {
            return true;
        }
        if (!getCamera().isEmpty()) {
            ItemEntity item = spawnAtLocation(level, getCamera(), getEyeHeight());
            if (item != null) {
                item.setPickUpDelay(5);
                playCameraRemoveSound();
            }
            setCamera(ItemStack.EMPTY);
            if (source.isCreativePlayer()) {
                return true;
            }
            amount = 1.0f;
        }
        getEntityData().set(DATA_HURT_DIR, -getEntityData().get(DATA_HURT_DIR));
        getEntityData().set(DATA_HURT, 10);
        getEntityData().set(DATA_DAMAGE, getEntityData().get(DATA_DAMAGE) + amount * 10.0f);
        gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        playHitSound();
        if (source.isCreativePlayer() || getEntityData().get(DATA_DAMAGE) > 10.0f) {
            destroy(level);
        }
        return true;
    }

    private void destroy(ServerLevel level) {
        kill(level);
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ItemStack drop = new ItemStack(Photography.CAMERA_STAND_ITEM);
            drop.set(DataComponents.CUSTOM_NAME, getCustomName());
            spawnAtLocation(level, drop, 0.5f);
        }
        playBreakSound();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Photography.CAMERA_STAND_ITEM);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.BLOCKS;
    }

    public void playPlaceSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_PLACE, getSoundSource(), 0.8f, 1.0f);
    }

    public void playHitSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_HIT, getSoundSource(), 0.8f, 1.0f);
    }

    public void playBreakSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_BREAK, getSoundSource(), 1.0f, 1.0f);
    }

    public void playCameraSetSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_PLACE, getSoundSource(), 0.8f, 1.0f);
    }

    public void playCameraRemoveSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, getSoundSource(), 0.8f, 1.0f);
    }
}
